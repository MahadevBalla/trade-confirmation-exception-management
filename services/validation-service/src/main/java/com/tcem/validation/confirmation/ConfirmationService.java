package com.tcem.validation.confirmation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.ConfirmationStatus;
import com.tcem.common.event.ConfirmationEvent;
import com.tcem.common.model.confirmation.ConfirmationRecord;
import com.tcem.common.model.message.MessageHeader;
import com.tcem.common.model.message.SchemaVersions;
import com.tcem.common.model.reference.ReferenceDataEnrichment;
import com.tcem.common.model.trade.Trade;
import com.tcem.common.registry.ConfirmationFingerprint;
import com.tcem.validation.config.ValidationProperties;
import com.tcem.validation.producer.ConfirmationGeneratedProducer;
import com.tcem.validation.repository.ConfirmationCouchDbRepository;
import com.tcem.validation.repository.ConfirmationDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the full confirmation lifecycle for a successfully STP-validated trade (FR6).
 *
 * <h3>Responsibilities</h3>
 * <ol>
 *   <li>Compute the {@link ConfirmationFingerprint} over the trade's confirmable fields.</li>
 *   <li>On an {@code AMEND_TRADE}: compare the new fingerprint against the stored one.
 *       If they are equal the confirmable fields did not change, so no new confirmation
 *       is emitted (duplicate-suppression rule).</li>
 *   <li>Generate the plain-text confirmation body via {@link PlainTextConfirmationGenerator}.</li>
 *   <li>Persist a {@link ConfirmationDocument} to CouchDB.</li>
 *   <li>Build a {@link ConfirmationRecord} and {@link ConfirmationEvent}.</li>
 *   <li>Publish the event to the {@code confirmation.generated} Kafka topic via
 *       {@link ConfirmationGeneratedProducer}.</li>
 *   <li>Return the event so the calling orchestrator can correlate the result.</li>
 * </ol>
 *
 * <h3>Thread safety</h3>
 * Stateless Spring {@code @Service}; safe to share across threads.
 */
@Service
public class ConfirmationService {

    private static final Logger log = LoggerFactory.getLogger(ConfirmationService.class);

    private final ConfirmationCouchDbRepository repository;
    private final PlainTextConfirmationGenerator generator;
    private final ConfirmationGeneratedProducer producer;
    private final ObjectMapper objectMapper;
    private final ValidationProperties properties;

