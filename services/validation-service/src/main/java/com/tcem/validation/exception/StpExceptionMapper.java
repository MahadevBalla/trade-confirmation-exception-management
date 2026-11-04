package com.tcem.validation.exception;

import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.event.ExceptionEvent;
import com.tcem.common.model.exception.ExceptionRecord;
import com.tcem.common.model.message.MessageHeader;
import com.tcem.common.model.message.SchemaVersions;
import com.tcem.common.model.trade.Trade;
import com.tcem.validation.config.ValidationProperties;
import com.tcem.validation.rules.StpValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Maps the output of the {@link com.tcem.validation.rules.StpRulesEngine} to one
 * {@link ExceptionEvent} per {@link StpValidationResult.Failure} (FR7 — Exception Routing Mapper).
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Transform each {@link StpValidationResult.Failure} into a structured
 *       {@link ExceptionRecord} with an assigned owner queue and recommended action.</li>
 *   <li>Populate all traceability fields: {@code tradeId}, {@code tradeVersion},
 *       {@code sourceMessageId}, {@code correlationId}.</li>
 *   <li>Route exceptions to the correct operational queue based on their
 *       {@link ExceptionCategory}.</li>
 * </ul>
 *
 * <h3>Queue routing</h3>
 * <table>
 *   <tr><td>{@code MISSING_ECONOMIC_DATA}</td>     <td>{@code DATA_REMEDIATION}</td></tr>
 *   <tr><td>{@code MISSING_STATIC_REFERENCE_DATA}</td><td>{@code DATA_REMEDIATION}</td></tr>
 *   <tr><td>{@code TEMPLATE_ISSUE}</td>             <td>{@code OPERATIONS_REVIEW}</td></tr>
 *   <tr><td>{@code BUSINESS_RULE_FAILURE}</td>      <td>{@code OPERATIONS_REVIEW}</td></tr>
 *   <tr><td>{@code TECHNICAL_PROCESSING_FAILURE}</td><td>{@code TECHNICAL_SUPPORT}</td></tr>
 *   <tr><td>all others</td>                          <td>{@code OPERATIONS_REVIEW}</td></tr>
 * </table>
 *
 * <h3>Thread safety</h3>
 * Stateless Spring {@code @Service}; safe to share across threads.
 */
@Service
public class StpExceptionMapper {

    private static final Logger log = LoggerFactory.getLogger(StpExceptionMapper.class);

    private static final String QUEUE_DATA_REMEDIATION   = "DATA_REMEDIATION";
    private static final String QUEUE_OPERATIONS_REVIEW  = "OPERATIONS_REVIEW";
    private static final String QUEUE_TECHNICAL_SUPPORT  = "TECHNICAL_SUPPORT";

    private final ValidationProperties properties;

    public StpExceptionMapper(ValidationProperties properties) {
        this.properties = properties;
    }

