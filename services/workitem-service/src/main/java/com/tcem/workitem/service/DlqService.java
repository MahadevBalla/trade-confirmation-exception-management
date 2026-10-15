package com.tcem.workitem.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.ProcessingStatus;
import com.tcem.common.event.DeadLetterEvent;
import com.tcem.common.model.message.MessageHeader;
import com.tcem.workitem.config.WorkItemProperties;
import com.tcem.workitem.model.document.DlqEntryDocument;
import com.tcem.workitem.model.document.WorkItemDocument;
import com.tcem.workitem.repository.DlqEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles messages arriving on 'deadletter.trade' (FR12): stores the DLQ entry, auto-creates a
 * DATA_FIX work item (FR10) and appends an audit record. A dead letter for a trade whose previous
 * failure is being replayed is merged into that entry instead (see {@link ReplayOutcomeService}).
 * Processing is idempotent on the DLQ message id so Kafka redeliveries do not duplicate work items.
 */
@Service
public class DlqService {

    private static final Logger log = LoggerFactory.getLogger(DlqService.class);

    /** Key prefixes ingestion-service uses when a message has no trade id. */
    static final List<String> PLACEHOLDER_KEY_PREFIXES = List.of("UNKNOWN_KEY_", "DLQ_");

    private final ObjectMapper objectMapper;
    private final DlqEntryRepository dlqRepository;
    private final WorkItemService workItemService;
    private final AuditService auditService;
    private final ReplayOutcomeService replayOutcomeService;
    private final WorkItemProperties properties;

    public DlqService(ObjectMapper objectMapper,
                      DlqEntryRepository dlqRepository,
                      WorkItemService workItemService,
                      AuditService auditService,
                      ReplayOutcomeService replayOutcomeService,
                      WorkItemProperties properties) {
        this.objectMapper = objectMapper;
        this.dlqRepository = dlqRepository;
        this.workItemService = workItemService;
        this.auditService = auditService;
        this.replayOutcomeService = replayOutcomeService;
        this.properties = properties;
    }

    /**
     * Processes a raw 'deadletter.trade' record.
     *
     * @return the stored (or merged-into) DLQ entry, or {@code null} when the payload was not a usable DeadLetterEvent
     */
    public DlqEntryDocument handleDeadLetter(String key, String rawValue) {
        DeadLetterEvent event;
        try {
            event = objectMapper.readValue(rawValue, DeadLetterEvent.class);
        } catch (Exception ex) {
            log.error("Discarding unparseable record on deadletter.trade [key='{}']: {}", key, ex.getMessage());
            return null;
        }

        String dlqId = resolveDlqId(key, event);
        Optional<DlqEntryDocument> existing = dlqRepository.findEntry(dlqId);
        if (existing.isPresent()) {
            log.info("Dead letter '{}' already recorded, skipping redelivery", dlqId);
            return existing.get();
        }

        String tradeId = resolveTradeId(key, event);
        Optional<DlqEntryDocument> merged = replayOutcomeService.mergeFailedReplay(tradeId, event, dlqId);
        if (merged.isPresent()) {
            return merged.get();
        }

        DlqEntryDocument entry = toEntry(dlqId, tradeId, event);
        boolean exhausted = !entry.isReplayable() || entry.getReplayCount() >= properties.getMaxReplayAttempts();
        if (exhausted) {
            entry.setDlqStatus(DlqEntryDocument.STATUS_PERMANENTLY_FAILED);
        }
        entry.getFailureHistory().add("Failed in " + entry.getFailedInService() + " on " + entry.getFailedOnTopic()
                + ": " + entry.getFailureReason());

        WorkItemDocument workItem = workItemService.create(workItemFor(entry, exhausted), entry.getCorrelationId());
        entry.setLinkedWorkItemId(workItem.getWorkItemId());
        dlqRepository.save(entry);

        auditService.record(tradeId != null ? tradeId : entry.getMessageId(), entry.getCorrelationId(),
                originalEventType(entry), "DLQ_RECEIVED", ProcessingStatus.DEAD_LETTER,
                "Dead letter from " + entry.getFailedInService() + " on " + entry.getFailedOnTopic()
                        + ": " + entry.getFailureReason() + " (work item " + workItem.getWorkItemId() + ")");
        return entry;
    }

