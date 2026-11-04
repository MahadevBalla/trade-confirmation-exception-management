package com.tcem.ingestion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.EventType;
import com.tcem.common.constants.ProcessingStatus;
import com.tcem.common.constants.TopicNames;
import com.tcem.common.model.trade.Trade;
import com.tcem.ingestion.model.document.TradeDocument;
import com.tcem.ingestion.repository.TradeCouchDbRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps the {@code trades} read model current as a trade moves through the pipeline (FR3, FR11): its processing
 * status, and the enriched version of the trade once enrichment has run.
 * <p>
 * Kafka orders records within a partition only, so events from different topics can arrive in any order. A status
 * therefore only moves forward by {@link #RANK}: a late {@code stp.validated} cannot undo {@code CONFIRMED}.
 * {@code CANCELLED} is final, and a replayed message may bring a trade back from {@code DEAD_LETTER}.
 */
@Service
public class TradeLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(TradeLifecycleService.class);

    /** Kafka keys ingestion and enrichment use when a message has no trade id. */
    private static final List<String> PLACEHOLDER_KEY_PREFIXES = List.of("UNKNOWN_KEY_", "DLQ_", "DLQ-");

    static final Map<ProcessingStatus, Integer> RANK = Map.of(
            ProcessingStatus.ACCEPTED, 1,
            ProcessingStatus.ENRICHED, 2,
            ProcessingStatus.STP_PASSED, 3,
            ProcessingStatus.STP_FAILED, 3,
            ProcessingStatus.EXCEPTION_CAPTURED, 4,
            ProcessingStatus.SCHEMA_INVALID, 5,
            ProcessingStatus.MANUAL_REVIEW, 6,
            ProcessingStatus.DEAD_LETTER, 7,
            ProcessingStatus.CONFIRMED, 8,
            ProcessingStatus.CANCELLED, 9);

    private final TradeCouchDbRepository tradeRepository;
    private final ObjectMapper objectMapper;

    public TradeLifecycleService(TradeCouchDbRepository tradeRepository, ObjectMapper objectMapper) {
        this.tradeRepository = tradeRepository;
        this.objectMapper = objectMapper;
    }

    /** Applies one pipeline event. Records that do not concern a known trade are ignored. */
    public void onPipelineEvent(String topic, String key, String value) {
        JsonNode root;
        try {
            root = objectMapper.readTree(value);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            log.debug("Ignoring unparseable record on '{}' [key='{}']", topic, key);
            return;
        }
        if (root == null || isPlaceholder(key)) {
            return;
        }
        Optional<TradeDocument> found = tradeRepository.findById(key);
        if (found.isEmpty()) {
            log.debug("No trade '{}' for record on '{}'", key, topic);
            return;
        }

        TradeDocument trade = found.get();
        boolean changed = false;
        if (TopicNames.TRADE_ENRICHED.equals(topic) && root.hasNonNull("trade")) {
            try {
                trade.setCanonicalTrade(objectMapper.treeToValue(root.get("trade"), Trade.class));
                changed = true;
            } catch (JsonProcessingException ex) {
                log.warn("Could not read enriched trade '{}': {}", key, ex.getMessage());
            }
        }

        ProcessingStatus next = statusFor(topic, root);
        if (next != null && canMove(trade.getProcessingStatus(), next, isReplay(root))) {
            trade.setProcessingStatus(next);
            changed = true;
        }
        if (changed) {
            tradeRepository.save(trade);
        }
    }

    static ProcessingStatus statusFor(String topic, JsonNode root) {
        return switch (topic) {
            case TopicNames.TRADE_ENRICHED -> ProcessingStatus.ENRICHED;
            case TopicNames.STP_VALIDATED -> root.path("cancelled").asBoolean(false) ? ProcessingStatus.CANCELLED
                    : root.path("stpPassed").asBoolean(false) ? ProcessingStatus.STP_PASSED : ProcessingStatus.STP_FAILED;
            case TopicNames.EXCEPTION_CREATED -> ProcessingStatus.EXCEPTION_CAPTURED;
            case TopicNames.MANUAL_REVIEW_REQUIRED -> ProcessingStatus.MANUAL_REVIEW;
            case TopicNames.CONFIRMATION_GENERATED -> ProcessingStatus.CONFIRMED;
            case TopicNames.DEADLETTER_TRADE -> ProcessingStatus.DEAD_LETTER;
            default -> null;
        };
    }

    static boolean canMove(ProcessingStatus current, ProcessingStatus next, boolean replay) {
        if (current == null) {
            return true;
        }
        if (current == ProcessingStatus.CANCELLED) {
            return false;
        }
        if (current == ProcessingStatus.DEAD_LETTER && replay) {
            return true;
        }
        return RANK.getOrDefault(next, 0) > RANK.getOrDefault(current, 0);
    }

    private static boolean isReplay(JsonNode root) {
        JsonNode header = root.path("messageHeader");
        return EventType.REPLAY.name().equals(header.path("eventType").asText()) || header.path("replayCount").asInt(0) > 0;
    }

    private static boolean isPlaceholder(String key) {
        return key == null || key.isBlank() || PLACEHOLDER_KEY_PREFIXES.stream().anyMatch(key::startsWith);
    }
}
