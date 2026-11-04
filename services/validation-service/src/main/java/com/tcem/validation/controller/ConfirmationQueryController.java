package com.tcem.validation.controller;

import com.tcem.validation.repository.ConfirmationCouchDbRepository;
import com.tcem.validation.repository.ConfirmationDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST API for querying confirmation records and STP metrics (FR8, FR9).
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code GET /confirmations/{id}} — fetch a single confirmation by its
 *       {@code confirmationId}.</li>
 *   <li>{@code GET /trades/{tradeId}/confirmations} — fetch all confirmation
 *       versions for a trade, ordered newest-first.</li>
 *   <li>{@code GET /trades/{tradeId}/confirmation} — fetch the latest (highest
 *       version) confirmation for a trade.</li>
 *   <li>{@code GET /metrics/stp-rate} — simple STP throughput snapshot: total
 *       confirmations persisted.</li>
 * </ul>
 *
 * <h3>Error handling</h3>
 * Not-found conditions return {@code 404 Not Found} with no body.
 * The controller does not catch repository exceptions — Spring Boot's default
 * error handler propagates them as {@code 500 Internal Server Error}.
 */
@RestController
@RequestMapping
public class ConfirmationQueryController {

    private static final Logger log = LoggerFactory.getLogger(ConfirmationQueryController.class);

    private final ConfirmationCouchDbRepository confirmationRepository;

    public ConfirmationQueryController(ConfirmationCouchDbRepository confirmationRepository) {
        this.confirmationRepository = confirmationRepository;
    }

    /**
     * Retrieves a page of confirmations across all trades.
     */
    @GetMapping("/confirmations")
    public ResponseEntity<Map<String, Object>> listConfirmations(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "100") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 1000);
        List<ConfirmationDocument> content = confirmationRepository.findPage(safeSize, safePage * safeSize);
        return ResponseEntity.ok(Map.of("content", content, "page", safePage, "size", safeSize, "count", content.size()));
    }

    /**
     * Retrieves a single confirmation document by its {@code confirmationId}.
     *
     * @param id the {@code confirmationId} (also the CouchDB {@code _id})
     * @return {@code 200 OK} with the {@link ConfirmationDocument}, or {@code 404 Not Found}
     */
    @GetMapping("/confirmations/{id}")
    public ResponseEntity<ConfirmationDocument> getConfirmationById(@PathVariable String id) {
        log.debug("GET /confirmations/{}", id);
        Optional<ConfirmationDocument> doc = confirmationRepository.findById(id);
        return doc.map(ResponseEntity::ok)
                  .orElseGet(() -> {
                      log.info("Confirmation not found [id={}]", id);
                      return ResponseEntity.notFound().build();
                  });
    }

    /**
     * Retrieves all confirmation versions for a trade, ordered by {@code tradeVersion}
     * descending (newest first).
     *
     * @param tradeId the trade identifier
     * @return {@code 200 OK} with the list (may be empty)
     */
    @GetMapping("/trades/{tradeId}/confirmations")
    public ResponseEntity<List<ConfirmationDocument>> getConfirmationsByTradeId(
            @PathVariable String tradeId) {
        log.debug("GET /trades/{}/confirmations", tradeId);
        List<ConfirmationDocument> docs = confirmationRepository.findByTradeId(tradeId);
        return ResponseEntity.ok(docs);
    }

    /**
     * Retrieves the most recent confirmation (highest {@code tradeVersion}) for a trade.
     *
     * @param tradeId the trade identifier
     * @return {@code 200 OK} with the {@link ConfirmationDocument}, or {@code 404 Not Found}
     */
    @GetMapping("/trades/{tradeId}/confirmation")
    public ResponseEntity<ConfirmationDocument> getLatestConfirmationForTrade(
            @PathVariable String tradeId) {
        log.debug("GET /trades/{}/confirmation", tradeId);
        Optional<ConfirmationDocument> doc = confirmationRepository.findLatestForTrade(tradeId);
        return doc.map(ResponseEntity::ok)
                  .orElseGet(() -> {
                      log.info("No confirmation found for tradeId={}", tradeId);
                      return ResponseEntity.notFound().build();
                  });
    }

    /**
     * Returns a simple STP metrics snapshot: the total count of confirmation documents
     * persisted in CouchDB.
     *
     * <p>The response is a JSON object with the following shape:
     * <pre>{@code
     * {
     *   "totalConfirmations": 42
     * }
     * }</pre>
     *
     * @return {@code 200 OK} with a metrics map
     */
    @GetMapping("/metrics/stp-rate")
    public ResponseEntity<Map<String, Object>> getStpRate() {
        log.debug("GET /metrics/stp-rate");
        long total = confirmationRepository.countAll();
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalConfirmations", total);
        log.debug("STP metrics: totalConfirmations={}", total);
        return ResponseEntity.ok(metrics);
    }
}

