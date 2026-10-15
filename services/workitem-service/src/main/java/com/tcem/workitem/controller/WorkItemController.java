package com.tcem.workitem.controller;

import com.tcem.workitem.model.document.WorkItemDocument;
import com.tcem.workitem.repository.WorkItemRepository;
import com.tcem.workitem.service.ManualReviewService;
import com.tcem.workitem.service.WorkItemException;
import com.tcem.workitem.service.WorkItemService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API over the mock work-item tracker and manual review queue (FR10, FR11).
 */
@RestController
@CrossOrigin(origins = "*")
public class WorkItemController {

    private static final int MAX_PAGE_SIZE = 100;

    private final WorkItemRepository workItemRepository;
    private final WorkItemService workItemService;
    private final ManualReviewService manualReviewService;

    public WorkItemController(WorkItemRepository workItemRepository,
                              WorkItemService workItemService,
                              ManualReviewService manualReviewService) {
        this.workItemRepository = workItemRepository;
        this.workItemService = workItemService;
        this.manualReviewService = manualReviewService;
    }

    /** Request body for {@code POST /workitems}. */
    public record CreateWorkItemRequest(String tradeId, String type, String title, String description,
                                        String ownerQueue, String priority, String linkedExceptionId) {}

    /** Request body for {@code PATCH /workitems/{id}}; null fields are left unchanged. */
    public record UpdateWorkItemRequest(String status, String priority, String ownerQueue, String notes, String actor) {}

    /** Request body for {@code POST /workitems/{id}/review}. */
    public record ReviewRequest(String decision, String notes, String reviewer) {}

    @GetMapping("/workitems")
    public Map<String, Object> list(@RequestParam(required = false) String status,
                                    @RequestParam(required = false) String type,
                                    @RequestParam(required = false) String ownerQueue,
                                    @RequestParam(required = false) String priority,
                                    @RequestParam(required = false) String tradeId,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Map<String, String> filters = new HashMap<>();
        filters.put("status", status);
        filters.put("type", type);
        filters.put("ownerQueue", ownerQueue);
        filters.put("priority", priority);
        filters.put("tradeId", tradeId);
        List<WorkItemDocument> items = workItemRepository.findPage(filters, safePage, safeSize);
        return Map.of("content", items, "page", safePage, "size", safeSize, "count", items.size());
    }

    @GetMapping("/workitems/summary")
    public Map<String, Object> summary() {
        return workItemService.summary();
    }

    @GetMapping("/workitems/{id}")
    public WorkItemDocument get(@PathVariable String id) {
        return workItemService.get(id);
    }

    @PostMapping("/workitems")
    public ResponseEntity<WorkItemDocument> create(@RequestBody CreateWorkItemRequest request) {
        WorkItemDocument created = workItemService.createManual(request.tradeId(), request.type(), request.title(),
                request.description(), request.ownerQueue(), request.priority(), request.linkedExceptionId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/workitems/{id}")
    public WorkItemDocument update(@PathVariable String id, @RequestBody UpdateWorkItemRequest request) {
        return workItemService.update(id, request.status(), request.priority(), request.ownerQueue(),
                request.notes(), request.actor());
    }

    @PostMapping("/workitems/{id}/review")
    public WorkItemDocument review(@PathVariable String id, @RequestBody ReviewRequest request) {
        return manualReviewService.decide(id, request.decision(), request.notes(), request.reviewer());
    }

    @ExceptionHandler(WorkItemException.class)
    public ResponseEntity<Map<String, Object>> handleWorkItemException(WorkItemException ex) {
        HttpStatus status = switch (ex.getReason()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "error", ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(Map.of("status", 400, "error", "Request body is missing or not valid JSON"));
    }
}
