package com.tcem.ingestion.service;

import com.tcem.ingestion.generator.TradeScenarioType;

import java.util.Map;

/**
 * Interface defining synthetic trade generation and seed loading operations.
 */
public interface TradeSeedService {

    /**
     * Generates a batch of synthetic trades and publishes them to trade.raw.
     *
     * @param count number of trades to generate
     * @return summary map containing generation stats
     */
    Map<String, Object> generateAndPublish(int count);

    /**
     * Generates and publishes a single trade of a specific test scenario.
     *
     * @param scenario test scenario to generate
     * @param sequence sequence number for IDs
     * @return summary map
     */
    Map<String, Object> generateSingleScenario(TradeScenarioType scenario, int sequence);

    /**
     * Loads trades from the seed file and publishes to trade.raw.
     *
     * @return summary map
     */
    Map<String, Object> loadSeedData();
}
