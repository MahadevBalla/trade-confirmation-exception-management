package com.tcem.validation.rules;

import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import com.tcem.common.constants.ProductType;
import com.tcem.common.model.message.TradeEvent;
import com.tcem.common.model.reference.ReferenceDataEnrichment;
import com.tcem.common.model.trade.Party;
import com.tcem.common.model.trade.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BusinessEligibilityRule} (FR5 Rule 2).
 * <p>
 * Covers: missing counterparty party, missing enrichment metadata,
 * counterparty STP ineligibility (false and null), product STP ineligibility
 * (false and null), and the fully eligible happy path.
 * </p>
 */
@DisplayName("BusinessEligibilityRule")
class BusinessEligibilityRuleTest {

    private BusinessEligibilityRule rule;
    private StpValidationResult result;

    @BeforeEach
    void setUp() {
        rule = new BusinessEligibilityRule();
        result = new StpValidationResult();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Party counterparty(String id) {
        Party p = new Party();
        p.setPartyRole("COUNTERPARTY");
        p.setPartyId(id);
        return p;
    }

    private Party bank() {
        Party p = new Party();
        p.setPartyRole("BANK");
        p.setPartyId("PTY-BANK-001");
        return p;
    }

    private ReferenceDataEnrichment eligibleMeta() {
        ReferenceDataEnrichment meta = new ReferenceDataEnrichment();
        meta.setCounterpartySTPEligible(true);
        meta.setProductSTPEligible(true);
        meta.setResolvedTemplateId("TMPL-FX-OPT-001");
        meta.setResolvedTemplateVersion("1.2.0");
        return meta;
    }

    private TradeEvent eventWith(ReferenceDataEnrichment meta, List<Party> parties) {
        Trade trade = new Trade();
        trade.setTradeId("TRD-10001");
        trade.setProductType(ProductType.FX_OPTION);
        trade.setParties(parties);
        trade.setEnrichmentMetadata(meta);

        TradeEvent event = new TradeEvent();
        event.setTrade(trade);
        return event;
    }

    // -----------------------------------------------------------------------
    // Happy path
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("passes when counterparty and product are both STP eligible")
    void evaluate_bothEligible_stpPassed() {
        TradeEvent event = eventWith(eligibleMeta(), List.of(bank(), counterparty("PTY-CP-101")));

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isTrue();
        assertThat(result.getFailures()).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Null trade (guard clause)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("skips evaluation silently when trade is null (EconomicFieldsRule owns that check)")
    void evaluate_nullTrade_noFailureAdded() {
        TradeEvent event = new TradeEvent();
        event.setTrade(null);

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isTrue(); // no double-counting
    }

    // -----------------------------------------------------------------------
    // Missing COUNTERPARTY party
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("fails UNKNOWN_COUNTERPARTY when parties list is null")
    void evaluate_nullParties_failsUnknownCounterparty() {
        TradeEvent event = eventWith(eligibleMeta(), null);

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isFalse();
        assertThat(result.getFailures()).hasSize(1);
        assertThat(result.getFailures().get(0).getSubTheme())
                .isEqualTo(ExceptionSubTheme.UNKNOWN_COUNTERPARTY);
        assertThat(result.getFailures().get(0).getCategory())
                .isEqualTo(ExceptionCategory.MISSING_STATIC_REFERENCE_DATA);
    }

    @Test
    @DisplayName("fails UNKNOWN_COUNTERPARTY when no party with role COUNTERPARTY exists")
    void evaluate_onlyBankParty_failsUnknownCounterparty() {
        TradeEvent event = eventWith(eligibleMeta(), List.of(bank()));

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isFalse();
        assertThat(result.getFailures().get(0).getSubTheme())
                .isEqualTo(ExceptionSubTheme.UNKNOWN_COUNTERPARTY);
    }

    // -----------------------------------------------------------------------
    // Missing enrichment metadata
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("fails ENRICHMENT_TIMEOUT when enrichmentMetadata is null")
    void evaluate_nullEnrichmentMetadata_failsEnrichmentTimeout() {
        TradeEvent event = eventWith(null, List.of(bank(), counterparty("PTY-CP-101")));

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isFalse();
        assertThat(result.getFailures()).hasSize(1);
        assertThat(result.getFailures().get(0).getSubTheme())
                .isEqualTo(ExceptionSubTheme.ENRICHMENT_TIMEOUT);
        assertThat(result.getFailures().get(0).getCategory())
                .isEqualTo(ExceptionCategory.TECHNICAL_PROCESSING_FAILURE);
    }

    // -----------------------------------------------------------------------
    // Counterparty STP eligibility
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("fails COUNTERPARTY_NOT_STP_ELIGIBLE when counterpartySTPEligible is false")
    void evaluate_counterpartyIneligible_false_fails() {
        ReferenceDataEnrichment meta = eligibleMeta();
        meta.setCounterpartySTPEligible(false);
        TradeEvent event = eventWith(meta, List.of(bank(), counterparty("PTY-CP-102")));

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isFalse();
        boolean hasCpFailure = result.getFailures().stream()
                .anyMatch(f -> f.getSubTheme() == ExceptionSubTheme.COUNTERPARTY_NOT_STP_ELIGIBLE);
        assertThat(hasCpFailure).isTrue();
    }

    @Test
    @DisplayName("fails COUNTERPARTY_NOT_STP_ELIGIBLE when counterpartySTPEligible is null (unknown)")
    void evaluate_counterpartyIneligible_null_fails() {
        ReferenceDataEnrichment meta = eligibleMeta();
        meta.setCounterpartySTPEligible(null);
        TradeEvent event = eventWith(meta, List.of(bank(), counterparty("PTY-CP-103")));

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isFalse();
        boolean hasCpFailure = result.getFailures().stream()
                .anyMatch(f -> f.getSubTheme() == ExceptionSubTheme.COUNTERPARTY_NOT_STP_ELIGIBLE);
        assertThat(hasCpFailure).isTrue();
    }

    // -----------------------------------------------------------------------
    // Product STP eligibility
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("fails PRODUCT_NOT_STP_ELIGIBLE when productSTPEligible is false")
    void evaluate_productIneligible_false_fails() {
        ReferenceDataEnrichment meta = eligibleMeta();
        meta.setProductSTPEligible(false);
        TradeEvent event = eventWith(meta, List.of(bank(), counterparty("PTY-CP-101")));

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isFalse();
        boolean hasProductFailure = result.getFailures().stream()
                .anyMatch(f -> f.getSubTheme() == ExceptionSubTheme.PRODUCT_NOT_STP_ELIGIBLE);
        assertThat(hasProductFailure).isTrue();
    }

    @Test
    @DisplayName("fails PRODUCT_NOT_STP_ELIGIBLE when productSTPEligible is null")
    void evaluate_productIneligible_null_fails() {
        ReferenceDataEnrichment meta = eligibleMeta();
        meta.setProductSTPEligible(null);
        TradeEvent event = eventWith(meta, List.of(bank(), counterparty("PTY-CP-101")));

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isFalse();
        boolean hasProductFailure = result.getFailures().stream()
                .anyMatch(f -> f.getSubTheme() == ExceptionSubTheme.PRODUCT_NOT_STP_ELIGIBLE);
        assertThat(hasProductFailure).isTrue();
    }

    // -----------------------------------------------------------------------
    // Non-short-circuit: both ineligible → two failures
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("records two failures when both counterparty and product are ineligible")
    void evaluate_bothIneligible_twoFailures() {
        ReferenceDataEnrichment meta = eligibleMeta();
        meta.setCounterpartySTPEligible(false);
        meta.setProductSTPEligible(false);
        TradeEvent event = eventWith(meta, List.of(bank(), counterparty("PTY-CP-101")));

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isFalse();
        assertThat(result.getFailures()).hasSize(2);

        boolean hasCp = result.getFailures().stream()
                .anyMatch(f -> f.getSubTheme() == ExceptionSubTheme.COUNTERPARTY_NOT_STP_ELIGIBLE);
        boolean hasProd = result.getFailures().stream()
                .anyMatch(f -> f.getSubTheme() == ExceptionSubTheme.PRODUCT_NOT_STP_ELIGIBLE);
        assertThat(hasCp).isTrue();
        assertThat(hasProd).isTrue();
    }

    // -----------------------------------------------------------------------
    // Party role case-insensitive matching
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("resolves counterparty when role is lower-cased (case-insensitive match)")
    void evaluate_lowerCaseCounterpartyRole_resolved() {
        Party cp = new Party();
        cp.setPartyRole("counterparty");   // lowercase
        cp.setPartyId("PTY-CP-200");

        TradeEvent event = eventWith(eligibleMeta(), List.of(bank(), cp));

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isTrue();
    }
}

