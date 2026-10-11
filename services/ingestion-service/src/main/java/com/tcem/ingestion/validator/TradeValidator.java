package com.tcem.ingestion.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import com.tcem.common.constants.ProductType;
import com.tcem.common.model.message.MessageHeader;
import com.tcem.common.model.message.SchemaVersions;
import com.tcem.common.model.message.TradeEvent;
import com.tcem.common.model.trade.FxOptionEconomics;
import com.tcem.common.model.trade.NonEconomicTerms;
import com.tcem.common.model.trade.Party;
import com.tcem.common.model.trade.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

/**
 * Validates incoming trade events and raw trade payloads against canonical schema
 * and business rules (FR2).
 */
@Component
public class TradeValidator {

    private static final Logger log = LoggerFactory.getLogger(TradeValidator.class);

    private static final Set<String> SUPPORTED_SCHEMA_VERSIONS = Set.of(
            SchemaVersions.V1_0,
            SchemaVersions.V1_1
    );

    private static final Set<String> VALID_OPTION_TYPES = Set.of("CALL", "PUT");
    private static final Set<String> VALID_BUY_SELL = Set.of("BUY", "SELL");

    private final ObjectMapper objectMapper;

    public TradeValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Validates a parsed canonical TradeEvent.
     *
     * @param event trade event to validate
     * @return ValidationResult containing any detected validation errors
     */
    public ValidationResult validate(TradeEvent event) {
        ValidationResult result = new ValidationResult();

        if (event == null) {
            result.addError(new ValidationError(
                    "event",
                    "Trade event payload cannot be null",
                    ExceptionCategory.TECHNICAL_PROCESSING_FAILURE,
                    ExceptionSubTheme.PARSE_FAILURE,
                    "VAL_NULL_EVENT"
            ));
            return result;
        }

        validateHeader(event.getMessageHeader(), result);
        validateTrade(event.getTrade(), result);

        return result;
    }

    /**
     * Validates a raw JSON payload string, parsing it and checking structure and economics.
     *
     * @param rawJson raw JSON message string
     * @return ValidationResult with errors if parsing or schema fails
     */
    public ValidationResult validateRawJson(String rawJson) {
        ValidationResult result = new ValidationResult();

        if (rawJson == null || rawJson.trim().isEmpty()) {
            result.addError(new ValidationError(
                    "payload",
                    "Incoming JSON payload is empty or null",
                    ExceptionCategory.TECHNICAL_PROCESSING_FAILURE,
                    ExceptionSubTheme.PARSE_FAILURE,
                    "VAL_EMPTY_PAYLOAD"
            ));
            return result;
        }

        try {
            JsonNode root = objectMapper.readTree(rawJson);

            // Verify basic JSON tree contains envelope
            if (!root.hasNonNull("messageHeader")) {
                result.addError(new ValidationError(
                        "messageHeader",
                        "Missing mandatory 'messageHeader' envelope in payload",
                        ExceptionCategory.TECHNICAL_PROCESSING_FAILURE,
                        ExceptionSubTheme.PARSE_FAILURE,
                        "VAL_MISSING_HEADER"
                ));
            }

            if (!root.hasNonNull("trade")) {
                result.addError(new ValidationError(
                        "trade",
                        "Missing mandatory 'trade' object in payload",
                        ExceptionCategory.TECHNICAL_PROCESSING_FAILURE,
                        ExceptionSubTheme.PARSE_FAILURE,
                        "VAL_MISSING_TRADE"
                ));
                return result;
            }

            // Deserialize to canonical TradeEvent and run in-depth validations
            TradeEvent event = objectMapper.treeToValue(root, TradeEvent.class);
            return validate(event);

        } catch (Exception ex) {
            log.warn("Payload failed JSON structural parse: {}", ex.getMessage());
            result.addError(new ValidationError(
                    "payload",
                    "Malformed JSON payload syntax: " + ex.getMessage(),
                    ExceptionCategory.TECHNICAL_PROCESSING_FAILURE,
                    ExceptionSubTheme.PARSE_FAILURE,
                    "VAL_JSON_SYNTAX_ERROR"
            ));
            return result;
        }
    }

