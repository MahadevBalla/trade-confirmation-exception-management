package com.tcem.workitem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.EventType;
import com.tcem.common.constants.ProcessingStatus;
import com.tcem.common.constants.TopicNames;
import com.tcem.common.event.DeadLetterEvent;
import com.tcem.workitem.config.WorkItemProperties;
import com.tcem.workitem.model.document.DlqEntryDocument;
import com.tcem.workitem.repository.DlqEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.tcem.workitem.model.document.DlqEntryDocument.STATUS_PENDING_REPLAY;
import static com.tcem.workitem.model.document.DlqEntryDocument.STATUS_PERMANENTLY_FAILED;
import static com.tcem.workitem.model.document.DlqEntryDocument.STATUS_REPLAY_SUCCEEDED;

/**
 * Tracks what happened to a replayed message (FR12).
 * <ul>
 *   <li><b>Success</b>: a replay-marked message for the trade reaches a topic downstream of the one it
 *       failed on. The entry becomes REPLAY_SUCCEEDED and its work item is closed.</li>
 *   <li><b>Failure</b>: the trade is dead-lettered again while its entry is REPLAYING. The failure is
 *       merged into that entry (back to PENDING_REPLAY, replay count kept) so the max-attempts policy
 *       applies across attempts; at the limit it becomes PERMANENTLY_FAILED and its work item is escalated.</li>
 * </ul>
 */
@Service
public class ReplayOutcomeService {

    private static final Logger log = LoggerFactory.getLogger(ReplayOutcomeService.class);

    /** For each topic a message can fail on, the topics that prove it was processed. */
    static final Map<String, Set<String>> SUCCESS_TOPICS = Map.of(
            TopicNames.TRADE_RAW, Set.of(TopicNames.TRADE_ACCEPTED, TopicNames.EXCEPTION_CREATED),
            TopicNames.TRADE_ACCEPTED, Set.of(TopicNames.TRADE_ENRICHED, TopicNames.EXCEPTION_CREATED),
            TopicNames.TRADE_ENRICHED, Set.of(TopicNames.STP_VALIDATED, TopicNames.CONFIRMATION_GENERATED,
                    TopicNames.EXCEPTION_CREATED),
            TopicNames.STP_VALIDATED, Set.of(TopicNames.CONFIRMATION_GENERATED, TopicNames.EXCEPTION_CREATED),
            TopicNames.EXCEPTION_CREATED, Set.of(TopicNames.EXCEPTION_CLASSIFIED));

    private final ObjectMapper objectMapper;
    private final DlqEntryRepository dlqRepository;
    private final WorkItemService workItemService;
    private final AuditService auditService;
    private final WorkItemProperties properties;

    public ReplayOutcomeService(ObjectMapper objectMapper,
                                DlqEntryRepository dlqRepository,
                                WorkItemService workItemService,
                                AuditService auditService,
                                WorkItemProperties properties) {
        this.objectMapper = objectMapper;
        this.dlqRepository = dlqRepository;
        this.workItemService = workItemService;
        this.auditService = auditService;
        this.properties = properties;
    }

    /**
     * Checks a pipeline message against entries awaiting a replay outcome.
     *
     * @param replayHeader true when the record carried a {@code tcem.replayCount} Kafka header
     * @return entries marked REPLAY_SUCCEEDED
     */
    public List<DlqEntryDocument> onPipelineMessage(String topic, String tradeId, String value, boolean replayHeader) {
        List<DlqEntryDocument> waiting = dlqRepository.findReplayingByTrade(tradeId).stream()
                .filter(entry -> SUCCESS_TOPICS.getOrDefault(entry.getFailedOnTopic(), Set.of()).contains(topic))
                .toList();
        if (waiting.isEmpty() || !(replayHeader || hasReplayMarker(value))) {
            return List.of();
        }
        for (DlqEntryDocument entry : waiting) {
            entry.setDlqStatus(STATUS_REPLAY_SUCCEEDED);
            dlqRepository.save(entry);
            auditService.record(tradeId, entry.getCorrelationId(), EventType.REPLAY.name(), "DLQ_REPLAY_SUCCEEDED",
                    ProcessingStatus.REPLAYED, "Replay #" + entry.getReplayCount() + " of " + entry.getId()
                            + " succeeded: message reached " + topic);
            if (entry.getLinkedWorkItemId() != null) {
                workItemService.close(entry.getLinkedWorkItemId(),
                        "Replay #" + entry.getReplayCount() + " succeeded (reached " + topic + ")");
            }
            log.info("DLQ entry '{}' replay succeeded on '{}'", entry.getId(), topic);
        }
        return waiting;
    }

