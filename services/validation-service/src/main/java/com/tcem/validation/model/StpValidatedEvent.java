package com.tcem.validation.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tcem.common.model.message.MessageHeader;
import com.tcem.validation.rules.StpValidationResult;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Kafka event envelope for the {@code stp.validated} topic (FR5).
 * <p>
 * Carries the STP decision and a summary of any rule failures, keyed by
 * {@code tradeId}. Downstream services (exception-service, workitem-service)
 * consume this topic to learn about STP outcomes.
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StpValidatedEvent {

    private MessageHeader messageHeader;
    private String tradeId;
    private int tradeVersion;
    private boolean stpPassed;

    /** True when this event reports a CANCEL_TRADE, which is not evaluated by STP. */
    private boolean cancelled;

    /**
     * Human-readable summary of all rule failures.
     * Null when STP passed.
     */
    private String failureSummary;

    /**
     * Structured failure list for downstream classification.
     * Empty/null when STP passed.
     */
    private List<StpFailureDetail> failures;

    public StpValidatedEvent() {}

    /**
     * Convenience factory from an STP result.
     */
    public static StpValidatedEvent of(MessageHeader header, String tradeId, int tradeVersion,
                                       StpValidationResult result) {
        StpValidatedEvent event = new StpValidatedEvent();
        event.messageHeader = header;
        event.tradeId = tradeId;
        event.tradeVersion = tradeVersion;
        event.stpPassed = result.isStpPassed();

        if (!result.isStpPassed()) {
            event.failureSummary = result.summarize();
            event.failures = result.getFailures().stream()
                    .map(f -> new StpFailureDetail(
                            f.getCategory().name(),
                            f.getSubTheme().name(),
                            f.getReasonCode(),
                            f.getAffectedFields()))
                    .collect(Collectors.toList());
        }
        return event;
    }

    /** Outcome for a CANCEL_TRADE: no STP decision was made. */
    public static StpValidatedEvent cancelled(MessageHeader header, String tradeId, int tradeVersion) {
        StpValidatedEvent event = new StpValidatedEvent();
        event.messageHeader = header;
        event.tradeId = tradeId;
        event.tradeVersion = tradeVersion;
        event.cancelled = true;
        return event;
    }

    // -----------------------------------------------------------------------
    // Nested DTO
    // -----------------------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StpFailureDetail {
        private String category;
        private String subTheme;
        private String reasonCode;
        private String affectedFields;

        public StpFailureDetail() {}
        public StpFailureDetail(String category, String subTheme, String reasonCode, String affectedFields) {
            this.category = category;
            this.subTheme = subTheme;
            this.reasonCode = reasonCode;
            this.affectedFields = affectedFields;
        }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getSubTheme() { return subTheme; }
        public void setSubTheme(String subTheme) { this.subTheme = subTheme; }
        public String getReasonCode() { return reasonCode; }
        public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
        public String getAffectedFields() { return affectedFields; }
        public void setAffectedFields(String affectedFields) { this.affectedFields = affectedFields; }
    }

    // -----------------------------------------------------------------------
    // Getters / setters
    // -----------------------------------------------------------------------

    public MessageHeader getMessageHeader() { return messageHeader; }
    public void setMessageHeader(MessageHeader messageHeader) { this.messageHeader = messageHeader; }

    public String getTradeId() { return tradeId; }
    public void setTradeId(String tradeId) { this.tradeId = tradeId; }

    public int getTradeVersion() { return tradeVersion; }
    public void setTradeVersion(int tradeVersion) { this.tradeVersion = tradeVersion; }

    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public boolean isStpPassed() { return stpPassed; }
    public void setStpPassed(boolean stpPassed) { this.stpPassed = stpPassed; }

    public String getFailureSummary() { return failureSummary; }
    public void setFailureSummary(String failureSummary) { this.failureSummary = failureSummary; }

    public List<StpFailureDetail> getFailures() { return failures; }
    public void setFailures(List<StpFailureDetail> failures) { this.failures = failures; }
}

