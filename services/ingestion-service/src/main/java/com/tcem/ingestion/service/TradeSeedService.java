package com.tcem.ingestion.service;

import com.tcem.ingestion.generator.TradeScenarioType;

import java.util.Map;

/**
 * Interface defining synthetic trade generation and seed loading operations.
 */
public interface TradeSeedService {

    /**
     * Generates a batch of synthetic trades and optionally publishes them to trade.raw.
     *
     * @param count          number of trades to generate
     * @param publishToKafka whether to dispatch to Kafka topic trade.raw
     * @return summary map containing generation stats
     */
    Map<String, Object> generateAndPublish(int count, boolean publishToKafka);

    default Map<String, Object> generateAndPublish(int count) {
        return generateAndPublish(count, true);
    }

    /**
     * Generates a single trade of a specific test scenario and optionally publishes it to trade.raw.
     *
     * @param scenario       test scenario to generate
     * @param sequence       sequence number for IDs
     * @param publishToKafka whether to dispatch to Kafka topic trade.raw
     * @return summary map containing scenario info and payload
     */
    Map<String, Object> generateSingleScenario(TradeScenarioType scenario, int sequence, boolean publishToKafka);

    default Map<String, Object> generateSingleScenario(TradeScenarioType scenario, int sequence) {
        return generateSingleScenario(scenario, sequence, true);
    }

    /**
     * Loads trades from the seed file and optionally publishes to trade.raw.
     *
     * @param publishToKafka whether to dispatch to Kafka topic trade.raw
     * @param force          publish even if the seed data is already loaded
     * @return summary map
     * @throws SeedAlreadyLoadedException if publishing would repeat the seed and force is false
     */
    Map<String, Object> loadSeedData(boolean publishToKafka, boolean force);

    default Map<String, Object> loadSeedData() {
        return loadSeedData(true, false);
    }
}