    /**
     * Merges a new dead letter into the trade's REPLAYING entry, if there is one.
     *
     * @param newDlqId id the new dead letter would have had; stored as an alias so redelivery is a no-op
     * @return the updated entry, or empty when the dead letter is unrelated to a pending replay
     */
    public Optional<DlqEntryDocument> mergeFailedReplay(String tradeId, DeadLetterEvent event, String newDlqId) {
        Optional<DlqEntryDocument> replaying = dlqRepository.findReplayingByTrade(tradeId).stream()
                .max(Comparator.comparing(DlqEntryDocument::getLastReplayAt, Comparator.nullsFirst(Comparator.naturalOrder())));
        if (replaying.isEmpty()) {
            return Optional.empty();
        }

        DlqEntryDocument entry = replaying.get();
        entry.getFailureHistory().add("Replay #" + entry.getReplayCount() + " failed in " + event.getFailedInService()
                + " on " + event.getFailedOnTopic() + ": " + event.getFailureReason());
        entry.setFailureReason(event.getFailureReason());
        entry.setFailedInService(event.getFailedInService());
        entry.setExceptionClass(event.getExceptionClass());
        entry.setStackTraceExcerpt(event.getStackTraceExcerpt());
        entry.setFailedAt(event.getFailedAt() != null ? event.getFailedAt() : Instant.now());
        entry.setReplayable(entry.isReplayable() && event.isReplayable());

        boolean exhausted = !entry.isReplayable() || entry.getReplayCount() >= properties.getMaxReplayAttempts();
        entry.setDlqStatus(exhausted ? STATUS_PERMANENTLY_FAILED : STATUS_PENDING_REPLAY);
        dlqRepository.save(entry);
        dlqRepository.save(DlqEntryDocument.alias(newDlqId, entry.getId()));

        auditService.record(tradeId, entry.getCorrelationId(), EventType.REPLAY.name(), "DLQ_REPLAY_FAILED",
                ProcessingStatus.DEAD_LETTER, "Replay #" + entry.getReplayCount() + " of " + entry.getId()
                        + " failed: " + event.getFailureReason() + (exhausted ? " (now PERMANENTLY_FAILED)" : ""));
        if (exhausted && entry.getLinkedWorkItemId() != null) {
            workItemService.escalate(entry.getLinkedWorkItemId(), entry.isReplayable()
                    ? "replay limit of " + properties.getMaxReplayAttempts() + " reached for " + entry.getId()
                    : "failure is not replayable: " + event.getFailureReason());
        }
        log.warn("Merged failed replay of trade '{}' into DLQ entry '{}' (status {})", tradeId, entry.getId(), entry.getDlqStatus());
        return Optional.of(entry);
    }

    /** True when the payload header says the message is a replay. Unparseable payloads carry no marker. */
    boolean hasReplayMarker(String value) {
        if (value == null) {
            return false;
        }
        try {
            JsonNode header = objectMapper.readTree(value).path("messageHeader");
            return EventType.REPLAY.name().equals(header.path("eventType").asText())
                    || header.path("replayCount").asInt(0) > 0;
        } catch (Exception ex) {
            return false;
        }
    }
}
