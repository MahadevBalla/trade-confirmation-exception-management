package com.tcem.exception.recommendation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * SLA Calculator (FR9).
 * <p>
 * Computes an {@code slaDeadline} for an exception based on its
 * {@code operationalImpact} level and the time the exception was first
 * captured ({@code capturedAt}).
 * </p>
 *
 * <h3>SLA policy</h3>
 * <table border="1">
 *   <tr><th>operationalImpact</th><th>SLA window</th></tr>
 *   <tr><td>CRITICAL</td><td>+4 hours</td></tr>
 *   <tr><td>HIGH</td><td>+24 hours</td></tr>
 *   <tr><td>MEDIUM</td><td>+72 hours</td></tr>
 *   <tr><td>LOW</td><td>+7 days</td></tr>
 *   <tr><td>null / unknown</td><td>+72 hours (default)</td></tr>
 * </table>
 */
@Component
public class SlaCalculator {

    private static final Logger log = LoggerFactory.getLogger(SlaCalculator.class);

    private static final long HOURS_4_SECONDS  = 4L  * 3600;
    private static final long HOURS_24_SECONDS = 24L * 3600;
    private static final long HOURS_72_SECONDS = 72L * 3600;
    private static final long DAYS_7_SECONDS   = 7L  * 86400;

    /**
     * Computes the SLA deadline for an exception.
     *
     * @param operationalImpact the impact string set by {@link RecommendationEngine}
     *                          (CRITICAL / HIGH / MEDIUM / LOW); may be null
     * @param capturedAt        the instant the exception was first captured;
     *                          if null, {@link Instant#now()} is used as the baseline
     * @return the SLA deadline instant (never null)
     */
    public Instant computeDeadline(String operationalImpact, Instant capturedAt) {
        Instant baseline = (capturedAt != null) ? capturedAt : Instant.now();

        if (operationalImpact == null) {
            log.debug("operationalImpact is null — applying default SLA of 72 hours");
            return baseline.plusSeconds(HOURS_72_SECONDS);
        }

        return switch (operationalImpact.toUpperCase()) {
            case "CRITICAL" -> {
                log.debug("SLA: CRITICAL → +4 hours from {}", baseline);
                yield baseline.plusSeconds(HOURS_4_SECONDS);
            }
            case "HIGH" -> {
                log.debug("SLA: HIGH → +24 hours from {}", baseline);
                yield baseline.plusSeconds(HOURS_24_SECONDS);
            }
            case "MEDIUM" -> {
                log.debug("SLA: MEDIUM → +72 hours from {}", baseline);
                yield baseline.plusSeconds(HOURS_72_SECONDS);
            }
            case "LOW" -> {
                log.debug("SLA: LOW → +7 days from {}", baseline);
                yield baseline.plusSeconds(DAYS_7_SECONDS);
            }
            default -> {
                log.warn("Unknown operationalImpact '{}' — applying default SLA of 72 hours", operationalImpact);
                yield baseline.plusSeconds(HOURS_72_SECONDS);
            }
        };
    }
}

