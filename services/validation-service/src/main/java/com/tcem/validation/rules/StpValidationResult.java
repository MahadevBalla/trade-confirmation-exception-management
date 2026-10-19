package com.tcem.validation.rules;

import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Accumulates STP rule violations and determines the overall pass/fail decision.
 * <p>
 * A trade passes STP if and only if {@link #getFailures()} is empty after all
 * rules have been evaluated.
 * </p>
 */
public class StpValidationResult {

    /**
     * A single STP rule failure.
     */
    public static class Failure {
        private final ExceptionCategory category;
        private final ExceptionSubTheme subTheme;
        private final String reasonCode;
        private final String affectedFields;

        public Failure(ExceptionCategory category, ExceptionSubTheme subTheme,
                       String reasonCode, String affectedFields) {
            this.category = category;
            this.subTheme = subTheme;
            this.reasonCode = reasonCode;
            this.affectedFields = affectedFields;
        }

        public ExceptionCategory getCategory() { return category; }
        public ExceptionSubTheme getSubTheme() { return subTheme; }
        public String getReasonCode() { return reasonCode; }
        public String getAffectedFields() { return affectedFields; }

        @Override
        public String toString() {
            return category + "/" + subTheme + ": " + reasonCode;
        }
    }

    private final List<Failure> failures = new ArrayList<>();

    /**
     * Records a rule violation.
     */
    public void addFailure(ExceptionCategory category, ExceptionSubTheme subTheme,
                           String reasonCode, String affectedFields) {
        failures.add(new Failure(category, subTheme, reasonCode, affectedFields));
    }

    /**
     * @return {@code true} if all rules passed (no failures recorded)
     */
    public boolean isStpPassed() {
        return failures.isEmpty();
    }

    /**
     * @return immutable list of rule violations
     */
    public List<Failure> getFailures() {
        return Collections.unmodifiableList(failures);
    }

    /**
     * Returns a human-readable summary of all failures, useful for logging.
     */
    public String summarize() {
        if (failures.isEmpty()) return "STP_PASSED";
        StringBuilder sb = new StringBuilder("STP_FAILED [");
        for (int i = 0; i < failures.size(); i++) {
            if (i > 0) sb.append("; ");
            sb.append(failures.get(i));
        }
        sb.append("]");
        return sb.toString();
    }
}