    // -----------------------------------------------------------------------
    // Header Validation
    // -----------------------------------------------------------------------

    private void validateHeader(MessageHeader header, ValidationResult result) {
        if (header == null) {
            result.addError(new ValidationError(
                    "messageHeader",
                    "Message header is required",
                    ExceptionCategory.TECHNICAL_PROCESSING_FAILURE,
                    ExceptionSubTheme.PARSE_FAILURE,
                    "VAL_HEADER_NULL"
            ));
            return;
        }

        if (isBlank(header.getMessageId())) {
            result.addError(new ValidationError(
                    "messageHeader.messageId",
                    "Message ID is mandatory",
                    ExceptionCategory.TECHNICAL_PROCESSING_FAILURE,
                    ExceptionSubTheme.PARSE_FAILURE,
                    "VAL_MISSING_MSG_ID"
            ));
        }

        if (isBlank(header.getCorrelationId())) {
            result.addError(new ValidationError(
                    "messageHeader.correlationId",
                    "Correlation ID is mandatory for distributed tracing",
                    ExceptionCategory.TECHNICAL_PROCESSING_FAILURE,
                    ExceptionSubTheme.PARSE_FAILURE,
                    "VAL_MISSING_CORR_ID"
            ));
        }

        if (header.getEventType() == null) {
            result.addError(new ValidationError(
                    "messageHeader.eventType",
                    "Event type is mandatory",
                    ExceptionCategory.TECHNICAL_PROCESSING_FAILURE,
                    ExceptionSubTheme.PARSE_FAILURE,
                    "VAL_MISSING_EVENT_TYPE"
            ));
        }

        if (header.getSchemaVersion() == null || !SUPPORTED_SCHEMA_VERSIONS.contains(header.getSchemaVersion())) {
            result.addError(new ValidationError(
                    "messageHeader.schemaVersion",
                    "Unsupported or missing schema version: '" + header.getSchemaVersion()
                            + "'. Supported: " + SUPPORTED_SCHEMA_VERSIONS,
                    ExceptionCategory.TECHNICAL_PROCESSING_FAILURE,
                    ExceptionSubTheme.SCHEMA_VERSION_UNSUPPORTED,
                    "VAL_UNSUPPORTED_SCHEMA_VER"
            ));
        }
    }

    // -----------------------------------------------------------------------
    // Trade Core Validation
    // -----------------------------------------------------------------------

    private void validateTrade(Trade trade, ValidationResult result) {
        if (trade == null) {
            result.addError(new ValidationError(
                    "trade",
                    "Trade body is mandatory",
                    ExceptionCategory.TECHNICAL_PROCESSING_FAILURE,
                    ExceptionSubTheme.PARSE_FAILURE,
                    "VAL_TRADE_NULL"
            ));
            return;
        }

        if (isBlank(trade.getTradeId())) {
            result.addError(new ValidationError(
                    "trade.tradeId",
                    "Trade ID is mandatory",
                    ExceptionCategory.TECHNICAL_PROCESSING_FAILURE,
                    ExceptionSubTheme.PARSE_FAILURE,
                    "VAL_MISSING_TRADE_ID"
            ));
        }

        if (isBlank(trade.getSourceSystemTradeId())) {
            result.addError(new ValidationError(
                    "trade.sourceSystemTradeId",
                    "Source system trade ID is mandatory",
                    ExceptionCategory.TECHNICAL_PROCESSING_FAILURE,
                    ExceptionSubTheme.PARSE_FAILURE,
                    "VAL_MISSING_SRC_TRADE_ID"
            ));
        }

        if (trade.getProductType() == null) {
            result.addError(new ValidationError(
                    "trade.productType",
                    "Product type is mandatory",
                    ExceptionCategory.BUSINESS_RULE_FAILURE,
                    ExceptionSubTheme.PRODUCT_NOT_STP_ELIGIBLE,
                    "VAL_MISSING_PRODUCT_TYPE"
            ));
        }

        validateDates(trade, result);
        validateParties(trade.getParties(), result);

        if (trade.getProductType() == ProductType.FX_OPTION) {
            validateFxOptionEconomics(trade.getFxOptionEconomics(), trade.getTradeDate(), result);
        }

        validateNonEconomicTerms(trade.getNonEconomicTerms(), result);
    }

