package com.tcem.validation.exception;

import com.tcem.common.constants.EventType;
import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import com.tcem.common.constants.ProductType;
import com.tcem.common.event.ExceptionEvent;
import com.tcem.common.model.exception.ExceptionRecord;
import com.tcem.common.model.message.MessageHeader;
import com.tcem.common.model.trade.FxOptionEconomics;
import com.tcem.common.model.trade.Party;
import com.tcem.common.model.trade.Trade;
import com.tcem.validation.config.ValidationProperties;
import com.tcem.validation.rules.StpValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link StpExceptionMapper}.
 *
 * No Spring context — pure unit tests with a real {@link ValidationProperties} instance.
 *
 * Coverage:
 * - Each ExceptionCategory routes to the correct owner queue
 * - Traceability fields (tradeId, tradeVersion, sourceMessageId, correlationId) are propagated
 * - ExceptionRecord contains category, subTheme, reasonCode, affectedFields from the failure
 * - Multiple failures produce multiple ExceptionEvents (one per failure)
 * - replayMayResolve flag is set correctly per category
 * - Outbound MessageHeader carries the inbound correlationId
 * - classificationConfidence is always 1.0 (deterministic rules)
 * - exceptionId is generated and non-blank
 * - capturedAt is non-null
 */
class StpExceptionMapperTest {

    private StpExceptionMapper mapper;

    @BeforeEach
    void setUp() {
        ValidationProperties properties = new ValidationProperties();
        // uses default serviceName = "validation-service"
        mapper = new StpExceptionMapper(properties);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Trade buildTrade(String tradeId) {
        Trade trade = new Trade();
        trade.setTradeId(tradeId);
        trade.setTradeVersion(1);
        trade.setProductType(ProductType.FX_OPTION);

        Party bank = new Party();
        bank.setPartyRole("BANK");
        bank.setPartyId("BANK-001");

        Party cp = new Party();
        cp.setPartyRole("COUNTERPARTY");
        cp.setPartyId("CP-001");

        trade.setParties(List.of(bank, cp));

        FxOptionEconomics eco = new FxOptionEconomics();
        eco.setStrike(new BigDecimal("1.25"));
        trade.setFxOptionEconomics(eco);

        return trade;
    }

    private MessageHeader buildHeader(String messageId, String correlationId) {
        MessageHeader header = new MessageHeader();
        header.setMessageId(messageId);
        header.setCorrelationId(correlationId);
        return header;
    }

    private StpValidationResult resultWithSingleFailure(ExceptionCategory category,
                                                        ExceptionSubTheme subTheme,
                                                        String reasonCode,
                                                        String affectedFields) {
        StpValidationResult result = new StpValidationResult();
        result.addFailure(category, subTheme, reasonCode, affectedFields);
        return result;
    }

    // -----------------------------------------------------------------------
    // Queue routing tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("MISSING_ECONOMIC_DATA routes to DATA_REMEDIATION queue")
    void testMissingEconomicDataRoutesToDataRemediation() {
        Trade trade = buildTrade("TRD-20001");
        MessageHeader header = buildHeader("MSG-001", "CORR-001");
        StpValidationResult result = resultWithSingleFailure(
                ExceptionCategory.MISSING_ECONOMIC_DATA,
                ExceptionSubTheme.MISSING_STRIKE,
                "Strike price missing",
                "fxOptionEconomics.strike");

        List<ExceptionEvent> events = mapper.mapToExceptionEvents(header, trade, result);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getException().getOwnerQueue()).isEqualTo("DATA_REMEDIATION");
    }