    public ConfirmationService(ConfirmationCouchDbRepository repository,
                               PlainTextConfirmationGenerator generator,
                               ConfirmationGeneratedProducer producer,
                               ObjectMapper objectMapper,
                               ValidationProperties properties) {
        this.repository = repository;
        this.generator = generator;
        this.producer = producer;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * Processes a confirmation for the given enriched trade.
     *
     * @param header message envelope header from the incoming Kafka message
     * @param trade  fully enriched trade that has already passed STP validation
     * @return {@link Optional} containing the published {@link ConfirmationEvent},
     *         or {@link Optional#empty()} when duplicate suppression applies
     */
    public Optional<ConfirmationEvent> processConfirmation(MessageHeader header, Trade trade) {
        String tradeId = trade.getTradeId();
        int tradeVersion = trade.getTradeVersion();

        // -----------------------------------------------------------------------
        // Step 1: Compute fingerprint over this trade version's confirmable fields
        // -----------------------------------------------------------------------
        String newFingerprint = ConfirmationFingerprint.compute(trade, objectMapper);
        log.debug("Computed fingerprint for tradeId={} version={}: {}", tradeId, tradeVersion, newFingerprint);

        // -----------------------------------------------------------------------
        // Step 2: Duplicate-suppression check (AMEND_TRADE only)
        //         Fetch the most recent existing confirmation and compare fingerprints.
        // -----------------------------------------------------------------------
        String previousFingerprint = null;
        Optional<ConfirmationDocument> latestOpt = repository.findLatestForTrade(tradeId);
        if (latestOpt.isPresent()) {
            previousFingerprint = latestOpt.get().getConfirmationFingerprint();
            if (newFingerprint.equals(previousFingerprint)) {
                log.info("Duplicate-suppression triggered: confirmable fields unchanged for tradeId={} version={}. " +
                         "Existing fingerprint={}. No new confirmation emitted.",
                         tradeId, tradeVersion, previousFingerprint);
                return Optional.empty();
            }
            log.debug("Fingerprint changed for tradeId={}: previous={}, new={}", tradeId, previousFingerprint, newFingerprint);
        }

        // -----------------------------------------------------------------------
        // Step 3: Resolve template identifiers from enrichment metadata
        // -----------------------------------------------------------------------
        ReferenceDataEnrichment enrichment = trade.getEnrichmentMetadata();
        String templateId = enrichment != null ? enrichment.getResolvedTemplateId() : null;
        String templateVersion = enrichment != null ? enrichment.getResolvedTemplateVersion() : null;

        // -----------------------------------------------------------------------
        // Step 4: Generate the plain-text confirmation body
        // -----------------------------------------------------------------------
        String confirmationBody = generator.generate(trade, templateId, templateVersion);

        // -----------------------------------------------------------------------
        // Step 5: Build and persist the CouchDB document
        // -----------------------------------------------------------------------
        String confirmationId = "CONF-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String generatedBy = properties.getServiceName();
        Instant now = Instant.now();

        ConfirmationDocument document = ConfirmationDocument.builder()
                .id(confirmationId)
                .confirmationId(confirmationId)
                .tradeId(tradeId)
                .tradeVersion(tradeVersion)
                .sourceMessageId(header.getMessageId())
                .templateId(templateId)
                .templateVersion(templateVersion)
                .status(ConfirmationStatus.ALLEGED)
                .confirmationFingerprint(newFingerprint)
                .previousConfirmationFingerprint(previousFingerprint)
                .confirmationBody(confirmationBody)
                .generatedAt(now)
                .generatedBy(generatedBy)
                .build();

        ConfirmationDocument saved = repository.save(document);
        log.info("Persisted confirmation [confirmationId={}, tradeId={}, version={}, fingerprint={}]",
                confirmationId, tradeId, tradeVersion, newFingerprint);

        // -----------------------------------------------------------------------
        // Step 6: Build ConfirmationRecord and ConfirmationEvent
        // -----------------------------------------------------------------------
        ConfirmationRecord record = new ConfirmationRecord();
        record.setConfirmationId(saved.getConfirmationId());
        record.setTradeId(tradeId);
        record.setTradeVersion(tradeVersion);
        record.setSourceMessageId(header.getMessageId());
        record.setTemplateId(templateId);
        record.setTemplateVersion(templateVersion);
        record.setStatus(ConfirmationStatus.ALLEGED);
        record.setConfirmationFingerprint(newFingerprint);
        record.setPreviousConfirmationFingerprint(previousFingerprint);
        record.setConfirmationBody(confirmationBody);
        record.setGeneratedAt(now);
        record.setGeneratedBy(generatedBy);

        MessageHeader outboundHeader = new MessageHeader();
        outboundHeader.setMessageId("MSG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        outboundHeader.setCorrelationId(header.getCorrelationId());
        outboundHeader.setEventType(header.getEventType());
        outboundHeader.setReplayCount(header.getReplayCount());
        outboundHeader.setSchemaVersion(SchemaVersions.CURRENT);
        outboundHeader.setCreatedTimestamp(now);
        outboundHeader.setSourceService(generatedBy);

        ConfirmationEvent event = new ConfirmationEvent(outboundHeader, record);

        // -----------------------------------------------------------------------
        // Step 7: Publish to confirmation.generated topic
        // -----------------------------------------------------------------------
        producer.sendConfirmationGenerated(tradeId, event);
        log.info("Published confirmation.generated event [confirmationId={}, tradeId={}, version={}]",
                confirmationId, tradeId, tradeVersion);

        return Optional.of(event);
    }
}

