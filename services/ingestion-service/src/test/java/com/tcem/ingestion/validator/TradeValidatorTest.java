package com.tcem.ingestion.validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import com.tcem.common.constants.ProductType;
import com.tcem.common.model.exception.ExceptionRecord;
import com.tcem.common.model.message.MessageHeader;
import com.tcem.common.model.message.SchemaVersions;
import com.tcem.common.model.message.TradeEvent;
import com.tcem.common.model.trade.FxOptionEconomics;
import com.tcem.common.model.trade.NonEconomicTerms;
import com.tcem.common.model.trade.Party;
import com.tcem.common.model.trade.Trade;
import com.tcem.ingestion.generator.SyntheticTradeGenerator;
import com.tcem.ingestion.generator.TradeScenarioType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TradeValidatorTest {

    private TradeValidator validator;
    private ObjectMapper objectMapper;
    private SyntheticTradeGenerator generator;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        validator = new TradeValidator(objectMapper);
        generator = new SyntheticTradeGenerator(objectMapper);
    }

    private TradeEvent createValidTradeEvent() {
        MessageHeader header = new MessageHeader();
        header.setMessageId("MSG-001");
        header.setCorrelationId("CORR-001");
        header.setEventType("NEW_TRADE");
        header.setSchemaVersion(SchemaVersions.V1_0);
        header.setSourceService("test-generator");

        Trade trade = new Trade();
        trade.setTradeId("TRD-10001");
        trade.setSourceSystemTradeId("SRC-001");
        trade.setTradeVersion(1);
        trade.setProductType(ProductType.FX_OPTION);
        trade.setTradeDate("2026-10-10");
        trade.setEffectiveDate("2026-10-12");
        trade.setMaturityDate("2027-01-10");

        Party bank = new Party();
        bank.setPartyRole("BANK");
        bank.setPartyId("PTY-BANK-001");
        bank.setLegalEntityName("Global Investment Bank");

        Party cp = new Party();
        cp.setPartyRole("COUNTERPARTY");
        cp.setPartyId("PTY-CP-101");
        cp.setLegalEntityName("Client Financial Entity");

        trade.setParties(List.of(bank, cp));

        FxOptionEconomics economics = new FxOptionEconomics();
        economics.setNotionalAmount(new BigDecimal("1000000"));
        economics.setNotionalCurrency("USD");
        economics.setBuySell("BUY");
        economics.setOptionType("CALL");
        economics.setStrike(new BigDecimal("1.0850"));
        economics.setUnderlier("EUR/USD");
        economics.setPremiumAmount(new BigDecimal("15000"));
        economics.setPremiumCurrency("USD");
        economics.setPremiumPaymentDate("2026-10-12");

        trade.setFxOptionEconomics(economics);

        NonEconomicTerms terms = new NonEconomicTerms();
        terms.setJurisdiction("GENERIC");
        terms.setConfirmationMethod("ELECTRONIC");
        terms.setTemplateHint("FX_OPTION_STANDARD");
        terms.setGoverningLaw("NEW_YORK");
        trade.setNonEconomicTerms(terms);

        TradeEvent event = new TradeEvent();
        event.setMessageHeader(header);
        event.setTrade(trade);
        return event;
    }

    @Test
    @DisplayName("Valid standard STP trade passes validation with zero errors")
    void validate_validTrade_passesValidation() {
        TradeEvent event = createValidTradeEvent();
        ValidationResult result = validator.validate(event);

        assertThat(result.isValid()).isTrue();
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("Generated standard STP trade passes validation")
    void validate_generatorStandardStpTrade_passesValidation() throws Exception {
        String json = generator.generateTradeJson(TradeScenarioType.VALID_STANDARD_STP, 1);
        ValidationResult result = validator.validateRawJson(json);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("Absent strike passes schema validation; EconomicFieldsRule raises it at STP (FR5)")
    void validate_missingStrike_passes() {
        TradeEvent event = createValidTradeEvent();
        event.getTrade().getFxOptionEconomics().setStrike(null);

        assertThat(validator.validate(event).isValid()).isTrue();
    }

    @Test
    @DisplayName("Zero or negative strike triggers MISSING_ECONOMIC_DATA / MISSING_STRIKE")
    void validate_zeroOrNegativeStrike_fails() {
        TradeEvent event = createValidTradeEvent();
        event.getTrade().getFxOptionEconomics().setStrike(BigDecimal.ZERO);

        ValidationResult result = validator.validate(event);

        assertThat(result.isValid()).isFalse();
        assertThat(result.firstError().getSubTheme()).isEqualTo(ExceptionSubTheme.MISSING_STRIKE);
    }

    @Test
    @DisplayName("Absent notional amount passes schema validation")
    void validate_missingNotional_passes() {
        TradeEvent event = createValidTradeEvent();
        event.getTrade().getFxOptionEconomics().setNotionalAmount(null);

        assertThat(validator.validate(event).isValid()).isTrue();
    }

    @Test
    @DisplayName("Absent premium amount passes schema validation")
    void validate_missingPremium_passes() {
        TradeEvent event = createValidTradeEvent();
        event.getTrade().getFxOptionEconomics().setPremiumAmount(null);

        assertThat(validator.validate(event).isValid()).isTrue();
    }

    @Test
    @DisplayName("Absent option type passes schema validation")
    void validate_missingOptionType_passes() {
        TradeEvent event = createValidTradeEvent();
        event.getTrade().getFxOptionEconomics().setOptionType(null);

        assertThat(validator.validate(event).isValid()).isTrue();
    }

    @Test
    @DisplayName("Absent underlier passes schema validation")
    void validate_missingUnderlier_passes() {
        TradeEvent event = createValidTradeEvent();
        event.getTrade().getFxOptionEconomics().setUnderlier(null);

        assertThat(validator.validate(event).isValid()).isTrue();
    }

    @Test
    @DisplayName("Unknown or missing counterparty triggers MISSING_STATIC_REFERENCE_DATA / UNKNOWN_COUNTERPARTY")
    void validate_unknownCounterparty_fails() {
        TradeEvent event = createValidTradeEvent();
        Party cp = event.getTrade().getParties().get(1);
        cp.setPartyId("UNKNOWN_CLIENT_999");

        ValidationResult result = validator.validate(event);

        assertThat(result.isValid()).isFalse();
        assertThat(result.firstError().getCategory()).isEqualTo(ExceptionCategory.MISSING_STATIC_REFERENCE_DATA);
        assertThat(result.firstError().getSubTheme()).isEqualTo(ExceptionSubTheme.UNKNOWN_COUNTERPARTY);
    }

    @Test
    @DisplayName("Chronologically inverted dates (effective before trade date) triggers TRADE_DATE_MISMATCH")
    void validate_invertedDates_fails() {
        TradeEvent event = createValidTradeEvent();
        event.getTrade().setTradeDate("2026-10-15");
        event.getTrade().setEffectiveDate("2026-10-10"); // before trade date!

        ValidationResult result = validator.validate(event);

        assertThat(result.isValid()).isFalse();
        assertThat(result.firstError().getCategory()).isEqualTo(ExceptionCategory.BUSINESS_RULE_FAILURE);
        assertThat(result.firstError().getSubTheme()).isEqualTo(ExceptionSubTheme.TRADE_DATE_MISMATCH);
    }

    @Test
    @DisplayName("Unsupported schema version triggers TECHNICAL_PROCESSING_FAILURE / SCHEMA_VERSION_UNSUPPORTED")
    void validate_unsupportedSchemaVersion_fails() {
        TradeEvent event = createValidTradeEvent();
        event.getMessageHeader().setSchemaVersion("capstone-fpml-json-9.9");

        ValidationResult result = validator.validate(event);

        assertThat(result.isValid()).isFalse();
        assertThat(result.firstError().getCategory()).isEqualTo(ExceptionCategory.TECHNICAL_PROCESSING_FAILURE);
        assertThat(result.firstError().getSubTheme()).isEqualTo(ExceptionSubTheme.SCHEMA_VERSION_UNSUPPORTED);
    }

    @Test
    @DisplayName("Non-STP jurisdiction triggers BUSINESS_RULE_FAILURE / COUNTERPARTY_NOT_STP_ELIGIBLE")
    void validate_nonStpJurisdiction_fails() {
        TradeEvent event = createValidTradeEvent();
        event.getTrade().getNonEconomicTerms().setJurisdiction("NON_STP_EMERGING_MARKET");

        ValidationResult result = validator.validate(event);

        assertThat(result.isValid()).isFalse();
        assertThat(result.firstError().getCategory()).isEqualTo(ExceptionCategory.BUSINESS_RULE_FAILURE);
        assertThat(result.firstError().getSubTheme()).isEqualTo(ExceptionSubTheme.COUNTERPARTY_NOT_STP_ELIGIBLE);
    }

    @Test
    @DisplayName("Missing confirmation template triggers TEMPLATE_ISSUE / NO_MATCHING_TEMPLATE")
    void validate_missingTemplate_fails() {
        TradeEvent event = createValidTradeEvent();
        event.getTrade().getNonEconomicTerms().setTemplateHint("UNKNOWN_TEMPLATE_SPECIAL");

        ValidationResult result = validator.validate(event);

        assertThat(result.isValid()).isFalse();
        assertThat(result.firstError().getCategory()).isEqualTo(ExceptionCategory.TEMPLATE_ISSUE);
        assertThat(result.firstError().getSubTheme()).isEqualTo(ExceptionSubTheme.NO_MATCHING_TEMPLATE);
    }

    @Test
    @DisplayName("Malformed JSON syntax in validateRawJson triggers PARSE_FAILURE")
    void validateRawJson_malformedJson_fails() {
        String brokenJson = "{\"trade\": { unquotedKey: invalid }";
        ValidationResult result = validator.validateRawJson(brokenJson);

        assertThat(result.isValid()).isFalse();
        assertThat(result.firstError().getCategory()).isEqualTo(ExceptionCategory.TECHNICAL_PROCESSING_FAILURE);
        assertThat(result.firstError().getSubTheme()).isEqualTo(ExceptionSubTheme.PARSE_FAILURE);
    }

    @Test
    @DisplayName("ValidationResult converts errors to platform ExceptionRecords with correct mapping")
    void toExceptionRecords_generatesValidRecords() {
        TradeEvent event = createValidTradeEvent();
        event.getTrade().getFxOptionEconomics().setStrike(new BigDecimal("-1"));

        ValidationResult result = validator.validate(event);
        List<ExceptionRecord> records = result.toExceptionRecords(event);

        assertThat(records).hasSize(1);
        ExceptionRecord record = records.get(0);
        assertThat(record.getExceptionId()).startsWith("EXC-");
        assertThat(record.getTradeId()).isEqualTo("TRD-10001");
        assertThat(record.getTradeVersion()).isEqualTo(1);
        assertThat(record.getSourceMessageId()).isEqualTo("MSG-001");
        assertThat(record.getCorrelationId()).isEqualTo("CORR-001");
        assertThat(record.getCategory()).isEqualTo(ExceptionCategory.MISSING_ECONOMIC_DATA);
        assertThat(record.getSubTheme()).isEqualTo(ExceptionSubTheme.MISSING_STRIKE);
        assertThat(record.isReplayMayResolve()).isTrue();
        assertThat(record.getOwnerQueue()).isEqualTo("DATA_REMEDIATION");
        assertThat(record.getStatus()).isEqualTo("OPEN");
        assertThat(record.getRecommendedAction()).contains("strike");
    }

    @Test
    @DisplayName("Null TradeEvent produces VAL_NULL_EVENT error")
    void validate_nullEvent_failsWithNullEventError() {
        ValidationResult result = validator.validate(null);

        assertThat(result.isValid()).isFalse();
        assertThat(result.firstError().getReasonCode()).isEqualTo("VAL_NULL_EVENT");
        assertThat(result.firstError().getCategory()).isEqualTo(ExceptionCategory.TECHNICAL_PROCESSING_FAILURE);
    }

    @Test
    @DisplayName("Null MessageHeader produces VAL_HEADER_NULL error")
    void validate_nullHeader_failsWithHeaderNullError() {
        TradeEvent event = createValidTradeEvent();
        event.setMessageHeader(null);

        ValidationResult result = validator.validate(event);

        assertThat(result.isValid()).isFalse();
        assertThat(result.firstError().getReasonCode()).isEqualTo("VAL_HEADER_NULL");
    }

    @Test
    @DisplayName("Blank or missing messageId produces VAL_MISSING_MSG_ID error")
    void validate_missingMessageId_fails() {
        TradeEvent event = createValidTradeEvent();
        event.getMessageHeader().setMessageId("  ");

        ValidationResult result = validator.validate(event);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> "VAL_MISSING_MSG_ID".equals(e.getReasonCode()));
    }

    @Test
    @DisplayName("Blank or missing correlationId produces VAL_MISSING_CORR_ID error")
    void validate_missingCorrelationId_fails() {
        TradeEvent event = createValidTradeEvent();
        event.getMessageHeader().setCorrelationId(null);

        ValidationResult result = validator.validate(event);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> "VAL_MISSING_CORR_ID".equals(e.getReasonCode()));
    }

    @Test
    @DisplayName("Null eventType produces VAL_MISSING_EVENT_TYPE error")
    void validate_missingEventType_fails() {
        TradeEvent event = createValidTradeEvent();
        event.getMessageHeader().setEventType((String) null);

        ValidationResult result = validator.validate(event);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> "VAL_MISSING_EVENT_TYPE".equals(e.getReasonCode()));
    }

    @Test
    @DisplayName("Null Trade body produces VAL_TRADE_NULL error")
    void validate_nullTrade_failsWithTradeNullError() {
        TradeEvent event = createValidTradeEvent();
        event.setTrade(null);

        ValidationResult result = validator.validate(event);

        assertThat(result.isValid()).isFalse();
        assertThat(result.firstError().getReasonCode()).isEqualTo("VAL_TRADE_NULL");
    }

    @Test
    @DisplayName("Missing tradeId produces VAL_MISSING_TRADE_ID error")
    void validate_missingTradeId_fails() {
        TradeEvent event = createValidTradeEvent();
        event.getTrade().setTradeId(null);

        ValidationResult result = validator.validate(event);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> "VAL_MISSING_TRADE_ID".equals(e.getReasonCode()));
    }

    @Test
    @DisplayName("Missing sourceSystemTradeId produces VAL_MISSING_SRC_TRADE_ID error")
    void validate_missingSourceSystemTradeId_fails() {
        TradeEvent event = createValidTradeEvent();
        event.getTrade().setSourceSystemTradeId("   ");

        ValidationResult result = validator.validate(event);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> "VAL_MISSING_SRC_TRADE_ID".equals(e.getReasonCode()));
    }

    @Test
    @DisplayName("Missing productType produces VAL_MISSING_PRODUCT_TYPE error")
    void validate_missingProductType_fails() {
        TradeEvent event = createValidTradeEvent();
        event.getTrade().setProductType(null);

        ValidationResult result = validator.validate(event);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> "VAL_MISSING_PRODUCT_TYPE".equals(e.getReasonCode()));
    }

    @Test
    @DisplayName("Missing tradeDate produces VAL_MISSING_DATE error")
    void validate_missingTradeDate_fails() {
        TradeEvent event = createValidTradeEvent();
        event.getTrade().setTradeDate(null);

        ValidationResult result = validator.validate(event);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> "VAL_MISSING_DATE".equals(e.getReasonCode()));
    }

    @Test
    @DisplayName("Invalid ISO-8601 date syntax produces VAL_INVALID_DATE_FORMAT error")
    void validate_invalidDateFormat_fails() {
        TradeEvent event = createValidTradeEvent();
        event.getTrade().setTradeDate("10/10/2026");

        ValidationResult result = validator.validate(event);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> "VAL_INVALID_DATE_FORMAT".equals(e.getReasonCode()));
    }

    @Test
    @DisplayName("Maturity date chronologically preceding effective date produces VAL_DATE_MATURITY_BEFORE_EFFECTIVE")
    void validate_maturityBeforeEffectiveDate_fails() {
        TradeEvent event = createValidTradeEvent();
        event.getTrade().setTradeDate("2026-10-10");
        event.getTrade().setEffectiveDate("2026-10-12");
        event.getTrade().setMaturityDate("2026-10-11"); // before effectiveDate

        ValidationResult result = validator.validate(event);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> "VAL_DATE_MATURITY_BEFORE_EFFECTIVE".equals(e.getReasonCode()));
    }

    @Test
    @DisplayName("Empty parties list produces VAL_PARTIES_EMPTY error")
    void validate_emptyPartiesList_fails() {
        TradeEvent event = createValidTradeEvent();
        event.getTrade().setParties(new ArrayList<>());

        ValidationResult result = validator.validate(event);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> "VAL_PARTIES_EMPTY".equals(e.getReasonCode()));
    }

    @Test
    @DisplayName("Missing executing BANK role produces VAL_MISSING_BANK_PARTY error")
    void validate_missingBankParty_fails() {
        TradeEvent event = createValidTradeEvent();
        Party cpOnly = new Party();
        cpOnly.setPartyRole("COUNTERPARTY");
        cpOnly.setPartyId("CP-123");
        event.getTrade().setParties(List.of(cpOnly));

        ValidationResult result = validator.validate(event);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> "VAL_MISSING_BANK_PARTY".equals(e.getReasonCode()));
    }

    @Test
    @DisplayName("Missing COUNTERPARTY role produces VAL_MISSING_CP_PARTY error")
    void validate_missingCounterpartyParty_fails() {
        TradeEvent event = createValidTradeEvent();
        Party bankOnly = new Party();
        bankOnly.setPartyRole("BANK");
        bankOnly.setPartyId("BANK-001");
        event.getTrade().setParties(List.of(bankOnly));

        ValidationResult result = validator.validate(event);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> "VAL_MISSING_CP_PARTY".equals(e.getReasonCode()));
    }

    @Test
    @DisplayName("Absent fxOptionEconomics block passes schema validation")
    void validate_nullFxOptionEconomics_passes() {
        TradeEvent event = createValidTradeEvent();
        event.getTrade().setFxOptionEconomics(null);

        assertThat(validator.validate(event).isValid()).isTrue();
    }

    @Test
    @DisplayName("Negative notional amount produces VAL_INVALID_NOTIONAL error")
    void validate_negativeNotional_fails() {
        TradeEvent event = createValidTradeEvent();
        event.getTrade().getFxOptionEconomics().setNotionalAmount(new BigDecimal("-100000"));

        ValidationResult result = validator.validate(event);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> "VAL_INVALID_NOTIONAL".equals(e.getReasonCode()));
    }

    @Test
    @DisplayName("Notional currency with invalid length produces VAL_INVALID_NOTIONAL_CCY error")
    void validate_invalidNotionalCurrencyLength_fails() {
        TradeEvent event = createValidTradeEvent();
        event.getTrade().getFxOptionEconomics().setNotionalCurrency("US");

        ValidationResult result = validator.validate(event);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> "VAL_INVALID_NOTIONAL_CCY".equals(e.getReasonCode()));
    }

    @Test
    @DisplayName("Invalid BuySell indicator produces VAL_INVALID_BUY_SELL error")
    void validate_invalidBuySellValue_fails() {
        TradeEvent event = createValidTradeEvent();
        event.getTrade().getFxOptionEconomics().setBuySell("HOLD");

        ValidationResult result = validator.validate(event);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> "VAL_INVALID_BUY_SELL".equals(e.getReasonCode()));
    }

    @Test
    @DisplayName("Invalid option type indicator produces VAL_INVALID_OPTION_TYPE error")
    void validate_invalidOptionTypeValue_fails() {
        TradeEvent event = createValidTradeEvent();
        event.getTrade().getFxOptionEconomics().setOptionType("STRADDLE");

        ValidationResult result = validator.validate(event);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> "VAL_INVALID_OPTION_TYPE".equals(e.getReasonCode()));
    }

    @Test
    @DisplayName("Underlier missing slash delimiter produces VAL_INVALID_UNDERLIER error")
    void validate_invalidUnderlierPair_fails() {
        TradeEvent event = createValidTradeEvent();
        event.getTrade().getFxOptionEconomics().setUnderlier("EURUSD");

        ValidationResult result = validator.validate(event);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> "VAL_INVALID_UNDERLIER".equals(e.getReasonCode()));
    }

    @Test
    @DisplayName("Negative premium amount produces VAL_INVALID_PREMIUM error")
    void validate_negativePremiumAmount_fails() {
        TradeEvent event = createValidTradeEvent();
        event.getTrade().getFxOptionEconomics().setPremiumAmount(new BigDecimal("-500"));

        ValidationResult result = validator.validate(event);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> "VAL_INVALID_PREMIUM".equals(e.getReasonCode()));
    }

    @Test
    @DisplayName("Premium currency with invalid length produces VAL_INVALID_PREMIUM_CCY error")
    void validate_invalidPremiumCurrencyLength_fails() {
        TradeEvent event = createValidTradeEvent();
        event.getTrade().getFxOptionEconomics().setPremiumCurrency("DOLLAR");

        ValidationResult result = validator.validate(event);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> "VAL_INVALID_PREMIUM_CCY".equals(e.getReasonCode()));
    }

    @Test
    @DisplayName("Premium payment date preceding trade date produces VAL_PREMIUM_DATE_BEFORE_TRADE error")
    void validate_premiumDateBeforeTradeDate_fails() {
        TradeEvent event = createValidTradeEvent();
        event.getTrade().setTradeDate("2026-10-10");
        event.getTrade().getFxOptionEconomics().setPremiumPaymentDate("2026-10-09");

        ValidationResult result = validator.validate(event);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> "VAL_PREMIUM_DATE_BEFORE_TRADE".equals(e.getReasonCode()));
    }

    @Test
    @DisplayName("Empty or whitespace-only raw JSON string produces VAL_EMPTY_PAYLOAD error")
    void validateRawJson_emptyPayload_fails() {
        ValidationResult result = validator.validateRawJson("   \n\t  ");

        assertThat(result.isValid()).isFalse();
        assertThat(result.firstError().getReasonCode()).isEqualTo("VAL_EMPTY_PAYLOAD");
    }

    @Test
    @DisplayName("Raw JSON missing messageHeader envelope produces VAL_MISSING_HEADER error")
    void validateRawJson_missingHeaderNode_fails() {
        String json = "{\"trade\":{\"tradeId\":\"TRD-999\"}}";
        ValidationResult result = validator.validateRawJson(json);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> "VAL_MISSING_HEADER".equals(e.getReasonCode()));
    }

    @Test
    @DisplayName("Raw JSON missing trade envelope produces VAL_MISSING_TRADE error")
    void validateRawJson_missingTradeNode_fails() {
        String json = "{\"messageHeader\":{\"messageId\":\"MSG-1\"}}";
        ValidationResult result = validator.validateRawJson(json);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> "VAL_MISSING_TRADE".equals(e.getReasonCode()));
    }

    @Test
    @DisplayName("Multiple validation errors across headers, economics, and dates are all accumulated without early exit")
    void validate_multipleValidationFailures_accumulatesAllErrors() {
        TradeEvent event = createValidTradeEvent();
        event.getMessageHeader().setMessageId(null); // error 1
        event.getTrade().getFxOptionEconomics().setStrike(new BigDecimal("-1")); // error 2
        event.getTrade().getFxOptionEconomics().setNotionalAmount(BigDecimal.ZERO); // error 3
        event.getTrade().getFxOptionEconomics().setBuySell("INVALID"); // error 4

        ValidationResult result = validator.validate(event);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors().size()).isGreaterThanOrEqualTo(4);
    }
}
