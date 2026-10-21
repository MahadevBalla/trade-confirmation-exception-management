package com.tcem.validation.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.ConfirmationStatus;
import com.tcem.common.constants.EventType;
import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import com.tcem.common.constants.ProductType;
import com.tcem.common.constants.TopicNames;
import com.tcem.common.event.ConfirmationEvent;
import com.tcem.common.event.ExceptionEvent;
import com.tcem.common.model.message.MessageHeader;
import com.tcem.common.model.message.SchemaVersions;
import com.tcem.common.model.message.TradeEvent;
import com.tcem.common.model.reference.ReferenceDataEnrichment;
import com.tcem.common.model.trade.FxOptionEconomics;
import com.tcem.common.model.trade.Party;
import com.tcem.common.model.trade.Trade;
import com.tcem.common.registry.ConfirmationFingerprint;
import com.tcem.validation.couchdb.CouchDbClient;
import com.tcem.validation.model.StpValidatedEvent;
import com.tcem.validation.repository.ConfirmationCouchDbRepository;
import com.tcem.validation.repository.ConfirmationDocument;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end integration test for the STP validation pipeline (FR5, FR6, FR7).
 *
 * <p>Uses a real {@code @KafkaListener} and real producers against {@link EmbeddedKafka}.
 * CouchDB is replaced at the repository/client boundary so the rules engine, consumer,
 * confirmation service, and Kafka producers remain under test.
 */
@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {
                TopicNames.TRADE_ENRICHED,
                TopicNames.STP_VALIDATED,
                TopicNames.CONFIRMATION_GENERATED,
                TopicNames.EXCEPTION_CREATED,
                TopicNames.DEADLETTER_TRADE
        }
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.group-id=it-validation-service-group-${random.uuid}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.listener.auto-startup=true",
        "couchdb.auto-create-databases=false",
        "server.port=0"
})
@DirtiesContext
class TradeEnrichedPipelineIntegrationTest {

    private static final Duration PIPELINE_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration ABSENCE_WINDOW = Duration.ofMillis(750);

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ConfirmationCouchDbRepository confirmationRepository;

    @MockBean
    private CouchDbClient couchDbClient;

    private IntegrationTestConsumer stpValidatedConsumer;
    private IntegrationTestConsumer confirmationGeneratedConsumer;
    private IntegrationTestConsumer exceptionCreatedConsumer;
    private IntegrationTestConsumer deadLetterConsumer;

    @BeforeEach
    void setUp() {
        reset(confirmationRepository, couchDbClient);
        when(couchDbClient.isHealthy()).thenReturn(false);
        when(confirmationRepository.findLatestForTrade(any())).thenReturn(Optional.empty());
        when(confirmationRepository.save(any(ConfirmationDocument.class))).thenAnswer(invocation -> {
            ConfirmationDocument doc = invocation.getArgument(0);
            doc.setRev("1-it");
            return doc;
        });

        stpValidatedConsumer = IntegrationTestConsumer.create(embeddedKafka, TopicNames.STP_VALIDATED);
        confirmationGeneratedConsumer = IntegrationTestConsumer.create(embeddedKafka, TopicNames.CONFIRMATION_GENERATED);
        exceptionCreatedConsumer = IntegrationTestConsumer.create(embeddedKafka, TopicNames.EXCEPTION_CREATED);
        deadLetterConsumer = IntegrationTestConsumer.create(embeddedKafka, TopicNames.DEADLETTER_TRADE);
    }

    @AfterEach
    void tearDown() {
        closeQuietly(stpValidatedConsumer);
        closeQuietly(confirmationGeneratedConsumer);
        closeQuietly(exceptionCreatedConsumer);
        closeQuietly(deadLetterConsumer);
    }