    // -----------------------------------------------------------------------
    // Dates Validation
    // -----------------------------------------------------------------------

    private void validateDates(Trade trade, ValidationResult result) {
        LocalDate tradeDate = parseDate("trade.tradeDate", trade.getTradeDate(), result);
        LocalDate effectiveDate = parseDate("trade.effectiveDate", trade.getEffectiveDate(), result);
        LocalDate maturityDate = parseDate("trade.maturityDate", trade.getMaturityDate(), result);

        if (tradeDate != null && effectiveDate != null && effectiveDate.isBefore(tradeDate)) {
            result.addError(new ValidationError(
                    "trade.effectiveDate",
                    "Effective date (" + effectiveDate + ") cannot be before trade date (" + tradeDate + ")",
                    ExceptionCategory.BUSINESS_RULE_FAILURE,
                    ExceptionSubTheme.TRADE_DATE_MISMATCH,
                    "VAL_DATE_EFFECTIVE_BEFORE_TRADE"
            ));
        }

        if (effectiveDate != null && maturityDate != null && maturityDate.isBefore(effectiveDate)) {
            result.addError(new ValidationError(
                    "trade.maturityDate",
                    "Maturity date (" + maturityDate + ") cannot be before effective date (" + effectiveDate + ")",
                    ExceptionCategory.BUSINESS_RULE_FAILURE,
                    ExceptionSubTheme.TRADE_DATE_MISMATCH,
                    "VAL_DATE_MATURITY_BEFORE_EFFECTIVE"
            ));
        }
    }

