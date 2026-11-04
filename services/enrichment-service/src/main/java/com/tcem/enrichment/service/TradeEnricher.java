package com.tcem.enrichment.service;

import com.tcem.common.model.message.TradeEvent;
import com.tcem.common.model.reference.EnrichmentSource;
import com.tcem.common.model.reference.ReferenceDataEnrichment;
import com.tcem.common.model.trade.Party;
import com.tcem.common.model.trade.Trade;
import com.tcem.enrichment.reference.CounterpartyReference;
import com.tcem.enrichment.reference.CounterpartyProductReference;
import com.tcem.enrichment.reference.InstrumentReference;
import com.tcem.enrichment.reference.ReferenceDataCatalog;
import com.tcem.enrichment.reference.TemplateReference;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Applies only the documented FR4 fields; all inbound economics and metadata remain intact. */
@Service
public class TradeEnricher {
    private static final String COUNTERPARTY_SOURCE = "counterparties.json";
    private static final String INSTRUMENT_SOURCE = "instruments.json";
    private static final String TEMPLATE_SOURCE = "templates.json";
    private static final String COUNTERPARTY_PRODUCT_SOURCE = "counterparty-products.json";
    private final ReferenceDataCatalog catalog;

    public TradeEnricher(ReferenceDataCatalog catalog) { this.catalog = catalog; }

    public TradeEvent enrich(TradeEvent event) {
        if (event == null || event.getTrade() == null) {
            throw new IllegalArgumentException("trade.accepted event must contain a trade");
        }
        Trade trade = event.getTrade();
        Instant lookupAt = Instant.now();
        Map<String, EnrichmentSource> provenance = new LinkedHashMap<>();
        List<String> unresolved = new java.util.ArrayList<>();
        Boolean counterpartyEligible = enrichParties(trade.getParties(), lookupAt, provenance, unresolved);
        enrichInstrument(trade, lookupAt, provenance, unresolved);
        boolean templateResolved = enrichTemplate(trade, lookupAt, provenance, unresolved);
        Boolean productEligible = enrichProductEligibility(trade, lookupAt, provenance, unresolved);

        ReferenceDataEnrichment metadata = new ReferenceDataEnrichment();
        metadata.setCounterpartySTPEligible(counterpartyEligible);
        metadata.setProductSTPEligible(productEligible);
        if (!templateResolved) {
            metadata.setResolvedTemplateId(null);
            metadata.setResolvedTemplateVersion(null);
        } else {
            TemplateReference template = catalog.template(trade.getProductType().name(), trade.getNonEconomicTerms().getTemplateHint());
            metadata.setResolvedTemplateId(template.templateId());
            metadata.setResolvedTemplateVersion(template.templateVersion());
        }
        metadata.setFieldProvenance(provenance);
        if (!unresolved.isEmpty()) {
            metadata.setEnrichmentNotes("Unresolved demo reference data: " + String.join(", ", unresolved));
        }
        trade.setEnrichmentMetadata(metadata);
        return event;
    }

    private Boolean enrichParties(List<Party> parties, Instant at, Map<String, EnrichmentSource> provenance, List<String> unresolved) {
        if (parties == null || parties.isEmpty()) {
            unresolved.add("party list");
            return null;
        }
        Boolean counterpartyEligible = null;
        for (Party party : parties) {
            CounterpartyReference ref = catalog.counterparty(party.getPartyId());
            if (ref == null) {
                unresolved.add("counterparty " + party.getPartyId());
                continue;
            }
            party.setLegalEntityName(ref.name());
            party.setLei(ref.lei());
            party.setCountry(countryFromJurisdiction(ref.jurisdiction()));
            provenance.put("party." + party.getPartyId() + ".legalEntityName", source(COUNTERPARTY_SOURCE, at, "SUPPLIED"));
            provenance.put("party." + party.getPartyId() + ".lei", source(COUNTERPARTY_SOURCE, at, "SUPPLIED"));
            provenance.put("party." + party.getPartyId() + ".country", source(COUNTERPARTY_SOURCE, at, "DERIVED"));
            if ("COUNTERPARTY".equalsIgnoreCase(party.getPartyRole())) {
                counterpartyEligible = ref.stpEligible();
                provenance.put("counterpartySTPEligible", source(COUNTERPARTY_SOURCE, at, "SUPPLIED"));
            }
        }
        return counterpartyEligible;
    }

    private void enrichInstrument(Trade trade, Instant at, Map<String, EnrichmentSource> provenance, List<String> unresolved) {
        if (trade.getFxOptionEconomics() == null) {
            unresolved.add("FX option economics");
            return;
        }
        if (trade.getFxOptionEconomics().getUnderlier() == null) {
            // Reference maps reject null keys. EconomicFieldsRule raises the missing underlier at STP.
            unresolved.add("instrument underlier");
            return;
        }
        InstrumentReference ref = catalog.instrument(trade.getFxOptionEconomics().getUnderlier());
        if (ref == null) {
            unresolved.add("instrument " + trade.getFxOptionEconomics().getUnderlier());
            return;
        }
        trade.getFxOptionEconomics().setInternalPricingRef(ref.internalPricingRef());
        provenance.put("fxOptionEconomics.internalPricingRef", source(INSTRUMENT_SOURCE, at, "DERIVED"));
    }

    private Boolean enrichProductEligibility(Trade trade, Instant at, Map<String, EnrichmentSource> provenance, List<String> unresolved) {
        String counterpartyId = trade.getParties() == null ? null : trade.getParties().stream()
                .filter(party -> "COUNTERPARTY".equalsIgnoreCase(party.getPartyRole()))
                .map(Party::getPartyId)
                .findFirst().orElse(null);
        if (counterpartyId == null || trade.getProductType() == null) {
            unresolved.add("product configuration");
            return null;
        }
        CounterpartyProductReference reference = catalog.counterpartyProduct(counterpartyId, trade.getProductType().name());
        if (reference == null) {
            unresolved.add("product configuration " + counterpartyId + ":" + trade.getProductType());
            return null;
        }
        provenance.put("productSTPEligible", source(COUNTERPARTY_PRODUCT_SOURCE, at, "SUPPLIED"));
        return reference.stpEligible();
    }

    private boolean enrichTemplate(Trade trade, Instant at, Map<String, EnrichmentSource> provenance, List<String> unresolved) {
        if (trade.getProductType() == null || trade.getNonEconomicTerms() == null || trade.getNonEconomicTerms().getTemplateHint() == null) {
            unresolved.add("template mapping");
            return false;
        }
        TemplateReference ref = catalog.template(trade.getProductType().name(), trade.getNonEconomicTerms().getTemplateHint());
        if (ref == null) {
            unresolved.add("template " + trade.getNonEconomicTerms().getTemplateHint());
            return false;
        }
        provenance.put("resolvedTemplateId", source(TEMPLATE_SOURCE, at, "SUPPLIED"));
        provenance.put("resolvedTemplateVersion", source(TEMPLATE_SOURCE, at, "SUPPLIED"));
        return true;
    }

    private String countryFromJurisdiction(String jurisdiction) {
        int separator = jurisdiction == null ? -1 : jurisdiction.lastIndexOf('-');
        return separator < 0 ? null : jurisdiction.substring(separator + 1);
    }

    private EnrichmentSource source(String name, Instant at, String method) {
        return new EnrichmentSource(name, at, method, "demo-1");
    }
}
