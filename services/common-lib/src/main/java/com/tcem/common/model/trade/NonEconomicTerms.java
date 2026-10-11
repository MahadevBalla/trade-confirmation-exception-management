package com.tcem.common.model.trade;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Non-economic terms of a trade.
 * <p>
 * These terms appear in the confirmation document but are not product economics.
 * Some are confirmable (both parties must agree on them); others are processing
 * metadata. The confirmable set is declared in
 * {@link com.tcem.common.registry.ConfirmableFieldRegistry}.
 * </p>
 *
 * <h3>Confirmable non-economic fields</h3>
 * jurisdiction, confirmationMethod, governingLaw, documentationVersion
 *
 * <h3>Non-confirmable non-economic fields</h3>
 * templateHint, businessUnit, internalRoutingCode
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NonEconomicTerms {

    // -----------------------------------------------------------------------
    // Confirmable
    // -----------------------------------------------------------------------

    /**
     * Legal jurisdiction under which the trade is governed.
     * Example: {@code GENERIC}, {@code ENGLISH_LAW}, {@code NEW_YORK_LAW}
     */
    private String jurisdiction;

    /**
     * How the confirmation will be exchanged.
     * Values: {@code ELECTRONIC}, {@code TELEX}, {@code PAPER}
     */
    private String confirmationMethod;

    /**
     * Governing law clause. Defaults to the value of {@code jurisdiction} if not
     * separately specified.
     */
    private String governingLaw;

    /**
     * Version of the ISDA/documentation framework governing this trade.
     * Example: {@code ISDA_2002}, {@code ISDA_2006}
     */
    private String documentationVersion;

    // -----------------------------------------------------------------------
    // Non-confirmable
    // -----------------------------------------------------------------------

    /**
     * Template hint used by the enrichment-service to look up the confirmation
     * template catalogue. NOT included in the confirmation document.
     * Example: {@code FX_OPTION_STANDARD}
     */
    private String templateHint;

    /**
     * Internal business-unit code. NOT confirmable.
     * Used for routing and SLA assignment.
     */
    private String businessUnit;

    /**
     * Internal routing code for operations queues. NOT confirmable.
     */
    private String internalRoutingCode;

    public String getJurisdiction() {
        return jurisdiction;
    }

    public void setJurisdiction(String jurisdiction) {
        this.jurisdiction = jurisdiction;
    }

    public String getConfirmationMethod() {
        return confirmationMethod;
    }

    public void setConfirmationMethod(String confirmationMethod) {
        this.confirmationMethod = confirmationMethod;
    }

    public String getGoverningLaw() {
        return governingLaw;
    }

    public void setGoverningLaw(String governingLaw) {
        this.governingLaw = governingLaw;
    }

    public String getDocumentationVersion() {
        return documentationVersion;
    }

    public void setDocumentationVersion(String documentationVersion) {
        this.documentationVersion = documentationVersion;
    }

    public String getTemplateHint() {
        return templateHint;
    }

    public void setTemplateHint(String templateHint) {
        this.templateHint = templateHint;
    }

    public String getBusinessUnit() {
        return businessUnit;
    }

    public void setBusinessUnit(String businessUnit) {
        this.businessUnit = businessUnit;
    }

    public String getInternalRoutingCode() {
        return internalRoutingCode;
    }

    public void setInternalRoutingCode(String internalRoutingCode) {
        this.internalRoutingCode = internalRoutingCode;
    }
}
