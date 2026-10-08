package com.tcem.common.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tcem.common.model.message.MessageHeader;
import com.tcem.common.model.workitem.WorkItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kafka event envelope for {@code workitem.linked} topic (FR10).
 * <p>
 * Published by the workitem-service when a work item is created or linked
 * to an exception. Consumed by the UI (FR11) and the audit trail.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkItemEvent {

    /** Standard envelope header. */
    private MessageHeader messageHeader;

    /** The work item that was created or updated. */
    private WorkItem workItem;
}
