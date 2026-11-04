package com.tcem.ingestion.runner;

import com.tcem.ingestion.config.IngestionProperties;
import com.tcem.ingestion.service.SeedAlreadyLoadedException;
import com.tcem.ingestion.service.TradeSeedService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StartupSeedRunnerTest {

    @Test
    @DisplayName("a restart with startup seeding on doesn't crash when the seed is already loaded")
    void alreadyLoadedDoesNotStopStartup() {
        TradeSeedService seedService = mock(TradeSeedService.class);
        when(seedService.loadSeedData()).thenThrow(new SeedAlreadyLoadedException());
        IngestionProperties properties = new IngestionProperties();
        properties.setSeedOnStartup(true);

        assertDoesNotThrow(() -> new StartupSeedRunner(seedService, properties).run(null));
        verify(seedService).loadSeedData();
    }
}
