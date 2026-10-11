package com.tcem.ingestion.runner;

import com.tcem.ingestion.config.IngestionProperties;
import com.tcem.ingestion.service.TradeSeedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Startup runner to optionally seed synthetic trades into trade.raw on application boot.
 */
@Component
public class StartupSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupSeedRunner.class);

    private final TradeSeedService tradeSeedService;
    private final IngestionProperties properties;

    public StartupSeedRunner(TradeSeedService tradeSeedService, IngestionProperties properties) {
        this.tradeSeedService = tradeSeedService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.isSeedOnStartup()) {
            log.info("Startup trade seeding enabled (SEED_ON_STARTUP=true). Loading synthetic trades into trade.raw...");
            tradeSeedService.loadSeedData();
        } else {
            log.info("Startup trade seeding disabled. Trades can be triggered on demand via POST /trades/generate or POST /trades/seed");
        }
    }
}