    /**
     * Converts each STP rule failure into a distinct {@link ExceptionEvent}.
     *
     * @param header incoming message header (carries traceability IDs)
     * @param trade  the trade that failed STP validation
     * @param result the STP validation result containing one or more failures
     * @return a list of {@link ExceptionEvent} instances; one per failure; never empty
     *         (callers should only invoke this when {@code !result.isStpPassed()})
     */
    public List<ExceptionEvent> mapToExceptionEvents(MessageHeader header,
                                                     Trade trade,
                                                     StpValidationResult result) {
        Instant now = Instant.now();
        String generatedBy = properties.getServiceName();

        return result.getFailures().stream()
                .map(failure -> buildExceptionEvent(header, trade, failure, now, generatedBy))
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private ExceptionEvent buildExceptionEvent(MessageHeader inboundHeader,
                                               Trade trade,
                                               StpValidationResult.Failure failure,
                                               Instant now,
                                               String generatedBy) {
        String exceptionId = "EXC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();

        ExceptionRecord record = new ExceptionRecord();
        record.setExceptionId(exceptionId);
        record.setTradeId(trade.getTradeId());
        record.setTradeVersion(trade.getTradeVersion());
        record.setSourceMessageId(inboundHeader.getMessageId());
        record.setCorrelationId(inboundHeader.getCorrelationId());
        record.setCategory(failure.getCategory());
        record.setSubTheme(failure.getSubTheme());
        record.setReasonCode(failure.getReasonCode());
        record.setAffectedFields(failure.getAffectedFields());
        record.setOwnerQueue(resolveOwnerQueue(failure.getCategory()));
        record.setRecommendedAction(resolveRecommendedAction(failure.getCategory()));
        record.setReplayMayResolve(isReplayCandidate(failure.getCategory()));
        record.setResolutionDifficulty(resolveResolutionDifficulty(failure.getCategory()));
        record.setOperationalImpact(resolveOperationalImpact(failure.getCategory()));
        record.setCapturedAt(now);
        record.setClassificationConfidence(1.0);

        MessageHeader outboundHeader = new MessageHeader();
        outboundHeader.setMessageId("MSG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        outboundHeader.setCorrelationId(inboundHeader.getCorrelationId());
        outboundHeader.setEventType(inboundHeader.getEventType());
        outboundHeader.setReplayCount(inboundHeader.getReplayCount());
        outboundHeader.setSchemaVersion(SchemaVersions.CURRENT);
        outboundHeader.setCreatedTimestamp(now);
        outboundHeader.setSourceService(generatedBy);

        ExceptionEvent event = new ExceptionEvent(outboundHeader, record);

        log.debug("Mapped STP failure to ExceptionRecord [exceptionId={}, tradeId={}, category={}, queue={}]",
                exceptionId, trade.getTradeId(), failure.getCategory(), record.getOwnerQueue());

        return event;
    }

    /**
     * Determines the owner queue based on exception category.
     */
    private String resolveOwnerQueue(ExceptionCategory category) {
        if (category == null) {
            return QUEUE_OPERATIONS_REVIEW;
        }
        return switch (category) {
            case MISSING_ECONOMIC_DATA, MISSING_STATIC_REFERENCE_DATA -> QUEUE_DATA_REMEDIATION;
            case TECHNICAL_PROCESSING_FAILURE                          -> QUEUE_TECHNICAL_SUPPORT;
            default                                                    -> QUEUE_OPERATIONS_REVIEW;
        };
    }

    /**
     * Suggests the recommended remediation action based on category.
     */
    private String resolveRecommendedAction(ExceptionCategory category) {
        if (category == null) {
            return "Review exception details and route to appropriate team";
        }
        return switch (category) {
            case MISSING_ECONOMIC_DATA ->
                    "Locate and supply missing economic field in source system; replay after update";
            case MISSING_STATIC_REFERENCE_DATA ->
                    "Update reference data catalogue with missing entity; replay after update";
            case TEMPLATE_ISSUE ->
                    "Verify confirmation template availability and version alignment; contact operations";
            case BUSINESS_RULE_FAILURE ->
                    "Review counterparty and product STP eligibility configuration; escalate if needed";
            case TECHNICAL_PROCESSING_FAILURE ->
                    "Check infrastructure health, logs, and DLQ; replay after resolution";
            default ->
                    "Review exception details and escalate to operations team";
        };
    }

    /**
     * Returns whether replaying the message after a data fix is expected to resolve this exception.
     */
    private boolean isReplayCandidate(ExceptionCategory category) {
        if (category == null) return false;
        return switch (category) {
            case MISSING_ECONOMIC_DATA, MISSING_STATIC_REFERENCE_DATA, TECHNICAL_PROCESSING_FAILURE -> true;
            default -> false;
        };
    }

    /**
     * Estimates resolution difficulty based on category.
     */
    private String resolveResolutionDifficulty(ExceptionCategory category) {
        if (category == null) return "MEDIUM";
        return switch (category) {
            case MISSING_ECONOMIC_DATA, TECHNICAL_PROCESSING_FAILURE -> "LOW";
            case MISSING_STATIC_REFERENCE_DATA                       -> "MEDIUM";
            default                                                  -> "HIGH";
        };
    }

    /**
     * Estimates operational impact based on category.
     */
    private String resolveOperationalImpact(ExceptionCategory category) {
        if (category == null) return "MEDIUM";
        return switch (category) {
            case BUSINESS_RULE_FAILURE, TEMPLATE_ISSUE   -> "HIGH";
            case TECHNICAL_PROCESSING_FAILURE             -> "MEDIUM";
            default                                       -> "MEDIUM";
        };
    }
}

