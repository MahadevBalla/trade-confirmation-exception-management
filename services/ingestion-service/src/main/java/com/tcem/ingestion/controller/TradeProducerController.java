package com.tcem.ingestion.controller;

import com.tcem.ingestion.generator.TradeScenarioType;
import com.tcem.ingestion.service.TradeSeedService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * REST controller for on-demand synthetic trade generation and seed loading (FR1).
 */
@RestController
@RequestMapping("/trades")
public class TradeProducerController {

    private final TradeSeedService tradeSeedService;

    public TradeProducerController(TradeSeedService tradeSeedService) {
        this.tradeSeedService = tradeSeedService;
    }

    /**
     * Generates a batch of synthetic trades and optionally publishes them to trade.raw.
     *
     * @param count          number of trades to produce (default: 500)
     * @param publishToKafka whether to publish to Kafka topic trade.raw (default: false for instant offline preview)
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateTrades(
            @RequestParam(name = "count", defaultValue = "500") int count,
            @RequestParam(name = "publishToKafka", defaultValue = "false") boolean publishToKafka) {
        Map<String, Object> response = tradeSeedService.generateAndPublish(count, publishToKafka);
        return ResponseEntity.ok(response);
    }

    /**
     * Generates a single trade for a specific test scenario.
     *
     * @param scenario       test scenario to generate
     * @param sequence       sequence number for IDs
     * @param publishToKafka whether to publish to Kafka topic trade.raw (default: false for instant offline preview)
     */
    @PostMapping("/generate/scenario")
    public ResponseEntity<Map<String, Object>> generateScenario(
            @RequestParam(name = "scenario") TradeScenarioType scenario,
            @RequestParam(name = "sequence", defaultValue = "1") int sequence,
            @RequestParam(name = "publishToKafka", defaultValue = "false") boolean publishToKafka) {
        Map<String, Object> response = tradeSeedService.generateSingleScenario(scenario, sequence, publishToKafka);
        return ResponseEntity.ok(response);
    }

    /**
     * Loads the pre-packaged seed dataset and optionally publishes to trade.raw.
     *
     * @param publishToKafka whether to publish to Kafka topic trade.raw (default: false for instant offline preview)
     */
    @PostMapping("/seed")
    public ResponseEntity<Map<String, Object>> seedTrades(
            @RequestParam(name = "publishToKafka", defaultValue = "false") boolean publishToKafka,
            @RequestParam(name = "force", defaultValue = "false") boolean force) {
        Map<String, Object> response = tradeSeedService.loadSeedData(publishToKafka, force);
        return ResponseEntity.ok(response);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(com.tcem.ingestion.service.SeedAlreadyLoadedException.class)
    public ResponseEntity<Map<String, Object>> seedAlreadyLoaded(com.tcem.ingestion.service.SeedAlreadyLoadedException ex) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).body(Map.of("status", 409, "error", ex.getMessage()));
    }

    /**
     * Lists all available synthetic trade test scenarios.
     */
    @GetMapping("/scenarios")
    public ResponseEntity<List<String>> listScenarios() {
        List<String> scenarios = Arrays.stream(TradeScenarioType.values())
                .map(Enum::name)
                .toList();
        return ResponseEntity.ok(scenarios);
    }
}
