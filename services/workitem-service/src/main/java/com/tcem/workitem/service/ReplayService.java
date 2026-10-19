package com.tcem.workitem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tcem.common.constants.EventType;
import com.tcem.common.constants.ProcessingStatus;
import com.tcem.workitem.config.WorkItemProperties;
import com.tcem.workitem.model.document.DlqEntryDocument;
import com.tcem.workitem.producer.ReplayProducer;
import com.tcem.workitem.repository.DlqEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static com.tcem.workitem.model.document.DlqEntryDocument.STATUS_PERMANENTLY_FAILED;
import static com.tcem.workitem.model.document.DlqEntryDocument.STATUS_REPLAYING;
import static com.tcem.workitem.model.document.DlqEntryDocument.STATUS_REPLAY_SUCCEEDED;

/**
 * Controlled replay of dead-lettered messages (FR12).
 * <p>
 * Protocol: the original payload is republished to the topic it failed on with its message header
 * rewritten to {@code eventType=REPLAY} and an incremented {@code replayCount}. Because the idempotency
 * key includes the event type, a REPLAY message is never suppressed as a duplicate of the original.
 * After {@code workitem.max-replay-attempts} replays the entry is marked PERMANENTLY_FAILED and a
 * entry's work item is escalated to HIGH instead of replaying again. Outcomes of a published replay are
 * tracked by {@link ReplayOutcomeService}.
 * </p>
 */
@Service
public class ReplayService {

    private static final Logger log = LoggerFactory.getLogger(ReplayService.class);
    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final ObjectMapper objectMapper;
    private final DlqEntryRepository dlqRepository;
    private final ReplayProducer replayProducer;
    private final WorkItemService workItemService;
    private final AuditService auditService;
    private final WorkItemProperties properties;

    public ReplayService(ObjectMapper objectMapper,
                         DlqEntryRepository dlqRepository,
                         ReplayProducer replayProducer,
                         WorkItemService workItemService,
                         AuditService auditService,
                         WorkItemProperties properties) {
        this.objectMapper = objectMapper;
        this.dlqRepository = dlqRepository;
        this.replayProducer = replayProducer;
        this.workItemService = workItemService;
        this.auditService = auditService;
        this.properties = properties;
    }

    public DlqEntryDocument replay(String requestedId) {
        DlqEntryDocument entry = dlqRepository.findEntry(requestedId)
                .orElseThrow(() -> new ReplayException(ReplayException.Reason.NOT_FOUND,
                        "DLQ entry '" + requestedId + "' not found"));
        String dlqId = entry.getId();

        if (STATUS_REPLAY_SUCCEEDED.equals(entry.getDlqStatus())) {
            throw new ReplayException(ReplayException.Reason.NOT_REPLAYABLE,
                    "DLQ entry '" + dlqId + "' was already replayed successfully");
        }
        if (STATUS_PERMANENTLY_FAILED.equals(entry.getDlqStatus()) || !entry.isReplayable()) {
            throw new ReplayException(ReplayException.Reason.NOT_REPLAYABLE,
                    "DLQ entry '" + dlqId + "' is not replayable");
        }
        if (STATUS_REPLAYING.equals(entry.getDlqStatus())) {
            throw new ReplayException(ReplayException.Reason.ALREADY_REPLAYING,
                    "DLQ entry '" + dlqId + "' is already being replayed");
        }
        if (entry.getReplayCount() >= properties.getMaxReplayAttempts()) {
            failPermanently(entry);
            throw new ReplayException(ReplayException.Reason.MAX_ATTEMPTS_REACHED,
                    "DLQ entry '" + dlqId + "' reached " + properties.getMaxReplayAttempts()
                            + " replay attempts and is now PERMANENTLY_FAILED");
        }

        int nextCount = entry.getReplayCount() + 1;
        String topic = entry.getFailedOnTopic();
        String key = entry.getTradeId() != null ? entry.getTradeId() : entry.getMessageId();

        // Persist REPLAYING before publishing: the replayed message can fail downstream and come back on
        // deadletter.trade within milliseconds, and it must find this entry REPLAYING to be merged into it.
        String previousStatus = entry.getDlqStatus();
        Instant previousReplayAt = entry.getLastReplayAt();
        entry.setReplayCount(nextCount);
        entry.setDlqStatus(STATUS_REPLAYING);
        entry.setLastReplayAt(Instant.now());
        dlqRepository.save(entry);
        try {
            replayProducer.replay(topic, key, buildReplayPayload(entry.getOriginalPayload(), nextCount), nextCount)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            entry.setReplayCount(nextCount - 1);
            entry.setDlqStatus(previousStatus);
            entry.setLastReplayAt(previousReplayAt);
            dlqRepository.save(entry);
            throw new ReplayException(ReplayException.Reason.PUBLISH_FAILED,
                    "Failed to republish DLQ entry '" + dlqId + "' to '" + topic + "': " + ex.getMessage(), ex);
        }

        auditService.record(auditTradeId(entry), entry.getCorrelationId(), EventType.REPLAY.name(), "DLQ_REPLAY",
                ProcessingStatus.REPLAYED, "Replay #" + nextCount + " published to " + topic);
        log.info("Replayed DLQ entry '{}' to '{}' (attempt {})", dlqId, topic, nextCount);
        return entry;
    }

    private void failPermanently(DlqEntryDocument entry) {
        entry.setDlqStatus(STATUS_PERMANENTLY_FAILED);
        dlqRepository.save(entry);
        String reason = "replay limit of " + properties.getMaxReplayAttempts() + " reached for " + entry.getId();
        if (entry.getLinkedWorkItemId() != null) {
            workItemService.escalate(entry.getLinkedWorkItemId(), reason);
        }
        auditService.record(auditTradeId(entry), entry.getCorrelationId(), EventType.REPLAY.name(),
                "DLQ_PERMANENT_FAILURE", ProcessingStatus.DEAD_LETTER,
                "Replay limit reached; escalated work item " + entry.getLinkedWorkItemId());
    }

    private static String auditTradeId(DlqEntryDocument entry) {
        return entry.getTradeId() != null ? entry.getTradeId() : entry.getMessageId();
    }

    /**
     * Rewrites {@code messageHeader.eventType} to REPLAY and bumps {@code messageHeader.replayCount}.
     * Payloads that are not JSON objects with a messageHeader are republished verbatim.
     */
    String buildReplayPayload(String originalPayload, int replayCount) {
        if (originalPayload == null) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(originalPayload);
            JsonNode header = root != null ? root.get("messageHeader") : null;
            if (header instanceof ObjectNode headerNode) {
                headerNode.put("eventType", EventType.REPLAY.name());
                headerNode.put("replayCount", replayCount);
                return objectMapper.writeValueAsString(root);
            }
        } catch (Exception ex) {
            log.debug("Original payload is not replay-rewritable JSON, republishing verbatim: {}", ex.getMessage());
        }
        return originalPayload;
    }
}
