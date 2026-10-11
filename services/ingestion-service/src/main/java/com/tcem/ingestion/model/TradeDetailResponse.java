package com.tcem.ingestion.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tcem.ingestion.model.document.AuditDocument;
import com.tcem.ingestion.model.document.TradeDocument;

import java.util.Collections;
import java.util.List;

/**
 * Detailed trade representation returned by GET /trades/{id} including CouchDB audit trail (FR3, FR11).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TradeDetailResponse {

    private TradeDocument trade;
    private List<AuditDocument> auditTrail;

    public TradeDetailResponse() {}

    public TradeDetailResponse(TradeDocument trade, List<AuditDocument> auditTrail) {
        this.trade = trade;
        this.auditTrail = auditTrail != null ? auditTrail : Collections.emptyList();
    }

    public TradeDocument getTrade() { return trade; }
    public void setTrade(TradeDocument trade) { this.trade = trade; }

    public List<AuditDocument> getAuditTrail() { return auditTrail; }
    public void setAuditTrail(List<AuditDocument> auditTrail) { this.auditTrail = auditTrail; }
}