    @Test
    @DisplayName("IT: valid NEW_TRADE publishes STP_PASSED, persists confirmation, and emits confirmation.generated")
    void validNewTrade_publishesStpPassedAndConfirmation() throws Exception {
        String tradeId = newTradeId("PASS");
        TradeEvent event = buildFullyEnrichedTradeEvent(tradeId, 1, EventType.NEW_TRADE);

        publishEnriched(tradeId, event);

        StpValidatedEvent stpEvent = readStpEvent(tradeId);
        assertThat(stpEvent.isStpPassed()).isTrue();
        assertThat(stpEvent.getTradeId()).isEqualTo(tradeId);
        assertThat(stpEvent.getTradeVersion()).isEqualTo(1);
        assertThat(stpEvent.getFailures()).isNullOrEmpty();
        assertThat(stpEvent.getMessageHeader().getCorrelationId())
                .isEqualTo(event.getMessageHeader().getCorrelationId());

        ArgumentCaptor<ConfirmationDocument> saved = ArgumentCaptor.forClass(ConfirmationDocument.class);
        verify(confirmationRepository, timeout(PIPELINE_TIMEOUT.toMillis())).save(saved.capture());
        ConfirmationDocument document = saved.getValue();
        assertThat(document.getTradeId()).isEqualTo(tradeId);
        assertThat(document.getTradeVersion()).isEqualTo(1);
        assertThat(document.getStatus()).isEqualTo(ConfirmationStatus.ALLEGED);
        assertThat(document.getTemplateId()).isEqualTo("TMPL-FX-OPT-001");
        assertThat(document.getTemplateVersion()).isEqualTo("1.2.0");
        assertThat(document.getConfirmationFingerprint()).isEqualTo(
                ConfirmationFingerprint.compute(event.getTrade(), objectMapper));
        assertThat(document.getConfirmationBody()).contains(tradeId).contains("TRADE CONFIRMATION");

        ConfirmationEvent confirmationEvent = readConfirmationEvent(tradeId);
        assertThat(confirmationEvent.getConfirmation().getTradeId()).isEqualTo(tradeId);
        assertThat(confirmationEvent.getConfirmation().getTradeVersion()).isEqualTo(1);
        assertThat(confirmationEvent.getConfirmation().getStatus()).isEqualTo(ConfirmationStatus.ALLEGED);
        assertThat(confirmationEvent.getConfirmation().getConfirmationFingerprint())
                .isEqualTo(document.getConfirmationFingerprint());
        assertThat(confirmationEvent.getMessageHeader().getCorrelationId())
                .isEqualTo(event.getMessageHeader().getCorrelationId());

        assertNoRecordForKey(exceptionCreatedConsumer, TopicNames.EXCEPTION_CREATED, tradeId, ABSENCE_WINDOW);
        assertNoRecordForKey(deadLetterConsumer, TopicNames.DEADLETTER_TRADE, tradeId, ABSENCE_WINDOW);
    }

    @Test
    @DisplayName("IT: NEW_TRADE missing strike publishes STP_FAILED and exception.created, with no confirmation")
    void invalidNewTrade_publishesStpFailedAndException_noConfirmation() throws Exception {
        String tradeId = newTradeId("FAIL");
        TradeEvent event = buildFullyEnrichedTradeEvent(tradeId, 1, EventType.NEW_TRADE);
        event.getTrade().getFxOptionEconomics().setStrike(null);

        publishEnriched(tradeId, event);

        StpValidatedEvent stpEvent = readStpEvent(tradeId);
        assertThat(stpEvent.isStpPassed()).isFalse();
        assertThat(stpEvent.getFailures()).isNotEmpty();
        assertThat(stpEvent.getFailures())
                .anyMatch(failure -> ExceptionCategory.MISSING_ECONOMIC_DATA.name().equals(failure.getCategory())
                        && ExceptionSubTheme.MISSING_STRIKE.name().equals(failure.getSubTheme()));

        ExceptionEvent exceptionEvent = objectMapper.readValue(
                awaitRecordByKey(exceptionCreatedConsumer.consumer(), TopicNames.EXCEPTION_CREATED, tradeId,
                        PIPELINE_TIMEOUT).value(),
                ExceptionEvent.class);
        assertThat(exceptionEvent.getException().getTradeId()).isEqualTo(tradeId);
        assertThat(exceptionEvent.getException().getCategory()).isEqualTo(ExceptionCategory.MISSING_ECONOMIC_DATA);
        assertThat(exceptionEvent.getException().getSubTheme()).isEqualTo(ExceptionSubTheme.MISSING_STRIKE);
        assertThat(exceptionEvent.getException().getOwnerQueue()).isEqualTo("DATA_REMEDIATION");
        assertThat(exceptionEvent.getException().getAffectedFields()).contains("fxOptionEconomics.strike");

        verify(confirmationRepository, never()).save(any());
        assertNoRecordForKey(confirmationGeneratedConsumer, TopicNames.CONFIRMATION_GENERATED, tradeId, ABSENCE_WINDOW);
        assertNoRecordForKey(deadLetterConsumer, TopicNames.DEADLETTER_TRADE, tradeId, ABSENCE_WINDOW);
    }