    @Test
    @DisplayName("MISSING_STATIC_REFERENCE_DATA routes to DATA_REMEDIATION queue")
    void testMissingStaticReferenceDataRoutesToDataRemediation() {
        Trade trade = buildTrade("TRD-20002");
        MessageHeader header = buildHeader("MSG-002", "CORR-002");
        StpValidationResult result = resultWithSingleFailure(
                ExceptionCategory.MISSING_STATIC_REFERENCE_DATA,
                ExceptionSubTheme.UNKNOWN_COUNTERPARTY,
                "Unknown counterparty",
                "parties[COUNTERPARTY].partyId");

        List<ExceptionEvent> events = mapper.mapToExceptionEvents(header, trade, result);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getException().getOwnerQueue()).isEqualTo("DATA_REMEDIATION");
    }

    @Test
    @DisplayName("TEMPLATE_ISSUE routes to OPERATIONS_REVIEW queue")
    void testTemplateIssueRoutesToOperationsReview() {
        Trade trade = buildTrade("TRD-20003");
        MessageHeader header = buildHeader("MSG-003", "CORR-003");
        StpValidationResult result = resultWithSingleFailure(
                ExceptionCategory.TEMPLATE_ISSUE,
                ExceptionSubTheme.NO_MATCHING_TEMPLATE,
                "No template found for product",
                "enrichmentMetadata.resolvedTemplateId");

        List<ExceptionEvent> events = mapper.mapToExceptionEvents(header, trade, result);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getException().getOwnerQueue()).isEqualTo("OPERATIONS_REVIEW");
    }

    @Test
    @DisplayName("BUSINESS_RULE_FAILURE routes to OPERATIONS_REVIEW queue")
    void testBusinessRuleFailureRoutesToOperationsReview() {
        Trade trade = buildTrade("TRD-20004");
        MessageHeader header = buildHeader("MSG-004", "CORR-004");
        StpValidationResult result = resultWithSingleFailure(
                ExceptionCategory.BUSINESS_RULE_FAILURE,
                ExceptionSubTheme.COUNTERPARTY_NOT_STP_ELIGIBLE,
                "Counterparty not STP eligible",
                "enrichmentMetadata.counterpartySTPEligible");

        List<ExceptionEvent> events = mapper.mapToExceptionEvents(header, trade, result);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getException().getOwnerQueue()).isEqualTo("OPERATIONS_REVIEW");
    }

    @Test
    @DisplayName("TECHNICAL_PROCESSING_FAILURE routes to TECHNICAL_SUPPORT queue")
    void testTechnicalFailureRoutesToTechnicalSupport() {
        Trade trade = buildTrade("TRD-20005");
        MessageHeader header = buildHeader("MSG-005", "CORR-005");
        StpValidationResult result = resultWithSingleFailure(
                ExceptionCategory.TECHNICAL_PROCESSING_FAILURE,
                ExceptionSubTheme.PARSE_FAILURE,
                "Parse error in enrichment",
                "unknown");

        List<ExceptionEvent> events = mapper.mapToExceptionEvents(header, trade, result);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getException().getOwnerQueue()).isEqualTo("TECHNICAL_SUPPORT");
    }

    // -----------------------------------------------------------------------
    // Traceability tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Traceability fields are propagated from trade and header")
    void testTraceabilityFieldsPropagated() {
        Trade trade = buildTrade("TRD-20006");
        trade.setTradeVersion(3);
        MessageHeader header = buildHeader("MSG-TRACE-001", "CORR-TRACE-001");
        StpValidationResult result = resultWithSingleFailure(
                ExceptionCategory.MISSING_ECONOMIC_DATA,
                ExceptionSubTheme.MISSING_NOTIONAL,
                "Notional missing",
                "fxOptionEconomics.notionalAmount");

        List<ExceptionEvent> events = mapper.mapToExceptionEvents(header, trade, result);

        ExceptionRecord record = events.get(0).getException();
        assertThat(record.getTradeId()).isEqualTo("TRD-20006");
        assertThat(record.getTradeVersion()).isEqualTo(3);
        assertThat(record.getSourceMessageId()).isEqualTo("MSG-TRACE-001");
        assertThat(record.getCorrelationId()).isEqualTo("CORR-TRACE-001");
    }

    @Test
    @DisplayName("ExceptionRecord carries category, subTheme, reasonCode and affectedFields from failure")
    void testExceptionRecordFailureFields() {
        Trade trade = buildTrade("TRD-20007");
        MessageHeader header = buildHeader("MSG-007", "CORR-007");
        StpValidationResult result = resultWithSingleFailure(
                ExceptionCategory.BUSINESS_RULE_FAILURE,
                ExceptionSubTheme.PRODUCT_NOT_STP_ELIGIBLE,
                "Product FX_OPTION not STP eligible",
                "enrichmentMetadata.productSTPEligible");

        List<ExceptionEvent> events = mapper.mapToExceptionEvents(header, trade, result);

        ExceptionRecord record = events.get(0).getException();
        assertThat(record.getCategory()).isEqualTo(ExceptionCategory.BUSINESS_RULE_FAILURE);
        assertThat(record.getSubTheme()).isEqualTo(ExceptionSubTheme.PRODUCT_NOT_STP_ELIGIBLE);
        assertThat(record.getReasonCode()).isEqualTo("Product FX_OPTION not STP eligible");
        assertThat(record.getAffectedFields()).isEqualTo("enrichmentMetadata.productSTPEligible");
    }

    @Test
    @DisplayName("Multiple failures produce one ExceptionEvent per failure")
    void testMultipleFailuresProduceMultipleEvents() {
        Trade trade = buildTrade("TRD-20008");
        MessageHeader header = buildHeader("MSG-008", "CORR-008");

        StpValidationResult result = new StpValidationResult();
        result.addFailure(ExceptionCategory.MISSING_ECONOMIC_DATA,
                ExceptionSubTheme.MISSING_STRIKE, "Strike missing", "fxOptionEconomics.strike");
        result.addFailure(ExceptionCategory.MISSING_ECONOMIC_DATA,
                ExceptionSubTheme.MISSING_NOTIONAL, "Notional missing", "fxOptionEconomics.notionalAmount");
        result.addFailure(ExceptionCategory.TEMPLATE_ISSUE,
                ExceptionSubTheme.NO_MATCHING_TEMPLATE, "No template", "enrichmentMetadata.resolvedTemplateId");

        List<ExceptionEvent> events = mapper.mapToExceptionEvents(header, trade, result);

        assertThat(events).hasSize(3);
        // verify each has a distinct exceptionId
        assertThat(events.stream().map(e -> e.getException().getExceptionId()).distinct()).hasSize(3);
    }

    @Test
    @DisplayName("replayMayResolve is true for MISSING_ECONOMIC_DATA")
    void testReplayMayResolveForMissingEconomicData() {
        Trade trade = buildTrade("TRD-20009");
        MessageHeader header = buildHeader("MSG-009", "CORR-009");
        StpValidationResult result = resultWithSingleFailure(
                ExceptionCategory.MISSING_ECONOMIC_DATA,
                ExceptionSubTheme.MISSING_PREMIUM,
                "Premium missing",
                "fxOptionEconomics.premiumAmount");

        List<ExceptionEvent> events = mapper.mapToExceptionEvents(header, trade, result);

        assertThat(events.get(0).getException().isReplayMayResolve()).isTrue();
    }

    @Test
    @DisplayName("replayMayResolve is false for BUSINESS_RULE_FAILURE")
    void testReplayMayResolveIsFalseForBusinessRuleFailure() {
        Trade trade = buildTrade("TRD-20010");
        MessageHeader header = buildHeader("MSG-010", "CORR-010");
        StpValidationResult result = resultWithSingleFailure(
                ExceptionCategory.BUSINESS_RULE_FAILURE,
                ExceptionSubTheme.COUNTERPARTY_NOT_STP_ELIGIBLE,
                "Counterparty not eligible",
                "enrichmentMetadata.counterpartySTPEligible");

        List<ExceptionEvent> events = mapper.mapToExceptionEvents(header, trade, result);

        assertThat(events.get(0).getException().isReplayMayResolve()).isFalse();
    }

    @Test
    @DisplayName("Outbound MessageHeader carries inbound correlationId")
    void testOutboundHeaderCorrelationId() {
        Trade trade = buildTrade("TRD-20011");
        MessageHeader header = buildHeader("MSG-011", "CORR-XYZ-999");
        StpValidationResult result = resultWithSingleFailure(
                ExceptionCategory.TEMPLATE_ISSUE,
                ExceptionSubTheme.TEMPLATE_VERSION_MISMATCH,
                "Template version mismatch",
                "enrichmentMetadata.resolvedTemplateVersion");

        List<ExceptionEvent> events = mapper.mapToExceptionEvents(header, trade, result);

        assertThat(events.get(0).getMessageHeader().getCorrelationId()).isEqualTo("CORR-XYZ-999");
    }

    @Test
    @DisplayName("classificationConfidence is always 1.0")
    void testClassificationConfidenceIsAlwaysOne() {
        Trade trade = buildTrade("TRD-20012");
        MessageHeader header = buildHeader("MSG-012", "CORR-012");
        StpValidationResult result = resultWithSingleFailure(
                ExceptionCategory.MISSING_ECONOMIC_DATA,
                ExceptionSubTheme.MISSING_UNDERLIER,
                "Underlier missing",
                "fxOptionEconomics.underlier");

        List<ExceptionEvent> events = mapper.mapToExceptionEvents(header, trade, result);

        assertThat(events.get(0).getException().getClassificationConfidence()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("exceptionId is non-blank for every generated event")
    void testExceptionIdIsNonBlank() {
        Trade trade = buildTrade("TRD-20013");
        MessageHeader header = buildHeader("MSG-013", "CORR-013");
        StpValidationResult result = resultWithSingleFailure(
                ExceptionCategory.BUSINESS_RULE_FAILURE,
                ExceptionSubTheme.PRODUCT_NOT_STP_ELIGIBLE,
                "Product not eligible",
                "enrichmentMetadata.productSTPEligible");

        List<ExceptionEvent> events = mapper.mapToExceptionEvents(header, trade, result);

        assertThat(events.get(0).getException().getExceptionId()).isNotBlank();
    }

    @Test
    @DisplayName("capturedAt is non-null for every generated event")
    void testCapturedAtIsNonNull() {
        Trade trade = buildTrade("TRD-20014");
        MessageHeader header = buildHeader("MSG-014", "CORR-014");
        StpValidationResult result = resultWithSingleFailure(
                ExceptionCategory.MISSING_ECONOMIC_DATA,
                ExceptionSubTheme.MISSING_OPTION_TYPE,
                "Option type missing",
                "fxOptionEconomics.optionType");

        List<ExceptionEvent> events = mapper.mapToExceptionEvents(header, trade, result);

        assertThat(events.get(0).getException().getCapturedAt()).isNotNull();
    }

    @Test
    @DisplayName("Replay identity (eventType, replayCount) survives onto exception.created")
    void testReplayIdentityPropagated() {
        MessageHeader header = buildHeader("MSG-RPL", "CORR-RPL");
        header.setEventType(EventType.REPLAY);
        header.setReplayCount(2);
        StpValidationResult result = resultWithSingleFailure(
                ExceptionCategory.MISSING_ECONOMIC_DATA, ExceptionSubTheme.MISSING_STRIKE,
                "Strike price missing", "fxOptionEconomics.strike");

        MessageHeader outbound = mapper.mapToExceptionEvents(header, buildTrade("TRD-RPL"), result)
                .get(0).getMessageHeader();

        assertThat(outbound.getEventType()).isEqualTo(EventType.REPLAY);
        assertThat(outbound.getReplayCount()).isEqualTo(2);
    }
}
