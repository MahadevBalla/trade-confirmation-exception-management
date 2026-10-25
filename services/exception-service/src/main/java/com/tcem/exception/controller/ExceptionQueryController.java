package com.tcem.exception.controller;

import com.tcem.common.constants.ExceptionCategory;
import com.tcem.exception.model.ExceptionDetailResponse;
import com.tcem.exception.model.ExceptionListResponse;
import com.tcem.exception.model.ExceptionMetricsSummary;
import com.tcem.exception.model.document.AuditDocument;
import com.tcem.exception.model.document.ExceptionDocument;
import com.tcem.exception.repository.AuditCouchDbRepository;
import com.tcem.exception.repository.ExceptionCouchDbRepository;
import com.tcem.exception.service.ExceptionMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller serving the exception query and metrics endpoints for the
 * React Operations UI (FR11).
 * <p>
 * All endpoints are read-only (GET). Mutations happen exclusively through the
 * Kafka pipeline. CORS is open to allow the frontend (port 3000/5173) to call
 * directly during development; production routing goes through Nginx.
 * </p>
 *
 * <h3>Endpoint summary</h3>
 * <ul>
 *   <li>{@code GET /exceptions/{id}}                             — single exception detail + audit trail</li>
 *   <li>{@code GET /exceptions}                                  — paginated list with optional filters</li>
 *   <li>{@code GET /exceptions/{id}/audit}                       — audit trail for a specific exception</li>
 *   <li>{@code GET /exceptions/metrics/summary}                  — aggregate in-memory counters</li>
 *   <li>{@code GET /metrics/exception-rate}                      — classification rate for dashboard widget</li>
 * </ul>
 */
@RestController
@CrossOrigin(origins = "*")
@RequestMapping
public class ExceptionQueryController {

    private static final Logger log = LoggerFactory.getLogger(ExceptionQueryController.class);

    private final ExceptionCouchDbRepository exceptionRepository;
    private final AuditCouchDbRepository auditRepository;
    private final ExceptionMetricsService metricsService;

    public ExceptionQueryController(ExceptionCouchDbRepository exceptionRepository,
                                    AuditCouchDbRepository auditRepository,
                                    ExceptionMetricsService metricsService) {
        this.exceptionRepository = exceptionRepository;
        this.auditRepository     = auditRepository;
        this.metricsService      = metricsService;
    }

    // =========================================================================
    // GET /exceptions/{id}
    // =========================================================================

    /**
     * Returns a single exception by its {@code exceptionId} together with the full audit trail.
     *
     * @param id the exceptionId (e.g. {@code EXC-30001})
     * @return 200 with {@link ExceptionDetailResponse}, or 404 if not found
     */
    @GetMapping("/exceptions/{id}")
    public ResponseEntity<?> getExceptionById(@PathVariable("id") String id) {
        log.debug("GET /exceptions/{}", id);

        Optional<ExceptionDocument> exceptionOpt = exceptionRepository.findById(id);
        if (exceptionOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "Exception not found",
                    "exceptionId", id,
                    "status", HttpStatus.NOT_FOUND.value()
            ));
        }

        List<AuditDocument> auditTrail;
        try {
            auditTrail = auditRepository.findByExceptionId(id);
        } catch (Exception ex) {
            log.warn("Failed to fetch audit trail for exception '{}': {}", id, ex.getMessage());
            auditTrail = Collections.emptyList();
        }

        return ResponseEntity.ok(new ExceptionDetailResponse(exceptionOpt.get(), auditTrail));
    }

    // =========================================================================
    // GET /exceptions?tradeId=&status=&category=&page=&size=
    // =========================================================================

    /**
     * Returns a paginated, optionally filtered list of exception documents.
     *
     * <p>Filter precedence: {@code tradeId} overrides {@code category} and {@code status}
     * because a tradeId query is typically a point lookup. When {@code tradeId} is present,
     * {@code status} and {@code category} are ignored.</p>
     *
     * @param tradeId  optional — returns all exceptions for this trade
     * @param status   optional — filter by lifecycle status (OPEN, IN_PROGRESS, RESOLVED, CLOSED, REPLAYED)
     * @param category optional — filter by {@link ExceptionCategory} name
     * @param page     0-based page index (default 0)
     * @param size     page size (default 50)
     */
    @GetMapping("/exceptions")
    public ResponseEntity<ExceptionListResponse> listExceptions(
            @RequestParam(name = "tradeId",   required = false) String tradeId,
            @RequestParam(name = "status",    required = false) String status,
            @RequestParam(name = "category",  required = false) String category,
            @RequestParam(name = "page",      defaultValue = "0")  int page,
            @RequestParam(name = "size",      defaultValue = "50") int size) {

        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 200)); // cap at 200
        int skip     = safePage * safeSize;

        List<ExceptionDocument> exceptions;

        if (tradeId != null && !tradeId.isBlank()) {
            // Point lookup by tradeId — ignore other filters
            exceptions = exceptionRepository.findByTradeId(tradeId.trim());
        } else if (category != null && !category.isBlank()) {
            ExceptionCategory parsedCategory = parseCategory(category);
            exceptions = (parsedCategory != null)
                    ? exceptionRepository.findByCategory(parsedCategory, safeSize, skip)
                    : exceptionRepository.findByStatus(null, safeSize, skip);
        } else {
            exceptions = exceptionRepository.findByStatus(
                    (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null,
                    safeSize, skip);
        }

        return ResponseEntity.ok(new ExceptionListResponse(
                exceptions, safePage, safeSize, tradeId, status, category));
    }

    // =========================================================================
    // GET /exceptions/{id}/audit
    // =========================================================================

    /**
     * Returns the full audit trail for a given exception (identified by {@code exceptionId}).
     *
     * @param id the exceptionId
     * @return ordered list of {@link AuditDocument} records (may be empty)
     */
    @GetMapping("/exceptions/{id}/audit")
    public ResponseEntity<List<AuditDocument>> getExceptionAuditTrail(@PathVariable("id") String id) {
        log.debug("GET /exceptions/{}/audit", id);
        List<AuditDocument> auditTrail;
        try {
            auditTrail = auditRepository.findByExceptionId(id);
        } catch (Exception ex) {
            log.warn("Failed to fetch audit trail for exception '{}': {}", id, ex.getMessage());
            auditTrail = Collections.emptyList();
        }
        return ResponseEntity.ok(auditTrail);
    }

    // =========================================================================
    // GET /exceptions/metrics/summary
    // =========================================================================

    /**
     * Returns aggregate in-memory counters for the exception pipeline.
     * Suitable for dashboard counters and alert thresholds.
     */
    @GetMapping("/exceptions/metrics/summary")
    public ResponseEntity<ExceptionMetricsSummary> getMetricsSummary() {
        log.debug("GET /exceptions/metrics/summary");
        return ResponseEntity.ok(metricsService.getSummary());
    }

    // =========================================================================
    // GET /metrics/exception-rate
    // =========================================================================

    /**
     * Returns the exception classification rate formatted for the React dashboard widget (FR11).
     * Mirrors the shape of {@code /metrics/stp-rate} from the ingestion-service.
     */
    @GetMapping({"/metrics/exception-rate", "/exceptions/metrics/exception-rate"})
    public ResponseEntity<Map<String, Object>> getExceptionRate() {
        log.debug("GET /metrics/exception-rate");
        return ResponseEntity.ok(metricsService.getExceptionRate());
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private ExceptionCategory parseCategory(String category) {
        try {
            return ExceptionCategory.valueOf(category.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.debug("Unknown ExceptionCategory filter '{}', ignoring", category);
            return null;
        }
    }
}

