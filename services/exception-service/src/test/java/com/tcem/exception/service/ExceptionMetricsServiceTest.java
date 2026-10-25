package com.tcem.exception.service;

import com.tcem.exception.model.ExceptionMetricsSummary;
import com.tcem.exception.model.ExceptionProcessingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ExceptionMetricsService}.
 */
class ExceptionMetricsServiceTest {

    private ExceptionMetricsService metricsService;

    @BeforeEach
    void setUp() {
        metricsService = new ExceptionMetricsService();
    }

    @Test
    @DisplayName("Initial state — all counters are zero and rate is 100.0")
    void initialState_allZero() {
        ExceptionMetricsSummary summary = metricsService.getSummary();

        assertThat(summary.getTotalReceived()).isZero();
        assertThat(summary.getTotalClassified()).isZero();
        assertThat(summary.getTotalManualReview()).isZero();
        assertThat(summary.getTotalDuplicates()).isZero();
        assertThat(summary.getTotalDeadLetter()).isZero();
        assertThat(summary.getClassificationRatePercent()).isEqualTo(100.0);
        assertThat(summary.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("recordReceived increments received counter")
    void recordReceived_incrementsCounter() {
        metricsService.recordReceived();
        metricsService.recordReceived();

        assertThat(metricsService.getReceivedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("record(CLASSIFIED) increments classified counter")
    void record_classified_incrementsCounter() {
        metricsService.record(ExceptionProcessingResult.CLASSIFIED);
        metricsService.record(ExceptionProcessingResult.CLASSIFIED);

        assertThat(metricsService.getClassifiedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("record(MANUAL_REVIEW) increments manualReview counter")
    void record_manualReview_incrementsCounter() {
        metricsService.record(ExceptionProcessingResult.MANUAL_REVIEW);

        assertThat(metricsService.getManualReviewCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("record(DUPLICATE) increments duplicate counter")
    void record_duplicate_incrementsCounter() {
        metricsService.record(ExceptionProcessingResult.DUPLICATE);

        assertThat(metricsService.getDuplicateCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("record(DEAD_LETTER) and recordDeadLetter both increment dead-letter counter")
    void record_deadLetter_incrementsCounter() {
        metricsService.record(ExceptionProcessingResult.DEAD_LETTER);
        metricsService.recordDeadLetter();

        assertThat(metricsService.getDeadLetterCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("record(null) does not throw or mutate counters")
    void record_null_noEffect() {
        metricsService.record(null);

        assertThat(metricsService.getClassifiedCount()).isZero();
        assertThat(metricsService.getManualReviewCount()).isZero();
    }

    @Test
    @DisplayName("getSummary computes classificationRatePercent correctly")
    void getSummary_classificationRateCorrect() {
        // 8 received, 1 duplicate → 7 processable; 5 classified + 2 manual = 7/7 = 100%
        for (int i = 0; i < 8; i++) metricsService.recordReceived();
        for (int i = 0; i < 5; i++) metricsService.record(ExceptionProcessingResult.CLASSIFIED);
        for (int i = 0; i < 2; i++) metricsService.record(ExceptionProcessingResult.MANUAL_REVIEW);
        metricsService.record(ExceptionProcessingResult.DUPLICATE);

        ExceptionMetricsSummary summary = metricsService.getSummary();

        assertThat(summary.getTotalReceived()).isEqualTo(8);
        assertThat(summary.getTotalClassified()).isEqualTo(5);
        assertThat(summary.getTotalManualReview()).isEqualTo(2);
        assertThat(summary.getTotalDuplicates()).isEqualTo(1);
        assertThat(summary.getClassificationRatePercent()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("getSummary classificationRate handles partial success (dead-letter reduces rate)")
    void getSummary_classificationRateWithDeadLetters() {
        // 10 received, 0 duplicates → 10 processable; 8 classified → 80%
        for (int i = 0; i < 10; i++) metricsService.recordReceived();
        for (int i = 0; i < 8; i++) metricsService.record(ExceptionProcessingResult.CLASSIFIED);
        for (int i = 0; i < 2; i++) metricsService.record(ExceptionProcessingResult.DEAD_LETTER);

        ExceptionMetricsSummary summary = metricsService.getSummary();

        assertThat(summary.getClassificationRatePercent()).isEqualTo(80.0);
    }

    @Test
    @DisplayName("getExceptionRate returns map with required fields")
    void getExceptionRate_returnsRequiredFields() {
        metricsService.recordReceived();
        metricsService.record(ExceptionProcessingResult.CLASSIFIED);

        Map<String, Object> rate = metricsService.getExceptionRate();

        assertThat(rate).containsKey("service");
        assertThat(rate).containsKey("classificationRatePercent");
        assertThat(rate).containsKey("totalReceived");
        assertThat(rate).containsKey("totalClassified");
        assertThat(rate).containsKey("totalManualReview");
        assertThat(rate).containsKey("totalDuplicates");
        assertThat(rate).containsKey("totalDeadLetter");
        assertThat(rate).containsKey("timestamp");
        assertThat(rate.get("service")).isEqualTo("exception-service");
    }
}

