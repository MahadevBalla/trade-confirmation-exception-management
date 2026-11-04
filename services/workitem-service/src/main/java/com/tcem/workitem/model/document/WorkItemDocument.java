package com.tcem.workitem.model.document;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tcem.common.model.workitem.WorkItem;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * CouchDB document in 'work-items' backing the mock work-item tracker (FR10).
 * Mirrors {@link WorkItem} and adds CouchDB metadata.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkItemDocument {

    @JsonProperty("_id")
    private String id;

    @JsonProperty("_rev")
    private String rev;

    private String docType = "WORK_ITEM";
    private String workItemId;
    private String linkedExceptionId;
    private String linkedDlqEntryId;
    private String tradeId;
    private String type;
    private String title;
    private String description;
    private String status = "OPEN";
    private String ownerQueue;
    private String priority;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant resolvedAt;

    /** Every exception linked to this item; {@code linkedExceptionId} holds the first one. */
    private List<String> linkedExceptionIds = new ArrayList<>();
    private String category;
    private String subTheme;
    private String reviewDecision;
    private String reviewNotes;
    private String reviewedBy;
    private String resolutionNotes;

    public WorkItem toWorkItem() {
        return WorkItem.builder()
                .workItemId(workItemId)
                .linkedExceptionId(linkedExceptionId)
                .tradeId(tradeId)
                .type(type)
                .title(title)
                .description(description)
                .status(status)
                .ownerQueue(ownerQueue)
                .priority(priority)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .resolvedAt(resolvedAt)
                .reviewDecision(reviewDecision)
                .build();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRev() { return rev; }
    public void setRev(String rev) { this.rev = rev; }

    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }

    public String getWorkItemId() { return workItemId; }
    public void setWorkItemId(String workItemId) { this.workItemId = workItemId; }

    public String getLinkedExceptionId() { return linkedExceptionId; }
    public void setLinkedExceptionId(String linkedExceptionId) { this.linkedExceptionId = linkedExceptionId; }

    public String getLinkedDlqEntryId() { return linkedDlqEntryId; }
    public void setLinkedDlqEntryId(String linkedDlqEntryId) { this.linkedDlqEntryId = linkedDlqEntryId; }

    public String getTradeId() { return tradeId; }
    public void setTradeId(String tradeId) { this.tradeId = tradeId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOwnerQueue() { return ownerQueue; }
    public void setOwnerQueue(String ownerQueue) { this.ownerQueue = ownerQueue; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    public List<String> getLinkedExceptionIds() { return linkedExceptionIds; }
    public void setLinkedExceptionIds(List<String> linkedExceptionIds) {
        this.linkedExceptionIds = linkedExceptionIds != null ? linkedExceptionIds : new ArrayList<>();
    }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSubTheme() { return subTheme; }
    public void setSubTheme(String subTheme) { this.subTheme = subTheme; }

    public String getReviewDecision() { return reviewDecision; }
    public void setReviewDecision(String reviewDecision) { this.reviewDecision = reviewDecision; }

    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }

    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }

    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }

    @JsonIgnore
    public boolean isOpen() {
        return "OPEN".equals(status) || "IN_PROGRESS".equals(status);
    }
}
