package com.tcem.workitem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Business configuration for DLQ handling and replay policy (FR12).
 */
@Configuration
@ConfigurationProperties(prefix = "workitem")
public class WorkItemProperties {

    /** Replays allowed per DLQ entry before it is marked PERMANENTLY_FAILED. */
    private int maxReplayAttempts = 3;

    /** Owner queue assigned to work items auto-created from DLQ entries. */
    private String dlqOwnerQueue = "TECHNOLOGY_SUPPORT";

    /** Classified exceptions below this confidence go to a MANUAL_REVIEW item instead of their category's type. */
    private double lowConfidenceThreshold = 0.6;

    /** Open work items older than this count as aged on the dashboard summary. */
    private int agedHours = 24;

    public double getLowConfidenceThreshold() { return lowConfidenceThreshold; }
    public void setLowConfidenceThreshold(double lowConfidenceThreshold) { this.lowConfidenceThreshold = lowConfidenceThreshold; }

    public int getAgedHours() { return agedHours; }
    public void setAgedHours(int agedHours) { this.agedHours = agedHours; }

    public int getMaxReplayAttempts() { return maxReplayAttempts; }
    public void setMaxReplayAttempts(int maxReplayAttempts) { this.maxReplayAttempts = maxReplayAttempts; }

    public String getDlqOwnerQueue() { return dlqOwnerQueue; }
    public void setDlqOwnerQueue(String dlqOwnerQueue) { this.dlqOwnerQueue = dlqOwnerQueue; }
}
