package com.tcem.ingestion.controller;

import com.tcem.common.constants.ProcessingStatus;
import com.tcem.ingestion.model.IngestionMetricsSummary;
import com.tcem.ingestion.model.TradeDetailResponse;
import com.tcem.ingestion.model.TradeListResponse;
import com.tcem.ingestion.model.document.AuditDocument;
import com.tcem.ingestion.model.document.TradeDocument;
import com.tcem.ingestion.repository.AuditCouchDbRepository;
import com.tcem.ingestion.repository.TradeCouchDbRepository;
import com.tcem.ingestion.service.IngestionMetricsService;
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
 * REST controller serving operations dashboards and audit views for trades (FR3, FR11).
 * Consumed by the React Operations UI (Port 3000).
 */
@RestController
@CrossOrigin(origins = "*")
@RequestMapping
public class TradeQueryController {

    private static final Logger log = LoggerFactory.getLogger(TradeQueryController.class);

    private static final int MAX_AUDIT_PAGE_SIZE = 1000;

    private final TradeCouchDbRepository tradeRepository;
    private final AuditCouchDbRepository auditRepository;
    private final IngestionMetricsService metricsService;

    public TradeQueryController(TradeCouchDbRepository tradeRepository,
                                AuditCouchDbRepository auditRepository,
                                IngestionMetricsService metricsService) {
        this.tradeRepository = tradeRepository;
        this.auditRepository = auditRepository;
        this.metricsService = metricsService;
    }

    /**
     * Retrieves trade detail and complete audit history by trade ID.
     *
     * @param id trade identifier (e.g. TRD-10001)
     * @return 200 with TradeDetailResponse or 404 if not found
     */
    @GetMapping("/trades/{id}")
    public ResponseEntity<?> getTradeById(@PathVariable("id") String id) {
        log.debug("GET /trades/{} requested", id);
        Optional<TradeDocument> tradeOpt = tradeRepository.findById(id);
        if (tradeOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "error", "Trade not found",
                            "tradeId", id,
                            "status", HttpStatus.NOT_FOUND.value()
                    ));
        }

        List<AuditDocument> auditTrail;
        try {
            auditTrail = auditRepository.findByTradeId(id);
        } catch (Exception ex) {
            log.warn("Failed to fetch audit events for trade '{}': {}", id, ex.getMessage());
            auditTrail = Collections.emptyList();
        }

        return ResponseEntity.ok(new TradeDetailResponse(tradeOpt.get(), auditTrail));
    }

    /**
     * Paginated list of trades with optional status filtering.
     *
     * @param status optional status filter (e.g. ACCEPTED, SCHEMA_INVALID, RECEIVED)
     * @param page   page index (0-based, default: 0)
     * @param size   page size (default: 50)
     * @return paginated list of trade documents
     */
    @GetMapping("/trades")
    public ResponseEntity<TradeListResponse> listTrades(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        int skip = safePage * safeSize;

        ProcessingStatus parsedStatus = null;
        if (status != null && !status.trim().isEmpty()) {
            try {
                parsedStatus = ProcessingStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                log.debug("Unknown processing status filter: '{}', ignoring filter", status);
            }
        }

        List<TradeDocument> trades;
        if (parsedStatus != null) {
            trades = tradeRepository.findByStatus(parsedStatus, safeSize, skip);
        } else {
            trades = tradeRepository.findAll(safeSize, skip);
        }

        // The raw message is only needed on the detail view; it would double the size of every poll.
        trades.forEach(trade -> trade.setRawPayload(null));
        return ResponseEntity.ok(new TradeListResponse(trades, safePage, safeSize, status));
    }

    /**
     * Retrieves the audit trail for a specific trade.
     *
     * @param id trade identifier
     * @return list of audit records
     */
    @GetMapping("/trades/{id}/audit")
    public ResponseEntity<List<AuditDocument>> getTradeAuditTrail(@PathVariable("id") String id) {
        List<AuditDocument> auditTrail = auditRepository.findByTradeId(id);
        return ResponseEntity.ok(auditTrail);
    }

    /**
     * Returns ingestion throughput and validation breakdown summary.
     */
    /**
     * Returns the most recent audit events across all trades and services, newest first (FR11).
     */
    @GetMapping("/audit")
    public ResponseEntity<Map<String, Object>> listAudit(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "100") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_AUDIT_PAGE_SIZE);
        List<AuditDocument> events = auditRepository.findRecent(safeSize, safePage * safeSize);
        return ResponseEntity.ok(Map.of("content", events, "page", safePage, "size", safeSize, "count", events.size()));
    }

    @GetMapping("/trades/metrics/summary")
    public ResponseEntity<IngestionMetricsSummary> getMetricsSummary() {
        return ResponseEntity.ok(metricsService.getSummary());
    }

    /**
     * Returns STP rate metrics for the React Operations UI (FR11).
     */
    @GetMapping({"/metrics/stp-rate", "/trades/metrics/stp-rate"})
    public ResponseEntity<Map<String, Object>> getStpRate() {
        return ResponseEntity.ok(metricsService.getStpRate());
    }
}
