package com.tcem.workitem.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.EventType;
import com.tcem.common.constants.TopicNames;
import com.tcem.common.event.DeadLetterEvent;
import com.tcem.common.model.message.MessageHeader;
import com.tcem.workitem.model.document.WorkItemDocument;
import com.tcem.workitem.producer.DeadLetterPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.tcem.workitem.service.WorkItemException.Reason.CONFLICT;
import static com.tcem.workitem.service.WorkItemException.Reason.INVALID_REQUEST;

/**
 * Decisions on the manual review queue (MANUAL_REVIEW work items).
 * <ul>
 *   <li>APPROVE closes the item as DONE. Generating the confirmation stays with validation-service,
 *       which can react to the closed item on 'workitem.linked'.</li>
 *   <li>REJECT closes the item and sends the trade to 'deadletter.trade' as non-replayable, mirroring
 *       the simulated flow in the frontend. The DLQ consumer then records it like any other dead letter.</li>
 * </ul>
 */
@Service
public class ManualReviewService {

    public static final String APPROVE = "APPROVE";
    public static final String REJECT = "REJECT";

    private final WorkItemService workItemService;
    private final DeadLetterPublisher deadLetterPublisher;
    private final ObjectMapper objectMapper;

    public ManualReviewService(WorkItemService workItemService,
                               DeadLetterPublisher deadLetterPublisher,
                               ObjectMapper objectMapper) {
        this.workItemService = workItemService;
        this.deadLetterPublisher = deadLetterPublisher;
        this.objectMapper = objectMapper;
    }

    public WorkItemDocument decide(String workItemId, String decision, String notes, String reviewer) {
        if (!APPROVE.equals(decision) && !REJECT.equals(decision)) {
            throw new WorkItemException(INVALID_REQUEST, "decision must be APPROVE or REJECT");
        }
        if (REJECT.equals(decision) && (notes == null || notes.isBlank())) {
            throw new WorkItemException(INVALID_REQUEST, "notes are required to reject a review");
        }
        WorkItemDocument item = workItemService.get(workItemId);
        if (!"MANUAL_REVIEW".equals(item.getType())) {
            throw new WorkItemException(CONFLICT, "Work item '" + workItemId + "' is " + item.getType()
                    + ", not a MANUAL_REVIEW item");
        }
        if (!item.isOpen()) {
            throw new WorkItemException(CONFLICT, "Work item '" + workItemId + "' is already " + item.getStatus());
        }

        WorkItemDocument decided = workItemService.recordReview(item,
                APPROVE.equals(decision) ? "APPROVED" : "REJECTED", notes, reviewer);
        if (REJECT.equals(decision)) {
            String key = item.getTradeId() != null ? item.getTradeId() : item.getWorkItemId();
            deadLetterPublisher.publish(key, rejectionDeadLetter(decided, notes));
        }
        return decided;
    }

    private DeadLetterEvent rejectionDeadLetter(WorkItemDocument item, String notes) {
        MessageHeader header = new MessageHeader();
        header.setMessageId("DLQ-MR-" + UUID.randomUUID().toString().substring(0, 8));
        header.setCorrelationId(item.getTradeId());
        header.setEventType(EventType.NEW_TRADE);
        header.setSourceService("workitem-service");
        header.setCreatedTimestamp(Instant.now());

        return DeadLetterEvent.builder()
                .messageHeader(header)
                .originalPayload(reviewPayload(item))
                .failedOnTopic(TopicNames.MANUAL_REVIEW_REQUIRED)
                .failedInService("workitem-service")
                .failureReason("Rejected in manual review: " + notes)
                .failedAt(Instant.now())
                .replayCount(0)
                .replayable(false)
                .dlqStatus("PERMANENTLY_FAILED")
                .build();
    }

    private String reviewPayload(WorkItemDocument item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workItemId", item.getWorkItemId());
        payload.put("tradeId", item.getTradeId());
        payload.put("linkedExceptionIds", item.getLinkedExceptionIds());
        payload.put("reviewedBy", item.getReviewedBy());
        payload.put("reviewNotes", item.getReviewNotes());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return payload.toString();
        }
    }
}
