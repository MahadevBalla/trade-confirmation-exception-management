package com.tcem.validation.confirmation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tcem.common.constants.ConfirmationStatus;
import com.tcem.common.constants.EventType;
import com.tcem.common.constants.ProductType;
import com.tcem.common.event.ConfirmationEvent;
import com.tcem.common.model.message.MessageHeader;
import com.tcem.common.model.reference.ReferenceDataEnrichment;
import com.tcem.common.model.trade.FxOptionEconomics;
import com.tcem.common.model.trade.NonEconomicTerms;
import com.tcem.common.model.trade.Party;
import com.tcem.common.model.trade.Trade;
import com.tcem.validation.config.ValidationProperties;
import com.tcem.validation.producer.ConfirmationGeneratedProducer;
import com.tcem.validation.repository.ConfirmationCouchDbRepository;
import com.tcem.validation.repository.ConfirmationDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ConfirmationService}.
 *
 * All dependencies are mocked with Mockito — no Spring context required.
 *
 * Coverage:
 * - New trade: generates, persists, and publishes confirmation
 * - Amendment with unchanged confirmable fields: duplicate suppression (returns empty)
 * - Amendment with changed confirmable fields: generates new confirmation, links fingerprints
 * - Confirmation record fields are set correctly (status ALLEGED, generatedBy, etc.)
 * - ConfirmationDocument fields mirror the record
 */
@ExtendWith(MockitoExtension.class)
class ConfirmationServiceTest {

    @Mock
    private ConfirmationCouchDbRepository repository;

    @Mock
    private PlainTextConfirmationGenerator generator;

    @Mock
    private ConfirmationGeneratedProducer producer;

    private ObjectMapper objectMapper;
    private ValidationProperties properties;
    private ConfirmationService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        properties = new ValidationProperties();
        // uses default serviceName = "validation-service"
        service = new ConfirmationService(repository, generator, producer, objectMapper, properties);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Trade buildTrade(String tradeId, int version) {
        Trade trade = new Trade();
        trade.setTradeId(tradeId);
        trade.setTradeVersion(version);
        trade.setProductType(ProductType.FX_OPTION);
        trade.setTradeDate("2026-01-15");
        trade.setEffectiveDate("2026-01-17");
        trade.setMaturityDate("2026-04-17");

        Party bank = new Party();
        bank.setPartyRole("BANK");
        bank.setPartyId("BANK-001");
        bank.setLegalEntityName("Global Bank PLC");

        Party cp = new Party();
        cp.setPartyRole("COUNTERPARTY");
        cp.setPartyId("CP-001");
        cp.setLegalEntityName("Alpha Corp");

        trade.setParties(List.of(bank, cp));

        FxOptionEconomics eco = new FxOptionEconomics();
        eco.setBuySell("BUY");
        eco.setOptionType("CALL");
        eco.setNotionalAmount(new BigDecimal("1000000"));
        eco.setNotionalCurrency("USD");
        eco.setStrike(new BigDecimal("1.2500"));
        eco.setUnderlier("EUR/USD");
        eco.setPremiumAmount(new BigDecimal("25000"));
        eco.setPremiumCurrency("USD");
        eco.setPremiumPaymentDate("2026-01-17");
        trade.setFxOptionEconomics(eco);

        NonEconomicTerms net = new NonEconomicTerms();
        net.setJurisdiction("ENGLISH");
        net.setGoverningLaw("ENGLISH_LAW");
        net.setConfirmationMethod("SWIFT");
        net.setDocumentationVersion("ISDA_2002");
        trade.setNonEconomicTerms(net);

        ReferenceDataEnrichment enrichment = new ReferenceDataEnrichment();
        enrichment.setResolvedTemplateId("TMPL-FX-OPT-001");
        enrichment.setResolvedTemplateVersion("v1.0");
        enrichment.setCounterpartySTPEligible(true);
        enrichment.setProductSTPEligible(true);
        trade.setEnrichmentMetadata(enrichment);

        return trade;
    }

    private MessageHeader buildHeader(String messageId, String correlationId) {
        MessageHeader header = new MessageHeader();
        header.setMessageId(messageId);
        header.setCorrelationId(correlationId);
        header.setEventType(EventType.NEW_TRADE);
        return header;
    }

