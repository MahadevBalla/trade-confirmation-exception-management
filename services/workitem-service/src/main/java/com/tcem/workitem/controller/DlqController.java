package com.tcem.workitem.controller;

import com.tcem.workitem.model.document.DlqEntryDocument;
import com.tcem.workitem.repository.DlqEntryRepository;
import com.tcem.workitem.service.ReplayException;
import com.tcem.workitem.service.ReplayService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST API for the dead-letter queue and controlled replay (FR11, FR12).
 */
@RestController
@CrossOrigin(origins = "*")
public class DlqController {

    private static final int MAX_PAGE_SIZE = 100;

    private final DlqEntryRepository dlqRepository;
    private final ReplayService replayService;

    public DlqController(DlqEntryRepository dlqRepository, ReplayService replayService) {
        this.dlqRepository = dlqRepository;
        this.replayService = replayService;
    }

    @GetMapping("/dlq")
    public Map<String, Object> list(@RequestParam(required = false) String status,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        List<DlqEntryDocument> entries = dlqRepository.findPage(status, safePage, safeSize);
        return Map.of("content", entries, "page", safePage, "size", safeSize, "count", entries.size());
    }

    @GetMapping("/dlq/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        return dlqRepository.findEntry(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> error(HttpStatus.NOT_FOUND, "DLQ entry '" + id + "' not found"));
    }

    @PostMapping("/dlq/{id}/replay")
    public ResponseEntity<?> replay(@PathVariable String id) {
        try {
            return ResponseEntity.ok(replayService.replay(id));
        } catch (ReplayException ex) {
            HttpStatus status = switch (ex.getReason()) {
                case NOT_FOUND -> HttpStatus.NOT_FOUND;
                case PUBLISH_FAILED -> HttpStatus.BAD_GATEWAY;
                default -> HttpStatus.CONFLICT;
            };
            return error(status, ex.getMessage());
        }
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "error", message));
    }
}
