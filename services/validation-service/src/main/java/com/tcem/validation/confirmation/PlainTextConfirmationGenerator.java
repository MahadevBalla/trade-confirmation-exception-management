package com.tcem.validation.confirmation;

import com.tcem.common.model.trade.FxOptionEconomics;
import com.tcem.common.model.trade.NonEconomicTerms;
import com.tcem.common.model.trade.Party;
import com.tcem.common.model.trade.Trade;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Generates a deterministic, monospace-friendly plain-text confirmation body for a given trade
 * (FR6 — Confirmation Generator).
 *
 * <h3>Format</h3>
 * The output is a structured text block with clearly labelled sections:
 * <ul>
 *   <li>Header — trade identifiers and dates</li>
 *   <li>Parties — BANK and COUNTERPARTY names and IDs</li>
 *   <li>Economics — FX Option fields sourced from {@link FxOptionEconomics}</li>
 *   <li>Non-Economic Terms — jurisdiction, governing law, etc.</li>
 *   <li>Template Reference — resolved template ID and version</li>
 * </ul>
 *
 * <h3>Design choices</h3>
 * <ul>
 *   <li>Stateless and thread-safe — suitable for singleton injection.</li>
 *   <li>Null-safe: missing optional fields are rendered as {@code N/A}.</li>
 *   <li>The output is deterministic: given the same trade, the same text is always produced.
 *       This property is important for fingerprint stability.</li>
 * </ul>
 */
@Component
public class PlainTextConfirmationGenerator {

    private static final String NA = "N/A";
    private static final String LINE = "=".repeat(60);
    private static final String DIVIDER = "-".repeat(60);

    /**
     * Renders the plain-text confirmation body.
     *
     * @param trade       the fully enriched trade
     * @param templateId  the resolved template identifier
     * @param templateVersion the resolved template version string
     * @return the confirmation body as a UTF-8 plain-text string
     */
    public String generate(Trade trade, String templateId, String templateVersion) {
        StringBuilder sb = new StringBuilder();

        appendLine(sb, LINE);
        appendLine(sb, "          TRADE CONFIRMATION");
        appendLine(sb, LINE);
        appendLine(sb, "");

        appendSection(sb, "TRADE DETAILS");
        appendField(sb, "Trade ID",         trade.getTradeId());
        appendField(sb, "Trade Version",    String.valueOf(trade.getTradeVersion()));
        appendField(sb, "Trade Date",       nvl(trade.getTradeDate()));
        appendField(sb, "Effective Date",   nvl(trade.getEffectiveDate()));
        appendField(sb, "Maturity Date",    nvl(trade.getMaturityDate()));
        appendField(sb, "Product Type",
                trade.getProductType() != null ? trade.getProductType().name() : NA);
        appendLine(sb, "");

        appendSection(sb, "PARTIES");
        List<Party> parties = trade.getParties();
        if (parties != null) {
            for (Party party : parties) {
                appendField(sb, nvl(party.getPartyRole()), formatParty(party));
            }
        } else {
            appendField(sb, "Parties", NA);
        }
        appendLine(sb, "");

        FxOptionEconomics eco = trade.getFxOptionEconomics();
        appendSection(sb, "FX OPTION ECONOMICS");
        if (eco != null) {
            appendField(sb, "Buy / Sell",            nvl(eco.getBuySell()));
            appendField(sb, "Option Type",           nvl(eco.getOptionType()));
            appendField(sb, "Notional Amount",
                    eco.getNotionalAmount() != null ? eco.getNotionalAmount().toPlainString() : NA);
            appendField(sb, "Notional Currency",     nvl(eco.getNotionalCurrency()));
            appendField(sb, "Strike",
                    eco.getStrike() != null ? eco.getStrike().toPlainString() : NA);
            appendField(sb, "Underlier",             nvl(eco.getUnderlier()));
            appendField(sb, "Premium Amount",
                    eco.getPremiumAmount() != null ? eco.getPremiumAmount().toPlainString() : NA);
            appendField(sb, "Premium Currency",      nvl(eco.getPremiumCurrency()));
            appendField(sb, "Premium Payment Date",  nvl(eco.getPremiumPaymentDate()));
        } else {
            appendField(sb, "Economics", NA);
        }
        appendLine(sb, "");

        NonEconomicTerms net = trade.getNonEconomicTerms();
        appendSection(sb, "NON-ECONOMIC TERMS");
        if (net != null) {
            appendField(sb, "Jurisdiction",           nvl(net.getJurisdiction()));
            appendField(sb, "Governing Law",          nvl(net.getGoverningLaw()));
            appendField(sb, "Confirmation Method",    nvl(net.getConfirmationMethod()));
            appendField(sb, "Documentation Version",  nvl(net.getDocumentationVersion()));
        } else {
            appendField(sb, "Non-Economic Terms", NA);
        }
        appendLine(sb, "");

        appendSection(sb, "TEMPLATE REFERENCE");
        appendField(sb, "Template ID",      nvl(templateId));
        appendField(sb, "Template Version", nvl(templateVersion));
        appendLine(sb, "");

        appendLine(sb, LINE);
        appendLine(sb, "This confirmation is computer-generated by the TCEM platform.");
        appendLine(sb, LINE);

        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void appendSection(StringBuilder sb, String title) {
        appendLine(sb, DIVIDER);
        appendLine(sb, "  " + title);
        appendLine(sb, DIVIDER);
    }

    private void appendField(StringBuilder sb, String label, String value) {
        // Left-pad the label to 24 characters for alignment
        String paddedLabel = String.format("  %-24s", label + ":");
        sb.append(paddedLabel).append(value).append('\n');
    }

    private void appendLine(StringBuilder sb, String line) {
        sb.append(line).append('\n');
    }

    private String formatParty(Party party) {
        StringBuilder sb = new StringBuilder();
        if (party.getLegalEntityName() != null) {
            sb.append(party.getLegalEntityName());
        }
        if (party.getPartyId() != null) {
            if (sb.length() > 0) sb.append(" (");
            sb.append(party.getPartyId());
            if (party.getLegalEntityName() != null) sb.append(")");
        }
        if (party.getLei() != null) {
            sb.append(" [LEI: ").append(party.getLei()).append("]");
        }
        return sb.length() > 0 ? sb.toString() : NA;
    }

    private String nvl(String value) {
        return value != null && !value.isBlank() ? value : NA;
    }
}

