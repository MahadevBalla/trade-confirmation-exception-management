package com.tcem.workitem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tcem.common.constants.EventType;
import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import com.tcem.common.constants.TopicNames;
import com.tcem.common.event.DeadLetterEvent;
import com.tcem.common.event.ExceptionEvent;
import com.tcem.common.model.exception.ExceptionRecord;
import com.tcem.common.model.message.MessageHeader;
import com.tcem.workitem.config.CouchDbProperties;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/** Shared builders for workitem-service tests. */
public final class DlqTestSupport {

    private DlqTestSupport() {}

    public static ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public static CouchDbProperties couchProperties() {
        CouchDbProperties props = new CouchDbProperties();
        props.setDlqEntriesDatabase("dlq-entries");
        props.setWorkItemsDatabase("work-items");
        props.setAuditEventsDatabase("audit-events");
        return props;
    }

    /** A replayable DLQ event for a well-formed trade that failed during processing. */
    public static DeadLetterEvent deadLetter(String messageId, boolean replayable, int replayCount) {
        MessageHeader header = new MessageHeader();
        header.setMessageId(messageId);
        header.setEventType(EventType.NEW_TRADE);
        header.setSourceService("ingestion-service");
        header.setCreatedTimestamp(Instant.parse("2026-10-11T10:00:00Z"));

        String payload = "{\"messageHeader\":{\"messageId\":\"MSG-1\",\"eventType\":\"NEW_TRADE\","
                + "\"schemaVersion\":\"capstone-fpml-json-1.0\"},\"trade\":{\"tradeId\":\"TRD-000001\"}}";

        return DeadLetterEvent.builder()
                .messageHeader(header)
                .originalPayload(payload)
                .failedOnTopic(TopicNames.TRADE_RAW)
                .failedInService("ingestion-service")
                .failureReason("Unexpected deserialization failure")
                .exceptionClass("java.lang.IllegalStateException")
                .failedAt(Instant.parse("2026-10-11T10:00:01Z"))
                .replayCount(replayCount)
                .replayable(replayable)
                .dlqStatus("PENDING_REPLAY")
                .build();
    }

    public static String json(Object event) throws Exception {
        return objectMapper().writeValueAsString(event);
    }

    /** A classified exception as exception-service would publish it on 'exception.classified'. */
    public static ExceptionEvent exceptionEvent(String exceptionId, String tradeId, ExceptionCategory category,
                                                ExceptionSubTheme subTheme, double confidence,
                                                String ownerQueue, String operationalImpact) {
        ExceptionRecord record = new ExceptionRecord();
        record.setExceptionId(exceptionId);
        record.setTradeId(tradeId);
        record.setCorrelationId("CORR-" + tradeId);
        record.setCategory(category);
        record.setSubTheme(subTheme);
        record.setClassificationConfidence(confidence);
        record.setReasonCode("RC-" + subTheme);
        record.setAffectedFields("trade.nonEconomicTerms.templateHint");
        record.setRecommendedAction("Fix it");
        record.setOwnerQueue(ownerQueue);
        record.setOperationalImpact(operationalImpact);
        record.setResolutionDifficulty("MEDIUM");
        record.setCapturedAt(Instant.parse("2026-10-11T10:00:00Z"));

        MessageHeader header = new MessageHeader();
        header.setMessageId("MSG-" + exceptionId);
        header.setCorrelationId("CORR-" + tradeId);
        header.setEventType(EventType.NEW_TRADE);
        header.setSourceService("exception-service");
        return new ExceptionEvent(header, record);
    }

    /** Makes every send on the mocked KafkaOperations succeed immediately. */
    @SuppressWarnings("unchecked")
    public static void stubSendsSucceed(KafkaOperations<String, String> kafka) {
        CompletableFuture<SendResult<String, String>> ok = CompletableFuture.completedFuture(new SendResult<>(null, null));
        lenient().when(kafka.send(anyString(), anyString(), any())).thenReturn(ok);
        lenient().when(kafka.send(any(org.apache.kafka.clients.producer.ProducerRecord.class))).thenReturn(ok);
    }
}
