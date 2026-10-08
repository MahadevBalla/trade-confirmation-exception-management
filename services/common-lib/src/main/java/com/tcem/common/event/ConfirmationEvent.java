package com.tcem.common.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tcem.common.model.confirmation.ConfirmationRecord;
import com.tcem.common.model.message.MessageHeader;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kafka event envelope for {@code confirmation.generated} topic.
 * <p>
 * Published by the validation-service (FR6) when STP succeeds and a new
 * confirmation is generated. The {@code messageHeader.correlationId} links
 * this back to the originating {@code TradeEvent}.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConfirmationEvent {

    /** Standard envelope header (correlationId, schemaVersion, eventType). */
    private MessageHeader messageHeader;

    /** The confirmation that was generated. */
    private ConfirmationRecord confirmation;
}
