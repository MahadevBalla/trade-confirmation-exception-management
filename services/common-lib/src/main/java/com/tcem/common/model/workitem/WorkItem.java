package com.tcem.common.model.workitem;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Mock work-item entity (FR10).
 * <p>
 * Represents a generic defect/task in the mock issue-tracker service.
 * In production this would be a Jira/Redmine ticket; here it is a
 * CouchDB document in the {@code workitems} database.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkItem {

    /** Platform-generated unique ID. Example: {@code WI-40001} */
    private String workItemId;

    /** Exception that triggered this work item. Null for non-exception work items. */
    private String linkedExceptionId;

    /** Trade associated with this work item. */
    private String tradeId;

    /**
     * Work-item type.
     * Values: {@code DATA_FIX}, {@code TEMPLATE_FIX}, {@code RULE_UPDATE},
     * {@code MANUAL_REVIEW}, {@code ENHANCEMENT}
     */
    private String type;

    /** Short title. Example: {@code "Missing strike price for TRD-10001"} */
    private String title;

    /** Detailed description including context and suggested fix. */
    private String description;

    /** Current status. Values: {@code OPEN}, {@code IN_PROGRESS}, {@code DONE}, {@code CANCELLED} */
    @Builder.Default
    private String status = "OPEN";

    /**
     * Assigned owner queue or team.
     * Example: {@code DATA_REMEDIATION}, {@code TEMPLATE_MAINTENANCE}
     */
    private String ownerQueue;

    /** Priority. Values: {@code LOW}, {@code MEDIUM}, {@code HIGH}, {@code CRITICAL} */
    private String priority;

    /** UTC timestamp when created. */
    private Instant createdAt;

    /** UTC timestamp when last updated. */
    private Instant updatedAt;

    /** UTC timestamp of resolution. Null while open. */
    private Instant resolvedAt;
}