    @Test
    @DisplayName("IT: consumer-level idempotency skips reprocessing of the same tradeId+version")
    void alreadyProcessedVersion_skipsEntirePipeline() throws Exception {
        String tradeId = newTradeId("IDEM");
        TradeEvent event = buildFullyEnrichedTradeEvent(tradeId, 1, EventType.NEW_TRADE);

        ConfirmationDocument existing = new ConfirmationDocument();
        existing.setTradeId(tradeId);
        existing.setTradeVersion(1);
        existing.setConfirmationFingerprint("existing-fingerprint");
        when(confirmationRepository.findLatestForTrade(tradeId)).thenReturn(Optional.of(existing));

        publishEnriched(tradeId, event);

        verify(confirmationRepository, timeout(PIPELINE_TIMEOUT.toMillis())).findLatestForTrade(tradeId);
        verify(confirmationRepository, never()).save(any());
        assertNoRecordForKey(stpValidatedConsumer, TopicNames.STP_VALIDATED, tradeId, ABSENCE_WINDOW);
        assertNoRecordForKey(confirmationGeneratedConsumer, TopicNames.CONFIRMATION_GENERATED, tradeId, ABSENCE_WINDOW);
        assertNoRecordForKey(exceptionCreatedConsumer, TopicNames.EXCEPTION_CREATED, tradeId, ABSENCE_WINDOW);
        assertNoRecordForKey(deadLetterConsumer, TopicNames.DEADLETTER_TRADE, tradeId, ABSENCE_WINDOW);
    }

    @Test
    @DisplayName("IT: AMEND_TRADE with unchanged fingerprint publishes STP_PASSED but suppresses confirmation")
    void amendTrade_unchangedFingerprint_suppressesConfirmation() throws Exception {
        String tradeId = newTradeId("AMEND");
        TradeEvent event = buildFullyEnrichedTradeEvent(tradeId, 2, EventType.AMEND_TRADE);
        String fingerprint = ConfirmationFingerprint.compute(event.getTrade(), objectMapper);

        ConfirmationDocument previous = new ConfirmationDocument();
        previous.setTradeId(tradeId);
        previous.setTradeVersion(1);
        previous.setConfirmationFingerprint(fingerprint);
        when(confirmationRepository.findLatestForTrade(tradeId)).thenReturn(Optional.of(previous));

        publishEnriched(tradeId, event);

        StpValidatedEvent stpEvent = readStpEvent(tradeId);
        assertThat(stpEvent.isStpPassed()).isTrue();
        assertThat(stpEvent.getTradeVersion()).isEqualTo(2);

        verify(confirmationRepository, never()).save(any());
        assertNoRecordForKey(confirmationGeneratedConsumer, TopicNames.CONFIRMATION_GENERATED, tradeId, ABSENCE_WINDOW);
        assertNoRecordForKey(exceptionCreatedConsumer, TopicNames.EXCEPTION_CREATED, tradeId, ABSENCE_WINDOW);
    }

    @Test
    @DisplayName("IT: REPLAY bypasses version idempotency, re-runs STP, and still suppresses duplicate confirmation")
    void replay_bypassesVersionIdempotency_butFingerprintStillSuppresses() throws Exception {
        String tradeId = newTradeId("REPLAY");
        TradeEvent event = buildFullyEnrichedTradeEvent(tradeId, 1, EventType.REPLAY);
        String fingerprint = ConfirmationFingerprint.compute(event.getTrade(), objectMapper);

        ConfirmationDocument existing = new ConfirmationDocument();
        existing.setTradeId(tradeId);
        existing.setTradeVersion(1);
        existing.setConfirmationFingerprint(fingerprint);
        when(confirmationRepository.findLatestForTrade(tradeId)).thenReturn(Optional.of(existing));

        publishEnriched(tradeId, event);

        StpValidatedEvent stpEvent = readStpEvent(tradeId);
        assertThat(stpEvent.isStpPassed()).isTrue();
        assertThat(stpEvent.getTradeVersion()).isEqualTo(1);

        verify(confirmationRepository, never()).save(any());
        assertNoRecordForKey(confirmationGeneratedConsumer, TopicNames.CONFIRMATION_GENERATED, tradeId, ABSENCE_WINDOW);
        assertNoRecordForKey(exceptionCreatedConsumer, TopicNames.EXCEPTION_CREATED, tradeId, ABSENCE_WINDOW);
    }

