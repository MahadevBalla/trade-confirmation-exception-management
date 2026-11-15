package com.tcem.workitem.service;

import com.tcem.common.constants.EventType;
import com.tcem.common.constants.ProcessingStatus;
import com.tcem.common.event.WorkItemEvent;
import com.tcem.common.model.message.MessageHeader;
import com.tcem.workitem.config.WorkItemProperties;
import com.tcem.workitem.model.document.WorkItemDocument;
import com.tcem.workitem.producer.WorkItemLinkedProducer;
import com.tcem.workitem.repository.WorkItemRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.tcem.workitem.service.WorkItemException.Reason.CONFLICT;
import static com.tcem.workitem.service.WorkItemException.Reason.INVALID_REQUEST;
import static com.tcem.workitem.service.WorkItemException.Reason.NOT_FOUND;

/**
 * The mock work-item tracker (FR10). Every create, link and update goes through here so each change is
 * persisted, audited and announced on 'workitem.linked' in the same way.
 */
@Service
public class WorkItemService {

    private static final int LOOKUP_LIMIT = 100;

    private final WorkItemRepository repository;
    private final WorkItemLinkedProducer producer;
    private final AuditService auditService;
    private final WorkItemProperties properties;

    public WorkItemService(WorkItemRepository repository,
                           WorkItemLinkedProducer producer,
                           AuditService auditService,
                           WorkItemProperties properties) {
        this.repository = repository;
        this.producer = producer;
        this.auditService = auditService;
        this.properties = properties;
    }

    public WorkItemDocument get(String workItemId) {
        return repository.findById(workItemId)
                .orElseThrow(() -> new WorkItemException(NOT_FOUND, "Work item '" + workItemId + "' not found"));
    }

    /** Persists a new OPEN work item built by the caller and announces it. */
    public WorkItemDocument create(WorkItemDocument draft, String correlationId) {
        String workItemId = "WI-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        draft.setId(workItemId);
        draft.setWorkItemId(workItemId);
        draft.setStatus("OPEN");
        draft.setCreatedAt(Instant.now());
        if (draft.getLinkedExceptionId() != null && !draft.getLinkedExceptionIds().contains(draft.getLinkedExceptionId())) {
            draft.getLinkedExceptionIds().add(draft.getLinkedExceptionId());
        }
        repository.save(draft);
        auditService.record(draft.getTradeId(), correlationId, null, "WORKITEM_CREATED", ProcessingStatus.WORKITEM_LINKED,
                draft.getType() + " work item " + workItemId + " created for " + draft.getOwnerQueue()
                        + " (" + draft.getPriority() + "): " + draft.getTitle());
        publish(draft, correlationId);
        return draft;
    }

    /** Validates and creates a work item requested through the REST API. */
    public WorkItemDocument createManual(String tradeId, String type, String title, String description,
                                         String ownerQueue, String priority, String linkedExceptionId) {
        require(type != null && WorkItemRules.TYPES.contains(type), "type must be one of " + WorkItemRules.TYPES);
        require(title != null && !title.isBlank(), "title is required");
        String queue = ownerQueue != null ? ownerQueue : "OPERATIONS_REVIEW";
        require(WorkItemRules.OWNER_QUEUES.contains(queue), "ownerQueue must be one of " + WorkItemRules.OWNER_QUEUES);
        String prio = priority != null ? priority : "MEDIUM";
        require(WorkItemRules.PRIORITIES.contains(prio), "priority must be one of " + WorkItemRules.PRIORITIES);

        WorkItemDocument draft = new WorkItemDocument();
        draft.setTradeId(tradeId);
        draft.setType(type);
        draft.setTitle(title);
        draft.setDescription(description);
        draft.setOwnerQueue(queue);
        draft.setPriority(prio);
        draft.setLinkedExceptionId(linkedExceptionId);
        return create(draft, null);
    }

