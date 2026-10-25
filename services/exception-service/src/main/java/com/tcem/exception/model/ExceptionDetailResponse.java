package com.tcem.exception.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tcem.exception.model.document.AuditDocument;
import com.tcem.exception.model.document.ExceptionDocument;

import java.util.Collections;
import java.util.List;

/**
 * REST response DTO for a single exception — returned by {@code GET /exceptions/{id}} (FR11).
 * <p>
 * Wraps the raw {@link ExceptionDocument} CouchDB record together with its
 * complete audit trail so the React Operations UI can render a timeline view
 * on the exception detail page.
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExceptionDetailResponse {

    private ExceptionDocument exception;
    private List<AuditDocument> auditTrail;

    public ExceptionDetailResponse() {}

    public ExceptionDetailResponse(ExceptionDocument exception, List<AuditDocument> auditTrail) {
        this.exception  = exception;
        this.auditTrail = auditTrail != null ? auditTrail : Collections.emptyList();
    }

    public ExceptionDocument getException() { return exception; }
    public void setException(ExceptionDocument exception) { this.exception = exception; }

    public List<AuditDocument> getAuditTrail() { return auditTrail; }
    public void setAuditTrail(List<AuditDocument> auditTrail) {
        this.auditTrail = auditTrail != null ? auditTrail : Collections.emptyList();
    }
}