    private TradeEvent buildFullyEnrichedTradeEvent(String tradeId, int version, EventType eventType) {
        MessageHeader header = new MessageHeader();
        header.setMessageId("MSG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        header.setCorrelationId("CORR-IT-" + tradeId);
        header.setSchemaVersion(SchemaVersions.CURRENT);
        header.setCreatedTimestamp(Instant.now());
        header.setSourceService("enrichment-service");
        header.setEventType(eventType);

        FxOptionEconomics economics = new FxOptionEconomics();
        economics.setNotionalAmount(new BigDecimal("1000000"));
        economics.setNotionalCurrency("USD");
        economics.setStrike(new BigDecimal("1.085"));
        economics.setUnderlier("EUR/USD");
        economics.setPremiumAmount(new BigDecimal("12000"));
        economics.setPremiumCurrency("USD");
        economics.setPremiumPaymentDate("2026-01-17");
        economics.setBuySell("BUY");
        economics.setOptionType("CALL");

        ReferenceDataEnrichment enrichment = new ReferenceDataEnrichment();
        enrichment.setCounterpartySTPEligible(true);
        enrichment.setProductSTPEligible(true);
        enrichment.setResolvedTemplateId("TMPL-FX-OPT-001");
        enrichment.setResolvedTemplateVersion("1.2.0");

        Party bank = new Party();
        bank.setPartyId("PTY-BANK-001");
        bank.setPartyRole("BANK");

        Party counterparty = new Party();
        counterparty.setPartyId("PTY-CP-101");
        counterparty.setPartyRole("COUNTERPARTY");
        counterparty.setLegalEntityName("Acme Bank Ltd");
        counterparty.setLei("ACME000000000000001");
        counterparty.setCountry("GB");

        Trade trade = new Trade();
        trade.setTradeId(tradeId);
        trade.setTradeVersion(version);
        trade.setProductType(ProductType.FX_OPTION);
        trade.setTradeDate("2026-01-15");
        trade.setEffectiveDate("2026-01-17");
        trade.setMaturityDate("2026-04-17");
        trade.setFxOptionEconomics(economics);
        trade.setEnrichmentMetadata(enrichment);
        trade.setParties(List.of(bank, counterparty));

        return new TradeEvent(header, trade);
    }

    private void publishEnriched(String tradeId, TradeEvent event) throws Exception {
        kafkaTemplate.send(TopicNames.TRADE_ENRICHED, tradeId, objectMapper.writeValueAsString(event))
                .get(10, TimeUnit.SECONDS);
        kafkaTemplate.flush();
    }

    private StpValidatedEvent readStpEvent(String tradeId) throws Exception {
        ConsumerRecord<String, String> record = awaitRecordByKey(
                stpValidatedConsumer.consumer(), TopicNames.STP_VALIDATED, tradeId, PIPELINE_TIMEOUT);
        StpValidatedEvent stpEvent = objectMapper.readValue(record.value(), StpValidatedEvent.class);
        assertThat(record.key()).isEqualTo(tradeId);
        return stpEvent;
    }

    private ConfirmationEvent readConfirmationEvent(String tradeId) throws Exception {
        ConsumerRecord<String, String> record = awaitRecordByKey(
                confirmationGeneratedConsumer.consumer(), TopicNames.CONFIRMATION_GENERATED, tradeId, PIPELINE_TIMEOUT);
        assertThat(record.key()).isEqualTo(tradeId);
        return objectMapper.readValue(record.value(), ConfirmationEvent.class);
    }

    private static String newTradeId(String prefix) {
        return "IT-" + prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private static ConsumerRecord<String, String> awaitRecordByKey(
            Consumer<String, String> consumer,
            String topic,
            String expectedKey,
            Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(250));
            for (ConsumerRecord<String, String> record : records) {
                if (topic.equals(record.topic()) && expectedKey.equals(record.key())) {
                    return record;
                }
            }
        }
        throw new AssertionError("Timed out waiting for key=" + expectedKey + " on topic=" + topic);
    }

    private static void assertNoRecordForKey(
            IntegrationTestConsumer testConsumer,
            String topic,
            String unexpectedKey,
            Duration window) {
        Instant deadline = Instant.now().plus(window);
        List<String> seenKeys = new ArrayList<>();
        while (Instant.now().isBefore(deadline)) {
            ConsumerRecords<String, String> records = testConsumer.consumer().poll(Duration.ofMillis(200));
            for (ConsumerRecord<String, String> record : records) {
                if (!topic.equals(record.topic())) {
                    continue;
                }
                seenKeys.add(record.key());
                assertThat(record.key())
                        .as("Did not expect a record for key [%s] on topic [%s]", unexpectedKey, topic)
                        .isNotEqualTo(unexpectedKey);
            }
        }
        assertThat(seenKeys).doesNotContain(unexpectedKey);
    }

    private static void closeQuietly(IntegrationTestConsumer consumer) {
        if (consumer != null) {
            consumer.close();
        }
    }
}
