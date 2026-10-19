package com.tcem.enrichment.reference;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.enrichment.config.EnrichmentProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Loads deterministic, demo-only reference data packaged with this service. */
@Component
public class ReferenceDataCatalog {
    private final ObjectMapper objectMapper;
    private final EnrichmentProperties properties;
    private Map<String, CounterpartyReference> counterparties;
    private Map<String, InstrumentReference> instruments;
    private Map<String, TemplateReference> templates;
    private Map<String, CounterpartyProductReference> counterpartyProducts;

    public ReferenceDataCatalog(ObjectMapper objectMapper, EnrichmentProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @PostConstruct
    void load() throws IOException {
        counterparties = byKey(read(properties.getCounterparties(), new TypeReference<List<CounterpartyReference>>() {}), CounterpartyReference::id);
        instruments = byKey(read(properties.getInstruments(), new TypeReference<List<InstrumentReference>>() {}), InstrumentReference::underlier);
        templates = byKey(read(properties.getTemplates(), new TypeReference<List<TemplateReference>>() {}),
                entry -> entry.productType() + ":" + entry.templateHint());
        counterpartyProducts = byKey(read(properties.getCounterpartyProducts(), new TypeReference<List<CounterpartyProductReference>>() {}),
                entry -> entry.counterpartyId() + ":" + entry.productType());
    }

    public CounterpartyReference counterparty(String partyId) { return counterparties.get(partyId); }
    public InstrumentReference instrument(String underlier) { return instruments.get(underlier); }
    public TemplateReference template(String productType, String templateHint) {
        return templates.get(productType + ":" + templateHint);
    }
    public CounterpartyProductReference counterpartyProduct(String counterpartyId, String productType) {
        return counterpartyProducts.get(counterpartyId + ":" + productType);
    }

    private <T> List<T> read(String path, TypeReference<List<T>> type) throws IOException {
        return objectMapper.readValue(new ClassPathResource(path).getInputStream(), type);
    }

    private <T> Map<String, T> byKey(List<T> values, Function<T, String> key) {
        return values.stream().collect(Collectors.toUnmodifiableMap(key, Function.identity()));
    }
}
