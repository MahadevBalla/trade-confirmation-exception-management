package com.tcem.ingestion.service;

import com.tcem.ingestion.model.IngestionMetricsSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionMetricsServiceTest {

    private IngestionMetricsService metricsService;

    @BeforeEach
    void setUp() {
        metricsService = new IngestionMetricsService();
    }

    @Test
    @DisplayName("Initial metrics summary starts with zeros and 100% acceptance rate")
    void testInitialMetrics() {
        IngestionMetricsSummary summary = metricsService.getSummary();

        assertThat(summary.getTotalReceived()).isZero();
        assertThat(summary.getTotalAccepted()).isZero();
        assertThat(summary.getTotalSchemaInvalid()).isZero();
        assertThat(summary.getTotalDuplicates()).isZero();
        assertThat(summary.getTotalDeadLetter()).isZero();
        assertThat(summary.getAcceptanceRatePercent()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("Recording trade events correctly updates counts and computes acceptance rate")
    void testRecordingTradeMetrics() {
        metricsService.recordReceived();
        metricsService.recordReceived();
        metricsService.recordReceived();
        metricsService.recordReceived();

        metricsService.recordAccepted();
        metricsService.recordAccepted();
        metricsService.recordAccepted();
        metricsService.recordSchemaInvalid();
        metricsService.recordDuplicate();
        metricsService.recordDeadLetter();

        IngestionMetricsSummary summary = metricsService.getSummary();

        assertThat(summary.getTotalReceived()).isEqualTo(4);
        assertThat(summary.getTotalAccepted()).isEqualTo(3);
        assertThat(summary.getTotalSchemaInvalid()).isEqualTo(1);
        assertThat(summary.getTotalDuplicates()).isEqualTo(1);
        assertThat(summary.getTotalDeadLetter()).isEqualTo(1);

        // 3 accepted out of 5 evaluated (3 accepted + 1 invalid + 1 deadletter) = 60.0%
        assertThat(summary.getAcceptanceRatePercent()).isEqualTo(60.0);
    }

    @Test
    @DisplayName("getStpRate returns correctly formatted map for dashboard")
    void testGetStpRate() {
        metricsService.recordAccepted();
        metricsService.recordAccepted();
        metricsService.recordAccepted();
        metricsService.recordSchemaInvalid();

        Map<String, Object> stpRateMap = metricsService.getStpRate();

        assertThat(stpRateMap).containsEntry("service", "ingestion-service");
        assertThat(stpRateMap).containsEntry("stpRatePercent", 75.0);
        assertThat(stpRateMap).containsEntry("totalTradesEvaluated", 4L);
        assertThat(stpRateMap).containsEntry("acceptedTrades", 3L);
        assertThat(stpRateMap).containsEntry("schemaInvalidTrades", 1L);
        assertThat(stpRateMap).containsKey("timestamp");
    }
}