    /** Stubs repository.save() to echo the document back (simulating CouchDB assign of _rev). */
    private void stubRepositorySave() {
        when(repository.save(any(ConfirmationDocument.class))).thenAnswer(inv -> {
            ConfirmationDocument doc = inv.getArgument(0);
            doc.setRev("1-abc");
            return doc;
        });
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("New trade: generates body, saves to CouchDB, publishes event, returns non-empty Optional")
    void testNewTradeGeneratesAndPublishes() {
        Trade trade = buildTrade("TRD-10001", 1);
        MessageHeader header = buildHeader("MSG-001", "CORR-001");

        when(repository.findLatestForTrade("TRD-10001")).thenReturn(Optional.empty());
        when(generator.generate(eq(trade), eq("TMPL-FX-OPT-001"), eq("v1.0")))
                .thenReturn("CONFIRMATION BODY TEXT");
        stubRepositorySave();
        when(producer.sendConfirmationGenerated(eq("TRD-10001"), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        Optional<ConfirmationEvent> result = service.processConfirmation(header, trade);

        assertThat(result).isPresent();
        ConfirmationEvent event = result.get();
        assertThat(event.getConfirmation()).isNotNull();
        assertThat(event.getConfirmation().getTradeId()).isEqualTo("TRD-10001");
        assertThat(event.getConfirmation().getStatus()).isEqualTo(ConfirmationStatus.ALLEGED);
        assertThat(event.getConfirmation().getGeneratedBy()).isEqualTo("validation-service");
        assertThat(event.getConfirmation().getConfirmationBody()).isEqualTo("CONFIRMATION BODY TEXT");
        assertThat(event.getConfirmation().getTemplateId()).isEqualTo("TMPL-FX-OPT-001");
        assertThat(event.getConfirmation().getTemplateVersion()).isEqualTo("v1.0");
        assertThat(event.getConfirmation().getSourceMessageId()).isEqualTo("MSG-001");
        assertThat(event.getConfirmation().getPreviousConfirmationFingerprint()).isNull();

        verify(repository).findLatestForTrade("TRD-10001");
        verify(generator).generate(eq(trade), eq("TMPL-FX-OPT-001"), eq("v1.0"));
        verify(repository).save(any(ConfirmationDocument.class));
        verify(producer).sendConfirmationGenerated(eq("TRD-10001"), any());
    }

    @Test
    @DisplayName("Amendment with identical confirmable fields: duplicate suppression, returns empty Optional")
    void testAmendTradeWithSameFingerprintReturnEmpty() {
        Trade trade = buildTrade("TRD-10002", 2);
        MessageHeader header = buildHeader("MSG-002", "CORR-002");

        // Compute what the fingerprint WILL be, then store it in the existing doc
        String existingFingerprint = com.tcem.common.registry.ConfirmationFingerprint.compute(trade, objectMapper);

        ConfirmationDocument existingDoc = ConfirmationDocument.builder()
                .confirmationId("CONF-OLD-001")
                .tradeId("TRD-10002")
                .tradeVersion(1)
                .confirmationFingerprint(existingFingerprint)
                .status(ConfirmationStatus.ALLEGED)
                .build();

        when(repository.findLatestForTrade("TRD-10002")).thenReturn(Optional.of(existingDoc));

        Optional<ConfirmationEvent> result = service.processConfirmation(header, trade);

        assertThat(result).isEmpty();
        verify(repository).findLatestForTrade("TRD-10002");
        verify(generator, never()).generate(any(), any(), any());
        verify(repository, never()).save(any());
        verify(producer, never()).sendConfirmationGenerated(any(), any());
    }

    @Test
    @DisplayName("Amendment with changed strike: new fingerprint, generates and publishes new confirmation")
    void testAmendTradeWithChangedFingerprintGeneratesNew() {
        // version 1 trade — compute its fingerprint to store as "previous"
        Trade tradeV1 = buildTrade("TRD-10003", 1);
        String previousFingerprint = com.tcem.common.registry.ConfirmationFingerprint.compute(tradeV1, objectMapper);

        // version 2 trade — change the strike to alter the fingerprint
        Trade tradeV2 = buildTrade("TRD-10003", 2);
        tradeV2.getFxOptionEconomics().setStrike(new BigDecimal("1.3000")); // different

        MessageHeader header = buildHeader("MSG-003", "CORR-003");

        ConfirmationDocument existingDoc = ConfirmationDocument.builder()
                .confirmationId("CONF-V1-001")
                .tradeId("TRD-10003")
                .tradeVersion(1)
                .confirmationFingerprint(previousFingerprint)
                .status(ConfirmationStatus.ALLEGED)
                .build();

        when(repository.findLatestForTrade("TRD-10003")).thenReturn(Optional.of(existingDoc));
        when(generator.generate(eq(tradeV2), eq("TMPL-FX-OPT-001"), eq("v1.0")))
                .thenReturn("AMENDED CONFIRMATION BODY");
        stubRepositorySave();
        when(producer.sendConfirmationGenerated(eq("TRD-10003"), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        Optional<ConfirmationEvent> result = service.processConfirmation(header, tradeV2);

        assertThat(result).isPresent();
        ConfirmationEvent event = result.get();
        assertThat(event.getConfirmation().getTradeId()).isEqualTo("TRD-10003");
        assertThat(event.getConfirmation().getTradeVersion()).isEqualTo(2);
        assertThat(event.getConfirmation().getPreviousConfirmationFingerprint()).isEqualTo(previousFingerprint);
        assertThat(event.getConfirmation().getConfirmationFingerprint())
                .isNotEqualTo(previousFingerprint);

        verify(repository).save(any(ConfirmationDocument.class));
        verify(producer).sendConfirmationGenerated(eq("TRD-10003"), any());
    }

    @Test
    @DisplayName("Outbound MessageHeader carries inbound correlationId")
    void testOutboundHeaderCorrelationId() {
        Trade trade = buildTrade("TRD-10004", 1);
        MessageHeader header = buildHeader("MSG-004", "CORR-XYZ");

        when(repository.findLatestForTrade("TRD-10004")).thenReturn(Optional.empty());
        when(generator.generate(any(), any(), any())).thenReturn("BODY");
        stubRepositorySave();
        when(producer.sendConfirmationGenerated(any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        Optional<ConfirmationEvent> result = service.processConfirmation(header, trade);

        assertThat(result).isPresent();
        assertThat(result.get().getMessageHeader().getCorrelationId()).isEqualTo("CORR-XYZ");
    }

    @Test
    @DisplayName("Saved CouchDB document mirrors confirmation record fields")
    void testSavedDocumentFields() {
        Trade trade = buildTrade("TRD-10005", 1);
        MessageHeader header = buildHeader("MSG-005", "CORR-005");

        when(repository.findLatestForTrade("TRD-10005")).thenReturn(Optional.empty());
        when(generator.generate(any(), any(), any())).thenReturn("BODY TEXT");
        stubRepositorySave();
        when(producer.sendConfirmationGenerated(any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        service.processConfirmation(header, trade);

        ArgumentCaptor<ConfirmationDocument> docCaptor = ArgumentCaptor.forClass(ConfirmationDocument.class);
        verify(repository).save(docCaptor.capture());
        ConfirmationDocument savedDoc = docCaptor.getValue();

        assertThat(savedDoc.getTradeId()).isEqualTo("TRD-10005");
        assertThat(savedDoc.getTradeVersion()).isEqualTo(1);
        assertThat(savedDoc.getTemplateId()).isEqualTo("TMPL-FX-OPT-001");
        assertThat(savedDoc.getTemplateVersion()).isEqualTo("v1.0");
        assertThat(savedDoc.getStatus()).isEqualTo(ConfirmationStatus.ALLEGED);
        assertThat(savedDoc.getConfirmationBody()).isEqualTo("BODY TEXT");
        assertThat(savedDoc.getGeneratedBy()).isEqualTo("validation-service");
        assertThat(savedDoc.getConfirmationId()).isNotBlank();
        assertThat(savedDoc.getConfirmationFingerprint()).isNotBlank();
    }

    @Test
    @DisplayName("Replay identity (eventType, replayCount) survives onto confirmation.generated")
    void replayIdentityPropagated() {
        MessageHeader header = buildHeader("MSG-RPL", "CORR-RPL");
        header.setEventType(EventType.REPLAY);
        header.setReplayCount(2);
        when(repository.findLatestForTrade("TRD-RPL")).thenReturn(Optional.empty());
        when(generator.generate(any(), any(), any())).thenReturn("body");
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MessageHeader outbound = service.processConfirmation(header, buildTrade("TRD-RPL", 1))
                .orElseThrow().getMessageHeader();

        assertThat(outbound.getEventType()).isEqualTo(EventType.REPLAY);
        assertThat(outbound.getReplayCount()).isEqualTo(2);
    }
}
