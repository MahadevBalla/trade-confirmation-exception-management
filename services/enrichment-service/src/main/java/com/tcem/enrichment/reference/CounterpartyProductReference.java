package com.tcem.enrichment.reference;

/** Demo-only configuration for whether a product is eligible for STP at a counterparty. */
public record CounterpartyProductReference(String counterpartyId, String productType, boolean stpEligible) {}
