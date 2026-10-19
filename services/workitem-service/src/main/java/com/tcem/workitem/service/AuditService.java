package com.tcem.workitem.service;

import com.tcem.common.constants.ProcessingStatus;
import com.tcem.workitem.model.document.AuditDocument;
import com.tcem.workitem.repository.AuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Appends audit entries to 'audit-events'. Audit is best-effort: a CouchDB failure is logged and never
 * aborts the business operation that triggered it.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditRepository auditRepository;

    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    public void record(String tradeId, String correlationId, String eventType, String stage,
                       ProcessingStatus status, String details) {
        try {
            AuditDocument doc = new AuditDocument();
            doc.setTradeId(tradeId);
            doc.setCorrelationId(correlationId);
            doc.setEventType(eventType != null ? eventType : "UNKNOWN");
            doc.setStage(stage);
            doc.setStatus(status);
            doc.setDetails(details);
            auditRepository.save(doc);
        } catch (Exception ex) {
            log.warn("Failed to write audit record [trade={}, stage={}]: {}", tradeId, stage, ex.getMessage());
        }
    }
}
