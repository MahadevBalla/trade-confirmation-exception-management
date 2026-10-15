package com.tcem.workitem.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.ProcessingStatus;
import com.tcem.common.event.ExceptionEvent;
import com.tcem.common.model.exception.ExceptionRecord;
import com.tcem.workitem.config.WorkItemProperties;
import com.tcem.workitem.model.document.WorkItemDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Creates or links work items for exceptions (FR10).
 * <ul>
 *   <li>{@code exception.classified}: work-item type, owner queue and priority come from the
 *       classification and recommendation (see {@link WorkItemRules}). Low-confidence classifications
 *       go to a MANUAL_REVIEW item so a person confirms them.</li>
 *   <li>{@code manual.review.required}: always a MANUAL_REVIEW item, decided via
 *       {@link ManualReviewService}.</li>
 * </ul>
 * Each (exception, work-item type) pair is handled once, so redeliveries are no-ops.
 */
@Service
public class ExceptionLinkingService {

    private static final Logger log = LoggerFactory.getLogger(ExceptionLinkingService.class);

    private final ObjectMapper objectMapper;
    private final WorkItemService workItemService;
    private final AuditService auditService;
    private final WorkItemProperties properties;

    public ExceptionLinkingService(ObjectMapper objectMapper,
                                   WorkItemService workItemService,
                                   AuditService auditService,
                                   WorkItemProperties properties) {
        this.objectMapper = objectMapper;
        this.workItemService = workItemService;
        this.auditService = auditService;
        this.properties = properties;
    }

    public WorkItemDocument onClassified(String rawValue) {
        ExceptionEvent event = parse(rawValue, "exception.classified");
        if (event == null) {
            return null;
        }
        ExceptionRecord record = event.getException();
        boolean lowConfidence = record.getClassificationConfidence() < properties.getLowConfidenceThreshold();
        String type = lowConfidence ? "MANUAL_REVIEW" : WorkItemRules.typeFor(record.getCategory());
        String queue = lowConfidence ? "OPERATIONS_REVIEW"
                : WorkItemRules.queueFor(record.getOwnerQueue(), record.getCategory());
        String reason = lowConfidence
                ? "Low classification confidence " + record.getClassificationConfidence()
                        + " (< " + properties.getLowConfidenceThreshold() + "): confirm the classification"
                : null;
        return link(event, type, queue, reason);
    }

    public WorkItemDocument onManualReview(String rawValue) {
        ExceptionEvent event = parse(rawValue, "manual.review.required");
        if (event == null) {
            return null;
        }
        ExceptionRecord record = event.getException();
        String queue = record.getOwnerQueue() != null && WorkItemRules.OWNER_QUEUES.contains(record.getOwnerQueue())
                ? record.getOwnerQueue() : "OPERATIONS_REVIEW";
        auditService.record(record.getTradeId(), record.getCorrelationId(), null, "MANUAL_REVIEW_REQUIRED",
                ProcessingStatus.MANUAL_REVIEW, "Exception " + record.getExceptionId() + " routed to manual review");
        return link(event, "MANUAL_REVIEW", queue, "Routed to manual review by exception-service");
    }

    private WorkItemDocument link(ExceptionEvent event, String type, String queue, String reason) {
        ExceptionRecord record = event.getException();
        Optional<WorkItemDocument> existing =
                workItemService.findForException(record.getTradeId(), record.getExceptionId(), type);
        if (existing.isPresent()) {
            log.info("Exception '{}' already linked to {} work item '{}', skipping",
                    record.getExceptionId(), type, existing.get().getWorkItemId());
            return existing.get();
        }

        WorkItemDocument candidate = new WorkItemDocument();
        candidate.setTradeId(record.getTradeId());
        candidate.setType(type);
        candidate.setOwnerQueue(queue);
        candidate.setPriority(WorkItemRules.priorityFor(record.getOperationalImpact(), record.getResolutionDifficulty()));
        candidate.setCategory(record.getCategory() != null ? record.getCategory().name() : null);
        candidate.setSubTheme(record.getSubTheme() != null ? record.getSubTheme().name() : null);
        candidate.setTitle(title(record, type));
        candidate.setDescription(description(record, reason));

        String correlationId = record.getCorrelationId() != null ? record.getCorrelationId()
                : event.getMessageHeader() != null ? event.getMessageHeader().getCorrelationId() : null;
        return workItemService.linkOrCreate(candidate, record.getExceptionId(), correlationId);
    }

    private ExceptionEvent parse(String rawValue, String topic) {
        try {
            ExceptionEvent event = objectMapper.readValue(rawValue, ExceptionEvent.class);
            if (event == null || event.getException() == null) {
                log.error("Discarding record on {} without an exception payload", topic);
                return null;
            }
            return event;
        } catch (Exception ex) {
            log.error("Discarding unparseable record on {}: {}", topic, ex.getMessage());
            return null;
        }
    }

    private static String title(ExceptionRecord record, String type) {
        String theme = record.getSubTheme() != null ? record.getSubTheme().name()
                : record.getCategory() != null ? record.getCategory().name() : "UNCLASSIFIED";
        String prefix = "MANUAL_REVIEW".equals(type) ? "Review " : "";
        return prefix + theme + " for " + (record.getTradeId() != null ? record.getTradeId() : record.getExceptionId());
    }

    private static String description(ExceptionRecord record, String reason) {
        StringBuilder text = new StringBuilder();
        if (reason != null) {
            text.append(reason).append('\n');
        }
        text.append("Exception: ").append(record.getExceptionId())
                .append("\nCategory: ").append(record.getCategory())
                .append(" / ").append(record.getSubTheme())
                .append("\nReason code: ").append(record.getReasonCode())
                .append("\nAffected fields: ").append(record.getAffectedFields())
                .append("\nConfidence: ").append(record.getClassificationConfidence())
                .append("\nRecommended action: ").append(record.getRecommendedAction())
                .append("\nReplay may resolve: ").append(record.isReplayMayResolve());
        if (record.getSlaDeadline() != null) {
            text.append("\nSLA deadline: ").append(record.getSlaDeadline());
        }
        return text.toString();
    }
}