    /**
     * Links an exception to an open work item of the same type for the same trade, or creates a new one.
     * Linking keeps the higher of the two priorities.
     */
    public WorkItemDocument linkOrCreate(WorkItemDocument candidate, String exceptionId, String correlationId) {
        Optional<WorkItemDocument> open = candidate.getTradeId() == null ? Optional.empty()
                : repository.findOpenByTrade(candidate.getTradeId()).stream()
                        .filter(item -> candidate.getType().equals(item.getType()))
                        .findFirst();
        if (open.isEmpty()) {
            candidate.setLinkedExceptionId(exceptionId);
            return create(candidate, correlationId);
        }

        WorkItemDocument item = open.get();
        if (exceptionId != null && !item.getLinkedExceptionIds().contains(exceptionId)) {
            item.getLinkedExceptionIds().add(exceptionId);
        }
        if (item.getLinkedExceptionId() == null) {
            item.setLinkedExceptionId(exceptionId);
        }
        item.setPriority(WorkItemRules.higherPriority(item.getPriority(), candidate.getPriority()));
        item.setDescription(appendLine(item.getDescription(), "Linked exception " + exceptionId + ": " + candidate.getTitle()));
        repository.save(item);
        auditService.record(item.getTradeId(), correlationId, null, "WORKITEM_LINKED", ProcessingStatus.WORKITEM_LINKED,
                "Exception " + exceptionId + " linked to existing work item " + item.getWorkItemId());
        publish(item, correlationId);
        return item;
    }

    /** Finds the work item of the given type that an exception is already linked to (idempotency). */
    public Optional<WorkItemDocument> findForException(String tradeId, String exceptionId, String type) {
        if (exceptionId == null) {
            return Optional.empty();
        }
        List<WorkItemDocument> candidates = tradeId != null
                ? repository.findByTrade(tradeId)
                : repository.findPage(Map.of("linkedExceptionId", exceptionId), 0, LOOKUP_LIMIT);
        return candidates.stream()
                .filter(item -> type.equals(item.getType()))
                .filter(item -> exceptionId.equals(item.getLinkedExceptionId())
                        || item.getLinkedExceptionIds().contains(exceptionId))
                .findFirst();
    }

    /** Applies a PATCH: any of status, priority and owner queue. Terminal items cannot change. */
    public WorkItemDocument update(String workItemId, String status, String priority, String ownerQueue,
                                   String notes, String actor) {
        require(status == null || WorkItemRules.STATUSES.contains(status), "status must be one of " + WorkItemRules.STATUSES);
        require(priority == null || WorkItemRules.PRIORITIES.contains(priority), "priority must be one of " + WorkItemRules.PRIORITIES);
        require(ownerQueue == null || WorkItemRules.OWNER_QUEUES.contains(ownerQueue), "ownerQueue must be one of " + WorkItemRules.OWNER_QUEUES);
        require(status != null || priority != null || ownerQueue != null, "nothing to update: give status, priority or ownerQueue");

        WorkItemDocument item = get(workItemId);
        if (!item.isOpen()) {
            throw new WorkItemException(CONFLICT, "Work item '" + workItemId + "' is " + item.getStatus() + " and can no longer change");
        }
        List<String> changes = new ArrayList<>();
        if (status != null && !status.equals(item.getStatus())) {
            if (!WorkItemRules.canTransition(item.getStatus(), status)) {
                throw new WorkItemException(CONFLICT, "Cannot move work item from " + item.getStatus() + " to " + status);
            }
            changes.add("status " + item.getStatus() + " -> " + status);
            applyStatus(item, status, notes);
        }
        if (priority != null && !priority.equals(item.getPriority())) {
            changes.add("priority " + item.getPriority() + " -> " + priority);
            item.setPriority(priority);
        }
        if (ownerQueue != null && !ownerQueue.equals(item.getOwnerQueue())) {
            changes.add("ownerQueue " + item.getOwnerQueue() + " -> " + ownerQueue);
            item.setOwnerQueue(ownerQueue);
        }
        if (changes.isEmpty()) {
            return item;
        }
        return saveChange(item, "WORKITEM_UPDATED", String.join(", ", changes)
                + (actor != null ? " by " + actor : "") + (notes != null ? ". Notes: " + notes : ""));
    }

    /** Closes one open item (system-initiated, e.g. replay succeeded). Already-closed items are left alone. */
    public Optional<WorkItemDocument> close(String workItemId, String note) {
        return repository.findById(workItemId)
                .filter(WorkItemDocument::isOpen)
                .map(item -> {
                    applyStatus(item, "DONE", note);
                    return saveChange(item, "WORKITEM_CLOSED", note);
                });
    }

    /** Closes every open work item of a trade, e.g. once its confirmation has been generated. */
    public List<WorkItemDocument> closeOpenForTrade(String tradeId, String note) {
        return repository.findOpenByTrade(tradeId).stream()
                .map(item -> {
                    applyStatus(item, "DONE", note);
                    return saveChange(item, "WORKITEM_CLOSED", note);
                })
                .toList();
    }

