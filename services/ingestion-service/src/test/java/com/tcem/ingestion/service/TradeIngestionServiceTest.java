package com.tcem.ingestion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tcem.common.constants.TopicNames;
import com.tcem.ingestion.config.CouchDbProperties;
import com.tcem.ingestion.couchdb.CouchDbOperations;
import com.tcem.ingestion.couchdb.CouchDbResponse;
import com.tcem.ingestion.generator.SyntheticTradeGenerator;
import com.tcem.ingestion.generator.TradeScenarioType;
import com.tcem.ingestion.model.IngestionResult;
import com.tcem.ingestion.model.document.IdempotencyDocument;
import com.tcem.ingestion.model.document.TradeDocument;
import com.tcem.ingestion.producer.DeadLetterProducer;
import com.tcem.ingestion.producer.ExceptionCreatedProducer;
import com.tcem.ingestion.producer.TradeAcceptedProducer;
import com.tcem.ingestion.repository.AuditCouchDbRepository;
import com.tcem.ingestion.repository.IdempotencyCouchDbRepository;
import com.tcem.ingestion.repository.TradeCouchDbRepository;
import com.tcem.ingestion.validator.TradeValidator;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeIngestionServiceTest {

    @Mock
    private CouchDbOperations couchDb;

    @Mock
    private KafkaOperations<String, String> kafkaOperations;

    private ObjectMapper objectMapper;
    private TradeValidator tradeValidator;
    private SyntheticTradeGenerator generator;
    private DefaultTradeIngestionService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        tradeValidator = new TradeValidator(objectMapper);
        generator = new SyntheticTradeGenerator(objectMapper);

        CouchDbProperties props = new CouchDbProperties();
        props.setTradesDatabase("trades");
        props.setAuditEventsDatabase("audit-events");

        TradeCouchDbRepository tradeRepository = new TradeCouchDbRepository(couchDb, props);
        AuditCouchDbRepository auditRepository = new AuditCouchDbRepository(couchDb, props);
        IdempotencyCouchDbRepository idempotencyRepository = new IdempotencyCouchDbRepository(couchDb, props);

        TradeAcceptedProducer tradeAcceptedProducer = new TradeAcceptedProducer(kafkaOperations, objectMapper);
        ExceptionCreatedProducer exceptionCreatedProducer = new ExceptionCreatedProducer(kafkaOperations, objectMapper);
        DeadLetterProducer deadLetterProducer = new DeadLetterProducer(kafkaOperations, objectMapper);

        service = new DefaultTradeIngestionService(
                objectMapper,
                tradeValidator,
                tradeRepository,
                auditRepository,
                idempotencyRepository,
                tradeAcceptedProducer,
                exceptionCreatedProducer,
                deadLetterProducer
        );
    }

    private SendResult<String, String> mockSendResult(String topic) {
        RecordMetadata metadata = new RecordMetadata(new TopicPartition(topic, 0), 0, 0, 0, 0, 0);
        return new SendResult<>(null, metadata);
    }

    @Test
    @DisplayName("Valid STP trade payload is validated, persisted in CouchDB, recorded in idempotency, and dispatched to trade.accepted")
    void testProcessValidTradeAccepted() {
        String payload = generator.generateTradeJson(TradeScenarioType.VALID_STANDARD_STP, 1);

        when(couchDb.getDocument(eq("trades"), startsWith("idempotency:"), eq(IdempotencyDocument.class)))
                .thenReturn(Optional.empty());
        when(couchDb.putDocument(eq("trades"), any(), any()))
                .thenReturn(CouchDbResponse.builder().ok(true).rev("1-rev").build());
        when(couchDb.putDocument(eq("audit-events"), any(), any()))
                .thenReturn(CouchDbResponse.builder().ok(true).rev("1-rev").build());
        when(kafkaOperations.send(eq(TopicNames.TRADE_ACCEPTED), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult(TopicNames.TRADE_ACCEPTED)));

        IngestionResult result = service.processRawTrade("TRD-STP-1", payload);

        assertThat(result.isAccepted()).isTrue();
        assertThat(result.getStatus()).isEqualTo(IngestionResult.Status.ACCEPTED);
        assertThat(result.getTradeId()).isNotNull();

        // CouchDB trade & idempotency saves verified
        verify(couchDb).putDocument(eq("trades"), eq(result.getTradeId()), any(TradeDocument.class));
        verify(couchDb).putDocument(eq("trades"), startsWith("idempotency:"), any(IdempotencyDocument.class));
        verify(couchDb).putDocument(eq("audit-events"), any(), any());

        // Kafka trade.accepted send verified
        verify(kafkaOperations).send(eq(TopicNames.TRADE_ACCEPTED), eq(result.getTradeId()), any());
        verify(kafkaOperations, never()).send(eq(TopicNames.EXCEPTION_CREATED), any(), any());
        verify(kafkaOperations, never()).send(eq(TopicNames.DEADLETTER_TRADE), any(), any());
    }

    @Test
    @DisplayName("Duplicate trade message is detected by IdempotencyKey and suppressed from trade.accepted")
    void testProcessDuplicateMessageSuppressed() {
        String payload = generator.generateTradeJson(TradeScenarioType.VALID_STANDARD_STP, 2);

        when(couchDb.getDocument(eq("trades"), startsWith("idempotency:"), eq(IdempotencyDocument.class)))
                .thenReturn(Optional.of(new IdempotencyDocument()));
        when(kafkaOperations.send(eq(TopicNames.EXCEPTION_CREATED), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult(TopicNames.EXCEPTION_CREATED)));

        IngestionResult result = service.processRawTrade("TRD-DUP-1", payload);

        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.getStatus()).isEqualTo(IngestionResult.Status.DUPLICATE);

        // Never saved to trades database as accepted document
        verify(couchDb, never()).putDocument(eq("trades"), eq(result.getTradeId()), any(TradeDocument.class));
        verify(kafkaOperations, never()).send(eq(TopicNames.TRADE_ACCEPTED), any(), any());

        // Exception emitted for duplicate
        verify(kafkaOperations).send(eq(TopicNames.EXCEPTION_CREATED), eq(result.getTradeId()),
                argThat((String body) -> body.contains("DUPLICATE_MESSAGE")));
    }

    @Test
    @DisplayName("Schema invalid trade is saved as SCHEMA_INVALID, records idempotency, and dispatches ExceptionEvent")
    void testProcessSchemaInvalidTrade() throws Exception {
        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(generator.generateTradeJson(TradeScenarioType.VALID_STANDARD_STP, 3));
        ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("trade").path("fxOptionEconomics")).put("strike", -1);
        String payload = objectMapper.writeValueAsString(root);

        when(couchDb.getDocument(eq("trades"), startsWith("idempotency:"), eq(IdempotencyDocument.class)))
                .thenReturn(Optional.empty());
        when(couchDb.putDocument(eq("trades"), any(), any()))
                .thenReturn(CouchDbResponse.builder().ok(true).rev("1-rev").build());
        when(kafkaOperations.send(eq(TopicNames.EXCEPTION_CREATED), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult(TopicNames.EXCEPTION_CREATED)));

        IngestionResult result = service.processRawTrade("TRD-ERR-STRIKE", payload);

        assertThat(result.isRejected()).isTrue();
        assertThat(result.getStatus()).isEqualTo(IngestionResult.Status.REJECTED);
        assertThat(result.getErrors()).isNotEmpty();

        // CouchDB trade saved with SCHEMA_INVALID
        verify(couchDb).putDocument(eq("trades"), eq(result.getTradeId()), any(TradeDocument.class));
        verify(couchDb).putDocument(eq("trades"), startsWith("idempotency:"), any(IdempotencyDocument.class));

        // Kafka routing: NOT to trade.accepted, YES to exception.created
        verify(kafkaOperations, never()).send(eq(TopicNames.TRADE_ACCEPTED), any(), any());
        verify(kafkaOperations).send(eq(TopicNames.EXCEPTION_CREATED), eq(result.getTradeId()), any());
        verify(kafkaOperations, never()).send(eq(TopicNames.DEADLETTER_TRADE), any(), any());
    }

    @Test
    @DisplayName("Malformed JSON syntax error is routed to deadletter.trade with diagnostic metadata")
    void testProcessMalformedJsonRoutesToDeadLetter() {
        String brokenJson = "{\"trade\": {unquoted_broken_content}}";
        when(kafkaOperations.send(eq(TopicNames.DEADLETTER_TRADE), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult(TopicNames.DEADLETTER_TRADE)));

        IngestionResult result = service.processRawTrade("BROKEN-KEY", brokenJson);

        assertThat(result.isDeadLetter()).isTrue();
        assertThat(result.getStatus()).isEqualTo(IngestionResult.Status.DEAD_LETTER);

        ArgumentCaptor<String> dlqBodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaOperations).send(eq(TopicNames.DEADLETTER_TRADE), eq("BROKEN-KEY"), dlqBodyCaptor.capture());
        assertThat(dlqBodyCaptor.getValue())
                .contains("trade.raw")
                .contains("ingestion-service")
                .contains("Malformed JSON");

        verify(kafkaOperations, never()).send(eq(TopicNames.TRADE_ACCEPTED), any(), any());
        verify(kafkaOperations, never()).send(eq(TopicNames.EXCEPTION_CREATED), any(), any());
    }

    @Test
    @DisplayName("Empty or null raw payload is routed to deadletter.trade")
    void testProcessEmptyPayloadRoutesToDeadLetter() {
        when(kafkaOperations.send(eq(TopicNames.DEADLETTER_TRADE), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult(TopicNames.DEADLETTER_TRADE)));

        IngestionResult result = service.processRawTrade("NULL-KEY", "   ");

        assertThat(result.isDeadLetter()).isTrue();
        verify(kafkaOperations).send(eq(TopicNames.DEADLETTER_TRADE), eq("NULL-KEY"), any());
        verify(kafkaOperations, never()).send(eq(TopicNames.TRADE_ACCEPTED), any(), any());
    }

    @Test
    @DisplayName("Resilient behavior when CouchDB encounters temporary downtime")
    void testResilientWhenCouchDbFails() {
        String payload = generator.generateTradeJson(TradeScenarioType.VALID_STANDARD_STP, 4);

        when(couchDb.getDocument(any(), any(), any())).thenThrow(new RuntimeException("CouchDB network timeout"));
        when(couchDb.putDocument(any(), any(), any())).thenThrow(new RuntimeException("CouchDB disk full"));
        when(kafkaOperations.send(eq(TopicNames.TRADE_ACCEPTED), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult(TopicNames.TRADE_ACCEPTED)));

        // Pipeline must not throw uncaught exception; still dispatches validated trade
        IngestionResult result = service.processRawTrade("TRD-RESILIENT", payload);

        assertThat(result.isAccepted()).isTrue();
        verify(kafkaOperations).send(eq(TopicNames.TRADE_ACCEPTED), eq(result.getTradeId()), any());
    }

    @Test
    @DisplayName("A second replay of the same message is reprocessed, not suppressed as a duplicate")
    void testReplayBypassesDuplicateSuppression() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode root = (com.fasterxml.jackson.databind.node.ObjectNode)
                objectMapper.readTree(generator.generateTradeJson(TradeScenarioType.VALID_STANDARD_STP, 3));
        com.fasterxml.jackson.databind.node.ObjectNode header =
                (com.fasterxml.jackson.databind.node.ObjectNode) root.get("messageHeader");
        header.put("eventType", "REPLAY");
        header.put("replayCount", 2);

        org.mockito.Mockito.lenient().when(couchDb.getDocument(eq("trades"), startsWith("idempotency:"), eq(IdempotencyDocument.class)))
                .thenReturn(Optional.of(new IdempotencyDocument()));
        when(kafkaOperations.send(eq(TopicNames.TRADE_ACCEPTED), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult(TopicNames.TRADE_ACCEPTED)));

        service.processRawTrade("TRD-REPLAY-1", objectMapper.writeValueAsString(root));

        verify(kafkaOperations).send(eq(TopicNames.TRADE_ACCEPTED), any(), any());
        verify(kafkaOperations, never()).send(eq(TopicNames.EXCEPTION_CREATED), any(), any());
    }

    @Test
    @DisplayName("A trade with an absent economic field is accepted, so STP can raise the exception (FR5)")
    void testMissingEconomicsAreAcceptedForStp() {
        String payload = generator.generateTradeJson(TradeScenarioType.MISSING_ECONOMIC_STRIKE, 3);

        when(couchDb.getDocument(eq("trades"), startsWith("idempotency:"), eq(IdempotencyDocument.class)))
                .thenReturn(Optional.empty());
        when(kafkaOperations.send(eq(TopicNames.TRADE_ACCEPTED), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult(TopicNames.TRADE_ACCEPTED)));

        service.processRawTrade("TRD-MISSING-STRIKE", payload);

        verify(kafkaOperations).send(eq(TopicNames.TRADE_ACCEPTED), any(), any());
        verify(kafkaOperations, never()).send(eq(TopicNames.EXCEPTION_CREATED), any(), any());
    }
}
