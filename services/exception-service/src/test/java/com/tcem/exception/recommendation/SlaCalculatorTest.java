package com.tcem.exception.recommendation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link SlaCalculator}.
 * Verifies SLA deadline computation for each operationalImpact level and edge cases.
 */
class SlaCalculatorTest {

    private SlaCalculator slaCalculator;

    @BeforeEach
    void setUp() {
        slaCalculator = new SlaCalculator();
    }

    @Test
    void computeDeadline_criticalImpact_returns4HoursFromCapturedAt() {
        Instant capturedAt = Instant.parse("2025-06-01T10:00:00Z");
        Instant deadline = slaCalculator.computeDeadline("CRITICAL", capturedAt);

        Instant expected = capturedAt.plusSeconds(4L * 3600);
        assertThat(deadline).isEqualTo(expected);
    }

    @Test
    void computeDeadline_highImpact_returns24HoursFromCapturedAt() {
        Instant capturedAt = Instant.parse("2025-06-01T10:00:00Z");
        Instant deadline = slaCalculator.computeDeadline("HIGH", capturedAt);

        Instant expected = capturedAt.plusSeconds(24L * 3600);
        assertThat(deadline).isEqualTo(expected);
    }

    @Test
    void computeDeadline_mediumImpact_returns72HoursFromCapturedAt() {
        Instant capturedAt = Instant.parse("2025-06-01T10:00:00Z");
        Instant deadline = slaCalculator.computeDeadline("MEDIUM", capturedAt);

        Instant expected = capturedAt.plusSeconds(72L * 3600);
        assertThat(deadline).isEqualTo(expected);
    }

    @Test
    void computeDeadline_lowImpact_returns7DaysFromCapturedAt() {
        Instant capturedAt = Instant.parse("2025-06-01T10:00:00Z");
        Instant deadline = slaCalculator.computeDeadline("LOW", capturedAt);

        Instant expected = capturedAt.plusSeconds(7L * 86400);
        assertThat(deadline).isEqualTo(expected);
    }

    @Test
    void computeDeadline_nullImpact_returnsDefault72Hours() {
        Instant capturedAt = Instant.parse("2025-06-01T10:00:00Z");
        Instant deadline = slaCalculator.computeDeadline(null, capturedAt);

        Instant expected = capturedAt.plusSeconds(72L * 3600);
        assertThat(deadline).isEqualTo(expected);
    }

    @Test
    void computeDeadline_unknownImpact_returnsDefault72Hours() {
        Instant capturedAt = Instant.parse("2025-06-01T10:00:00Z");
        Instant deadline = slaCalculator.computeDeadline("EXTREME", capturedAt);

        Instant expected = capturedAt.plusSeconds(72L * 3600);
        assertThat(deadline).isEqualTo(expected);
    }

    @Test
    void computeDeadline_caseInsensitive_criticalLowercase() {
        Instant capturedAt = Instant.parse("2025-06-01T10:00:00Z");
        Instant deadline = slaCalculator.computeDeadline("critical", capturedAt);

        Instant expected = capturedAt.plusSeconds(4L * 3600);
        assertThat(deadline).isEqualTo(expected);
    }

    @Test
    void computeDeadline_nullCapturedAt_usesNowAsBaseline() {
        Instant before = Instant.now();
        Instant deadline = slaCalculator.computeDeadline("HIGH", null);
        Instant after = Instant.now();

        // deadline should be ~24 hours from now (±1 second tolerance)
        assertThat(deadline).isAfterOrEqualTo(before.plusSeconds(24L * 3600 - 1));
        assertThat(deadline).isBeforeOrEqualTo(after.plusSeconds(24L * 3600 + 1));
    }
}

