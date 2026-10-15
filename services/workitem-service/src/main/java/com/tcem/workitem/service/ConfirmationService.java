package com.tcem.workitem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.ProcessingStatus;
import com.tcem.workitem.model.document.WorkItemDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Handles 'confirmation.generated' (FR6 output): records the confirmation in the audit trail and closes the
 * trade's remaining open work items, since a confirmed trade needs no further remediation.
 */
@Service
public class ConfirmationService {

    private static final Logger log = LoggerFactory.getLogger(ConfirmationService.class);

    private final ObjectMapper objectMapper;
    private final WorkItemService workItemService;
    private final AuditService auditService;

    public ConfirmationService(ObjectMapper objectMapper, WorkItemService workItemService, AuditService auditService) {
        this.objectMapper = objectMapper;
        this.workItemService = workItemService;
        this.auditService = auditService;
    }

    /** @return the work items closed because of this confirmation */
    public List<WorkItemDocument> onConfirmation(String key, String rawValue) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawValue);
        } catch (Exception ex) {
            log.error("Discarding unparseable record on confirmation.generated [key='{}']: {}", key, ex.getMessage());
            return List.of();
        }
        JsonNode confirmation = root.path("confirmation");
        String tradeId = text(confirmation, "tradeId", key);
        if (tradeId == null) {
            log.error("Discarding confirmation without a trade id");
            return List.of();
        }
        String confirmationId = text(confirmation, "confirmationId", "unknown");
        auditService.record(tradeId, text(root.path("messageHeader"), "correlationId", null),
                text(root.path("messageHeader"), "eventType", null), "CONFIRMATION_RECORDED",
                ProcessingStatus.CONFIRMED, "Confirmation " + confirmationId + " generated (template "
                        + text(confirmation, "templateId", "n/a") + ", status " + text(confirmation, "status", "n/a") + ")");
        List<WorkItemDocument> closed = workItemService.closeOpenForTrade(tradeId,
                "Trade confirmed by " + confirmationId);
        if (!closed.isEmpty()) {
            log.info("Closed {} open work item(s) for confirmed trade '{}'", closed.size(), tradeId);
        }
        return closed;
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : fallback;
    }
}
