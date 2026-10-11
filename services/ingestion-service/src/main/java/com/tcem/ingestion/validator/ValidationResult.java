package com.tcem.ingestion.validator;

import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import com.tcem.common.model.exception.ExceptionRecord;
import com.tcem.common.model.message.TradeEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Result of trade schema and business rule validation.
 */
public class ValidationResult {

    private final List<ValidationError> errors = new ArrayList<>();

    public static ValidationResult valid() {
        return new ValidationResult();
    }

    public static ValidationResult invalid(ValidationError error) {
        ValidationResult result = new ValidationResult();
        result.addError(error);
        return result;
    }

    public void addError(ValidationError error) {
        if (error != null) {
            errors.add(error);
        }
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public List<ValidationError> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public ValidationError firstError() {
        return errors.isEmpty() ? null : errors.get(0);
    }

    public String getErrorSummary() {
        return errors.stream()
                .map(err -> String.format("[%s: %s]", err.getField(), err.getMessage()))
                .collect(Collectors.joining("; "));
    }

    /**
     * Converts validation errors into platform-standard ExceptionRecords.
     *
     * @param event trade event that failed validation (may be null if payload was unparseable)
     * @return list of ExceptionRecord objects
     */
    public List<ExceptionRecord> toExceptionRecords(TradeEvent event) {
        String tradeId = (event != null && event.getTrade() != null && event.getTrade().getTradeId() != null)
                ? event.getTrade().getTradeId()
                : "TRD-UNKNOWN";
        int tradeVersion = (event != null && event.getTrade() != null)
                ? event.getTrade().getTradeVersion()
                : 1;
        String messageId = (event != null && event.getMessageHeader() != null)
                ? event.getMessageHeader().getMessageId()
                : null;
        String correlationId = (event != null && event.getMessageHeader() != null)
                ? event.getMessageHeader().getCorrelationId()
                : null;

        List<ExceptionRecord> records = new ArrayList<>();
        for (ValidationError err : errors) {
            ExceptionRecord record = new ExceptionRecord();
            record.setExceptionId("EXC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            record.setTradeId(tradeId);
            record.setTradeVersion(tradeVersion);
            record.setSourceMessageId(messageId);
            record.setCorrelationId(correlationId);
            record.setCategory(err.getCategory());
            record.setSubTheme(err.getSubTheme());
            record.setClassificationConfidence(1.0);
            record.setReasonCode(err.getReasonCode());
            record.setAffectedFields(err.getField());
            record.setStatus("OPEN");
            record.setCapturedAt(Instant.now());
            record.setReplayMayResolve(isReplayable(err.getCategory()));
            record.setRecommendedAction(resolveRecommendedAction(err));
            record.setOwnerQueue(resolveOwnerQueue(err.getCategory()));
            record.setResolutionDifficulty("MEDIUM");
            record.setOperationalImpact("HIGH");
            records.add(record);
        }
        return records;
    }

    private boolean isReplayable(ExceptionCategory category) {
        return category == ExceptionCategory.MISSING_ECONOMIC_DATA
                || category == ExceptionCategory.MISSING_STATIC_REFERENCE_DATA
                || category == ExceptionCategory.TEMPLATE_ISSUE;
    }

    private String resolveOwnerQueue(ExceptionCategory category) {
        if (category == null) {
            return "OPERATIONS_REVIEW";
        }
        return switch (category) {
            case MISSING_ECONOMIC_DATA, MISSING_STATIC_REFERENCE_DATA -> "DATA_REMEDIATION";
            case TEMPLATE_ISSUE -> "TEMPLATE_MANAGEMENT";
            case TECHNICAL_PROCESSING_FAILURE -> "SUPPORT_ENGINEERING";
            default -> "OPERATIONS_REVIEW";
        };
    }

    private String resolveRecommendedAction(ValidationError error) {
        if (error.getSubTheme() == null) {
            return "Review trade payload with front-office trade support.";
        }
        return switch (error.getSubTheme()) {
            case MISSING_STRIKE -> "Request amended trade with agreed option strike rate from executing desk.";
            case MISSING_NOTIONAL -> "Capture missing notional amount from trade ticket and replay.";
            case MISSING_PREMIUM -> "Confirm premium terms with trader and update trade record.";
            case MISSING_PREMIUM_CURRENCY -> "Verify agreed settlement currency for premium and update.";
            case MISSING_UNDERLIER -> "Confirm currency pair underlier and re-enrich.";
            case MISSING_OPTION_TYPE -> "Clarify CALL vs PUT option type from confirmation draft.";
            case UNKNOWN_COUNTERPARTY -> "Register counterparty LEI in Master Reference Data and replay.";
            case TRADE_DATE_MISMATCH -> "Align trade date, effective date, and maturity date sequence.";
            case SCHEMA_VERSION_UNSUPPORTED -> "Migrate payload to supported canonical schema version.";
            case PARSE_FAILURE -> "Inspect raw JSON format for syntax errors.";
            case NO_MATCHING_TEMPLATE, PRODUCT_TEMPLATE_MISMATCH -> "Assign appropriate ISDA confirmation template for product family.";
            default -> "Review trade parameters against master terms agreement.";
        };
    }
}