    /** Cancels every open work item of a trade, e.g. once the trade itself has been cancelled. */
    public List<WorkItemDocument> cancelOpenForTrade(String tradeId, String note) {
        return repository.findOpenByTrade(tradeId).stream()
                .map(item -> {
                    applyStatus(item, "CANCELLED", note);
                    return saveChange(item, "WORKITEM_CANCELLED", note);
                })
                .toList();
    }

    /** Raises an item to HIGH (never lowers CRITICAL) and records why. */
    public Optional<WorkItemDocument> escalate(String workItemId, String reason) {
        return repository.findById(workItemId).map(item -> {
            item.setPriority(WorkItemRules.higherPriority(item.getPriority(), "HIGH"));
            item.setDescription(appendLine(item.getDescription(), "Escalated: " + reason));
            return saveChange(item, "WORKITEM_ESCALATED", reason);
        });
    }

    /** Records a manual-review decision on the item and closes it. */
    WorkItemDocument recordReview(WorkItemDocument item, String decision, String notes, String reviewer) {
        item.setReviewDecision(decision);
        item.setReviewNotes(notes);
        item.setReviewedBy(reviewer);
        applyStatus(item, "DONE", "Manual review " + decision.toLowerCase() + (notes != null ? ": " + notes : ""));
        return saveChange(item, "MANUAL_REVIEW_" + decision,
                "Reviewed by " + (reviewer != null ? reviewer : "unknown") + (notes != null ? ". Notes: " + notes : ""));
    }

    /** Dashboard counts (FR11): totals by status, type, owner queue and priority, plus aged open items. */
    public Map<String, Object> summary() {
        List<WorkItemDocument> all = repository.findAll();
        Instant agedBefore = Instant.now().minus(Duration.ofHours(properties.getAgedHours()));
        List<WorkItemDocument> open = all.stream().filter(WorkItemDocument::isOpen).toList();
        Map<String, Object> summary = new TreeMap<>();
        summary.put("total", all.size());
        summary.put("open", open.size());
        summary.put("agedOpen", open.stream()
                .filter(item -> item.getCreatedAt() != null && item.getCreatedAt().isBefore(agedBefore))
                .count());
        summary.put("agedHours", properties.getAgedHours());
        summary.put("byStatus", countBy(all, WorkItemDocument::getStatus));
        summary.put("byType", countBy(all, WorkItemDocument::getType));
        summary.put("openByOwnerQueue", countBy(open, WorkItemDocument::getOwnerQueue));
        summary.put("openByPriority", countBy(open, WorkItemDocument::getPriority));
        return summary;
    }

    private void applyStatus(WorkItemDocument item, String status, String note) {
        item.setStatus(status);
        if (WorkItemRules.TERMINAL_STATUSES.contains(status)) {
            item.setResolvedAt(Instant.now());
            if (note != null) {
                item.setResolutionNotes(note);
            }
        } else {
            item.setResolvedAt(null);
        }
    }

    private WorkItemDocument saveChange(WorkItemDocument item, String stage, String details) {
        repository.save(item);
        ProcessingStatus auditStatus = WorkItemRules.TERMINAL_STATUSES.contains(item.getStatus())
                ? ProcessingStatus.RESOLVED : ProcessingStatus.WORKITEM_LINKED;
        auditService.record(item.getTradeId(), null, null, stage, auditStatus,
                "Work item " + item.getWorkItemId() + ": " + details);
        publish(item, null);
        return item;
    }

    private void publish(WorkItemDocument item, String correlationId) {
        MessageHeader header = new MessageHeader();
        header.setMessageId("WIL-" + UUID.randomUUID().toString().substring(0, 8));
        header.setCorrelationId(correlationId != null ? correlationId : item.getTradeId());
        header.setEventType(EventType.NEW_TRADE);
        header.setSourceService("workitem-service");
        header.setCreatedTimestamp(Instant.now());
        String key = item.getTradeId() != null ? item.getTradeId() : item.getWorkItemId();
        producer.sendWorkItemLinked(key, new WorkItemEvent(header, item.toWorkItem()));
    }

    private static Map<String, Long> countBy(List<WorkItemDocument> items, Function<WorkItemDocument, String> field) {
        return items.stream().collect(Collectors.groupingBy(
                item -> field.apply(item) != null ? field.apply(item) : "UNKNOWN", TreeMap::new, Collectors.counting()));
    }

    private static String appendLine(String text, String line) {
        return text == null || text.isBlank() ? line : text + "\n" + line;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new WorkItemException(INVALID_REQUEST, message);
        }
    }
}
