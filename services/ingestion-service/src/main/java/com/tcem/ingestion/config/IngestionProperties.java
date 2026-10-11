package com.tcem.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for ingestion service seed loading and generation.
 */
@Configuration
@ConfigurationProperties(prefix = "ingestion")
public class IngestionProperties {

    private boolean seedOnStartup = false;
    private int defaultGenerateCount = 500;
    private String seedFilePath = "seed/sample-trades.json";

    public IngestionProperties() {}

    public boolean isSeedOnStartup() {
        return seedOnStartup;
    }

    public void setSeedOnStartup(boolean seedOnStartup) {
        this.seedOnStartup = seedOnStartup;
    }

    public int getDefaultGenerateCount() {
        return defaultGenerateCount;
    }

    public void setDefaultGenerateCount(int defaultGenerateCount) {
        this.defaultGenerateCount = defaultGenerateCount;
    }

    public String getSeedFilePath() {
        return seedFilePath;
    }

    public void setSeedFilePath(String seedFilePath) {
        this.seedFilePath = seedFilePath;
    }
}