    private LocalDate parseDate(String field, String dateStr, ValidationResult result) {
        if (isBlank(dateStr)) {
            result.addError(new ValidationError(
                    field,
                    "Date field '" + field + "' is mandatory",
                    ExceptionCategory.BUSINESS_RULE_FAILURE,
                    ExceptionSubTheme.TRADE_DATE_MISMATCH,
                    "VAL_MISSING_DATE"
            ));
            return null;
        }
        try {
            return LocalDate.parse(dateStr);
        } catch (DateTimeParseException ex) {
            result.addError(new ValidationError(
                    field,
                    "Invalid ISO-8601 date format for '" + field + "': " + dateStr + " (expected YYYY-MM-DD)",
                    ExceptionCategory.BUSINESS_RULE_FAILURE,
                    ExceptionSubTheme.TRADE_DATE_MISMATCH,
                    "VAL_INVALID_DATE_FORMAT"
            ));
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // Parties Validation
    // -----------------------------------------------------------------------

    private void validateParties(List<Party> parties, ValidationResult result) {
        if (parties == null || parties.isEmpty()) {
            result.addError(new ValidationError(
                    "trade.parties",
                    "Parties list is mandatory and must contain at least 2 parties",
                    ExceptionCategory.MISSING_STATIC_REFERENCE_DATA,
                    ExceptionSubTheme.UNKNOWN_COUNTERPARTY,
                    "VAL_PARTIES_EMPTY"
            ));
            return;
        }

        boolean hasBank = false;
        boolean hasCounterparty = false;

        for (Party party : parties) {
            if ("BANK".equalsIgnoreCase(party.getPartyRole())) {
                if (!isBlank(party.getPartyId())) {
                    hasBank = true;
                }
            } else if ("COUNTERPARTY".equalsIgnoreCase(party.getPartyRole())) {
                if (!isBlank(party.getPartyId()) && !party.getPartyId().toUpperCase().contains("UNKNOWN")) {
                    hasCounterparty = true;
                } else {
                    result.addError(new ValidationError(
                            "trade.parties[COUNTERPARTY].partyId",
                            "Counterparty identifier is missing or unknown: '" + party.getPartyId() + "'",
                            ExceptionCategory.MISSING_STATIC_REFERENCE_DATA,
                            ExceptionSubTheme.UNKNOWN_COUNTERPARTY,
                            "VAL_UNKNOWN_COUNTERPARTY"
                    ));
                }
            }
        }

        if (!hasBank) {
            result.addError(new ValidationError(
                    "trade.parties[BANK]",
                    "Trade must contain an executing BANK party with valid partyId",
                    ExceptionCategory.MISSING_STATIC_REFERENCE_DATA,
                    ExceptionSubTheme.UNKNOWN_COUNTERPARTY,
                    "VAL_MISSING_BANK_PARTY"
            ));
        }

        if (!hasCounterparty) {
            result.addError(new ValidationError(
                    "trade.parties[COUNTERPARTY]",
                    "Trade must contain a COUNTERPARTY party with valid partyId",
                    ExceptionCategory.MISSING_STATIC_REFERENCE_DATA,
                    ExceptionSubTheme.UNKNOWN_COUNTERPARTY,
                    "VAL_MISSING_CP_PARTY"
            ));
        }
    }

    // -----------------------------------------------------------------------
    // FX Option Economics Validation
    // -----------------------------------------------------------------------

    private void validateFxOptionEconomics(FxOptionEconomics economics, String tradeDateStr, ValidationResult result) {
        if (economics == null) {
            result.addError(new ValidationError(
                    "trade.fxOptionEconomics",
                    "FX Option economics block is mandatory for FX_OPTION trades",
                    ExceptionCategory.MISSING_ECONOMIC_DATA,
                    ExceptionSubTheme.MISSING_NOTIONAL,
                    "VAL_MISSING_FX_ECONOMICS"
            ));
            return;
        }

        // 1. Strike Rate
        if (economics.getStrike() == null || economics.getStrike().compareTo(BigDecimal.ZERO) <= 0) {
            result.addError(new ValidationError(
                    "trade.fxOptionEconomics.strike",
                    "Strike rate is mandatory and must be strictly positive",
                    ExceptionCategory.MISSING_ECONOMIC_DATA,
                    ExceptionSubTheme.MISSING_STRIKE,
                    "VAL_INVALID_STRIKE"
            ));
        }

        // 2. Notional Amount
        if (economics.getNotionalAmount() == null || economics.getNotionalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            result.addError(new ValidationError(
                    "trade.fxOptionEconomics.notionalAmount",
                    "Notional amount is mandatory and must be strictly positive",
                    ExceptionCategory.MISSING_ECONOMIC_DATA,
                    ExceptionSubTheme.MISSING_NOTIONAL,
                    "VAL_INVALID_NOTIONAL"
            ));
        }

        // 3. Notional Currency
        if (isBlank(economics.getNotionalCurrency()) || economics.getNotionalCurrency().length() != 3) {
            result.addError(new ValidationError(
                    "trade.fxOptionEconomics.notionalCurrency",
                    "Notional currency is mandatory and must be a 3-character ISO code",
                    ExceptionCategory.MISSING_ECONOMIC_DATA,
                    ExceptionSubTheme.MISSING_NOTIONAL,
                    "VAL_INVALID_NOTIONAL_CCY"
            ));
        }

        // 4. Option Type (CALL / PUT)
        if (isBlank(economics.getOptionType()) || !VALID_OPTION_TYPES.contains(economics.getOptionType().toUpperCase())) {
            result.addError(new ValidationError(
                    "trade.fxOptionEconomics.optionType",
                    "Option type is mandatory and must be CALL or PUT (found: '" + economics.getOptionType() + "')",
                    ExceptionCategory.MISSING_ECONOMIC_DATA,
                    ExceptionSubTheme.MISSING_OPTION_TYPE,
                    "VAL_INVALID_OPTION_TYPE"
            ));
        }

        // 5. Buy / Sell
        if (isBlank(economics.getBuySell()) || !VALID_BUY_SELL.contains(economics.getBuySell().toUpperCase())) {
            result.addError(new ValidationError(
                    "trade.fxOptionEconomics.buySell",
                    "Buy/Sell indicator is mandatory and must be BUY or SELL",
                    ExceptionCategory.MISSING_ECONOMIC_DATA,
                    ExceptionSubTheme.MISSING_OPTION_TYPE,
                    "VAL_INVALID_BUY_SELL"
            ));
        }

        // 6. Underlier Currency Pair
        if (isBlank(economics.getUnderlier()) || !economics.getUnderlier().contains("/")) {
            result.addError(new ValidationError(
                    "trade.fxOptionEconomics.underlier",
                    "Underlier currency pair is mandatory (e.g., EUR/USD)",
                    ExceptionCategory.MISSING_ECONOMIC_DATA,
                    ExceptionSubTheme.MISSING_UNDERLIER,
                    "VAL_INVALID_UNDERLIER"
            ));
        }

        // 7. Premium Amount
        if (economics.getPremiumAmount() == null || economics.getPremiumAmount().compareTo(BigDecimal.ZERO) < 0) {
            result.addError(new ValidationError(
                    "trade.fxOptionEconomics.premiumAmount",
                    "Premium amount is mandatory and must be non-negative",
                    ExceptionCategory.MISSING_ECONOMIC_DATA,
                    ExceptionSubTheme.MISSING_PREMIUM,
                    "VAL_INVALID_PREMIUM"
            ));
        }

        // 8. Premium Currency
        if (isBlank(economics.getPremiumCurrency()) || economics.getPremiumCurrency().length() != 3) {
            result.addError(new ValidationError(
                    "trade.fxOptionEconomics.premiumCurrency",
                    "Premium currency is mandatory and must be a 3-character ISO code",
                    ExceptionCategory.MISSING_ECONOMIC_DATA,
                    ExceptionSubTheme.MISSING_PREMIUM_CURRENCY,
                    "VAL_INVALID_PREMIUM_CCY"
            ));
        }

        // 9. Premium Payment Date
        if (!isBlank(economics.getPremiumPaymentDate()) && !isBlank(tradeDateStr)) {
            try {
                LocalDate premiumDate = LocalDate.parse(economics.getPremiumPaymentDate());
                LocalDate tradeDate = LocalDate.parse(tradeDateStr);
                if (premiumDate.isBefore(tradeDate)) {
                    result.addError(new ValidationError(
                            "trade.fxOptionEconomics.premiumPaymentDate",
                            "Premium payment date (" + premiumDate + ") cannot precede trade date (" + tradeDate + ")",
                            ExceptionCategory.BUSINESS_RULE_FAILURE,
                            ExceptionSubTheme.TRADE_DATE_MISMATCH,
                            "VAL_PREMIUM_DATE_BEFORE_TRADE"
                    ));
                }
            } catch (DateTimeParseException ignored) {
                // Already reported or ignored if format invalid
            }
        }
    }

    // -----------------------------------------------------------------------
    // Non-Economic Terms & Business Rules
    // -----------------------------------------------------------------------

    private void validateNonEconomicTerms(NonEconomicTerms terms, ValidationResult result) {
        if (terms == null) {
            return;
        }

        if (!isBlank(terms.getJurisdiction())) {
            String jur = terms.getJurisdiction().toUpperCase();
            if (jur.contains("NON_STP") || jur.contains("RESTRICTED") || jur.contains("EMERGING")) {
                result.addError(new ValidationError(
                        "trade.nonEconomicTerms.jurisdiction",
                        "Jurisdiction '" + terms.getJurisdiction() + "' is not eligible for automated STP",
                        ExceptionCategory.BUSINESS_RULE_FAILURE,
                        ExceptionSubTheme.COUNTERPARTY_NOT_STP_ELIGIBLE,
                        "VAL_NON_STP_JURISDICTION"
                ));
            }
        }

        if (!isBlank(terms.getTemplateHint())) {
            String template = terms.getTemplateHint().toUpperCase();
            if (template.contains("MISSING") || template.contains("UNKNOWN") || template.contains("NONE")) {
                result.addError(new ValidationError(
                        "trade.nonEconomicTerms.templateHint",
                        "No valid confirmation template catalogue entry matches '" + terms.getTemplateHint() + "'",
                        ExceptionCategory.TEMPLATE_ISSUE,
                        ExceptionSubTheme.NO_MATCHING_TEMPLATE,
                        "VAL_TEMPLATE_NOT_FOUND"
                ));
            }
        }
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}
