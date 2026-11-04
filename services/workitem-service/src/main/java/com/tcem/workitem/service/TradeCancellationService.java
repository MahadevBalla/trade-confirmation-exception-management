package com.tcem.workitem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.EventType;
import com.tcem.common.constants.ProcessingStatus;
import com.tcem.workitem.model.document.WorkItemDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Handles cancelled outcomes on 'stp.validated': records the cancellation in the audit trail and cancels the
 * trade's open work items, since a cancelled trade needs no further remediation.
 */
@Service
public class TradeCancellationService {

    private static final Logger log = LoggerFactory.getLogger(TradeCancellationService.class);

    private final ObjectMapper objectMapper;
    private final WorkItemService workItemService;
    private final AuditService auditService;

    public TradeCancellationService(ObjectMapper objectMapper, WorkItemService workItemService, AuditService auditService) {
        this.objectMapper = objectMapper;
        this.workItemService = workItemService;
        this.auditService = auditService;
    }

    /** @return the work items cancelled; empty for any record that does not report a cancellation */
    public List<WorkItemDocument> onStpValidated(String key, String rawValue) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawValue);
        } catch (Exception ex) {
            log.debug("Ignoring unparseable record on stp.validated [key='{}']: {}", key, ex.getMessage());
            return List.of();
        }
        if (root == null || !root.path("cancelled").asBoolean(false)) {
            return List.of();
        }
        String tradeId = root.path("tradeId").asText(key);
        if (tradeId == null || tradeId.isBlank()) {
            log.error("Discarding cancelled outcome without a trade id");
            return List.of();
        }
        List<WorkItemDocument> cancelled = workItemService.cancelOpenForTrade(tradeId, "Trade cancelled by CANCEL_TRADE");
        auditService.record(tradeId, root.path("messageHeader").path("correlationId").asText(null),
                EventType.CANCEL_TRADE.name(), "TRADE_CANCELLED", ProcessingStatus.CANCELLED,
                "Trade cancelled; " + cancelled.size() + " open work item(s) cancelled");
        log.info("Trade '{}' cancelled; cancelled {} open work item(s)", tradeId, cancelled.size());
        return cancelled;
    }
}