    private WorkItemDocument workItemFor(DlqEntryDocument entry, boolean permanentlyFailed) {
        WorkItemDocument item = new WorkItemDocument();
        item.setLinkedDlqEntryId(entry.getId());
        item.setTradeId(entry.getTradeId());
        item.setType("DATA_FIX");
        item.setTitle((permanentlyFailed ? "Irrecoverable message: " : "Failed message awaiting replay: ")
                + (entry.getTradeId() != null ? entry.getTradeId() : entry.getMessageId()));
        item.setDescription("Service: " + entry.getFailedInService()
                + "\nTopic: " + entry.getFailedOnTopic()
                + "\nReason: " + entry.getFailureReason()
                + "\nReplayable: " + entry.isReplayable()
                + ", replays so far: " + entry.getReplayCount());
        item.setOwnerQueue(properties.getDlqOwnerQueue());
        item.setPriority(permanentlyFailed ? "HIGH" : "MEDIUM");
        return item;
    }

    private DlqEntryDocument toEntry(String dlqId, String tradeId, DeadLetterEvent event) {
        MessageHeader header = event.getMessageHeader();
        MessageHeader original = event.getOriginalMessageHeader();
        DlqEntryDocument entry = new DlqEntryDocument();
        entry.setId(dlqId);
        entry.setMessageId(header != null ? header.getMessageId() : null);
        entry.setCorrelationId(original != null ? original.getCorrelationId()
                : header != null ? header.getCorrelationId() : null);
        entry.setTradeId(tradeId);
        entry.setOriginalMessageHeader(original);
        entry.setOriginalPayload(event.getOriginalPayload());
        entry.setFailedOnTopic(event.getFailedOnTopic());
        entry.setFailedInService(event.getFailedInService());
        entry.setFailureReason(event.getFailureReason());
        entry.setExceptionClass(event.getExceptionClass());
        entry.setStackTraceExcerpt(event.getStackTraceExcerpt());
        entry.setFailedAt(event.getFailedAt());
        entry.setReceivedAt(Instant.now());
        entry.setReplayCount(Math.max(event.getReplayCount(),
                original != null ? original.getReplayCount() : 0));
        entry.setReplayable(event.isReplayable());
        entry.setDlqStatus(event.getDlqStatus() != null ? event.getDlqStatus() : DlqEntryDocument.STATUS_PENDING_REPLAY);
        return entry;
    }

    private static String originalEventType(DlqEntryDocument entry) {
        MessageHeader original = entry.getOriginalMessageHeader();
        return original != null && original.getEventType() != null ? original.getEventType().name() : null;
    }

    /**
     * The Kafka key is the tradeId when known. Ingestion uses placeholder keys when a message has no
     * usable trade id ({@code UNKNOWN_KEY_<ts>} from TradeRawProducer, {@code DLQ_<ts>} from DeadLetterProducer).
     * Those must not be stored as trade ids.
     */
    String resolveTradeId(String key, DeadLetterEvent event) {
        if (key == null || key.isBlank()) {
            return null;
        }
        for (String placeholder : PLACEHOLDER_KEY_PREFIXES) {
            if (key.startsWith(placeholder)) {
                return null;
            }
        }
        return key;
    }

    private String resolveDlqId(String key, DeadLetterEvent event) {
        MessageHeader header = event.getMessageHeader();
        if (header != null && header.getMessageId() != null && !header.getMessageId().isBlank()) {
            return "dlq:" + header.getMessageId();
        }
        return "dlq:" + UUID.nameUUIDFromBytes(((key != null ? key : "") + "|" + event.getFailureReason()
                + "|" + event.getFailedAt()).getBytes(StandardCharsets.UTF_8));
    }
}
