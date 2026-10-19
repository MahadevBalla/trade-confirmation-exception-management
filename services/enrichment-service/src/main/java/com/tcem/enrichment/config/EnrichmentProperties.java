package com.tcem.enrichment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("enrichment.reference-data")
public class EnrichmentProperties {
    private String counterparties;
    private String instruments;
    private String templates;
    private String counterpartyProducts;
    public String getCounterparties() { return counterparties; }
    public void setCounterparties(String counterparties) { this.counterparties = counterparties; }
    public String getInstruments() { return instruments; }
    public void setInstruments(String instruments) { this.instruments = instruments; }
    public String getTemplates() { return templates; }
    public void setTemplates(String templates) { this.templates = templates; }
    public String getCounterpartyProducts() { return counterpartyProducts; }
    public void setCounterpartyProducts(String counterpartyProducts) { this.counterpartyProducts = counterpartyProducts; }
}
