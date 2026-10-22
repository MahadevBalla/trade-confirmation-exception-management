package com.tcem.validation.rules;

import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import com.tcem.common.constants.ProductType;
import com.tcem.common.model.message.TradeEvent;
import com.tcem.common.model.reference.ReferenceDataEnrichment;
import com.tcem.common.model.trade.NonEconomicTerms;
import com.tcem.common.model.trade.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TemplateResolutionRule} (FR5 Rule 3).
 * <p>
 * Covers: null trade guard, null enrichment metadata guard, missing templateId,
 * templateId present but version missing (TEMPLATE_VERSION_MISMATCH), and the
 * fully resolved happy path.
 * </p>
 */
@DisplayName("TemplateResolutionRule")
class TemplateResolutionRuleTest {

    private TemplateResolutionRule rule;
    private StpValidationResult result;

    @BeforeEach
    void setUp() {
        rule = new TemplateResolutionRule();
        result = new StpValidationResult();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private TradeEvent eventWith(String templateId, String templateVersion) {
        ReferenceDataEnrichment meta = new ReferenceDataEnrichment();
        meta.setResolvedTemplateId(templateId);
        meta.setResolvedTemplateVersion(templateVersion);
        meta.setCounterpartySTPEligible(true);
        meta.setProductSTPEligible(true);

        Trade trade = new Trade();
        trade.setTradeId("TRD-10001");
        trade.setProductType(ProductType.FX_OPTION);
        trade.setEnrichmentMetadata(meta);

        TradeEvent event = new TradeEvent();
        event.setTrade(trade);
        return event;
    }

    // -----------------------------------------------------------------------
    // Happy path
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("passes when both templateId and templateVersion are resolved")
    void evaluate_bothResolved_stpPassed() {
        TradeEvent event = eventWith("TMPL-FX-OPT-001", "1.2.0");

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isTrue();
        assertThat(result.getFailures()).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Guard clauses — null trade / null meta handled upstream
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("skips silently when trade is null")
    void evaluate_nullTrade_noFailureAdded() {
        TradeEvent event = new TradeEvent();
        event.setTrade(null);

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isTrue();
    }

    @Test
    @DisplayName("skips silently when enrichmentMetadata is null")
    void evaluate_nullEnrichmentMetadata_noFailureAdded() {
        Trade trade = new Trade();
        trade.setTradeId("TRD-10001");
        trade.setEnrichmentMetadata(null);

        TradeEvent event = new TradeEvent();
        event.setTrade(trade);

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isTrue();
    }

    // -----------------------------------------------------------------------
    // No matching template (templateId is null)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("fails NO_MATCHING_TEMPLATE when resolvedTemplateId is null")
    void evaluate_nullTemplateId_failsNoMatchingTemplate() {
        TradeEvent event = eventWith(null, "1.2.0");

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isFalse();
        assertThat(result.getFailures()).hasSize(1);
        assertThat(result.getFailures().get(0).getCategory())
                .isEqualTo(ExceptionCategory.TEMPLATE_ISSUE);
        assertThat(result.getFailures().get(0).getSubTheme())
                .isEqualTo(ExceptionSubTheme.NO_MATCHING_TEMPLATE);
        assertThat(result.getFailures().get(0).getAffectedFields())
                .isEqualTo("enrichmentMetadata.resolvedTemplateId");
    }

    @Test
    @DisplayName("fails NO_MATCHING_TEMPLATE when resolvedTemplateId is blank")
    void evaluate_blankTemplateId_failsNoMatchingTemplate() {
        TradeEvent event = eventWith("  ", "1.2.0");

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isFalse();
        assertThat(result.getFailures().get(0).getSubTheme())
                .isEqualTo(ExceptionSubTheme.NO_MATCHING_TEMPLATE);
    }

    @Test
    @DisplayName("failure reason includes productType and templateHint when templateId is missing")
    void evaluate_nullTemplateId_failureReasonContainsHint() {
        ReferenceDataEnrichment meta = new ReferenceDataEnrichment();
        meta.setResolvedTemplateId(null);
        meta.setResolvedTemplateVersion("1.2.0");

        NonEconomicTerms nonEco = new NonEconomicTerms();
        nonEco.setTemplateHint("FX_OPTION_STANDARD");

        Trade trade = new Trade();
        trade.setTradeId("TRD-10001");
        trade.setProductType(ProductType.FX_OPTION);
        trade.setEnrichmentMetadata(meta);
        trade.setNonEconomicTerms(nonEco);

        TradeEvent event = new TradeEvent();
        event.setTrade(trade);

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isFalse();
        assertThat(result.getFailures().get(0).getReasonCode())
                .contains("FX_OPTION")
                .contains("FX_OPTION_STANDARD");
    }

    // -----------------------------------------------------------------------
    // Template version missing (templateId present, version blank/null)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("fails TEMPLATE_VERSION_MISMATCH when templateId present but version is null")
    void evaluate_templateIdPresentVersionNull_failsVersionMismatch() {
        TradeEvent event = eventWith("TMPL-FX-OPT-001", null);

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isFalse();
        assertThat(result.getFailures()).hasSize(1);
        assertThat(result.getFailures().get(0).getSubTheme())
                .isEqualTo(ExceptionSubTheme.TEMPLATE_VERSION_MISMATCH);
        assertThat(result.getFailures().get(0).getAffectedFields())
                .isEqualTo("enrichmentMetadata.resolvedTemplateVersion");
    }

    @Test
    @DisplayName("fails TEMPLATE_VERSION_MISMATCH when templateId present but version is blank")
    void evaluate_templateIdPresentVersionBlank_failsVersionMismatch() {
        TradeEvent event = eventWith("TMPL-FX-OPT-001", "  ");

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isFalse();
        assertThat(result.getFailures().get(0).getSubTheme())
                .isEqualTo(ExceptionSubTheme.TEMPLATE_VERSION_MISMATCH);
    }

    @Test
    @DisplayName("failure reason for version mismatch includes the resolved templateId")
    void evaluate_versionMissing_reasonCodeContainsTemplateId() {
        TradeEvent event = eventWith("TMPL-FX-OPT-001", null);

        rule.evaluate(event, result);

        assertThat(result.getFailures().get(0).getReasonCode())
                .contains("TMPL-FX-OPT-001");
    }

    // -----------------------------------------------------------------------
    // When templateId is null, only NO_MATCHING_TEMPLATE is emitted (not also version mismatch)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("emits only NO_MATCHING_TEMPLATE when templateId is null — does not also emit version mismatch")
    void evaluate_nullTemplateId_onlyOneFailure() {
        TradeEvent event = eventWith(null, null);

        rule.evaluate(event, result);

        assertThat(result.getFailures()).hasSize(1);
        assertThat(result.getFailures().get(0).getSubTheme())
                .isEqualTo(ExceptionSubTheme.NO_MATCHING_TEMPLATE);
    }
}

