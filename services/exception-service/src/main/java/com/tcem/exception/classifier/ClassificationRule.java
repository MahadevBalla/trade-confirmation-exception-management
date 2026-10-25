package com.tcem.exception.classifier;

import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import com.tcem.common.model.exception.ExceptionRecord;

/**
 * Interface for a single, stateless exception classification rule.
 * <p>
 * Each implementation handles one {@link ExceptionCategory}. Implementations
 * must be non-short-circuiting: they update the {@link ClassificationResult}
 * accumulator and return without throwing.
 * </p>
 */
public interface ClassificationRule {

    /**
     * Returns the {@link ExceptionCategory} this rule is responsible for.
     */
    ExceptionCategory appliesTo();

    /**
     * Evaluates the inbound {@link ExceptionRecord} and updates the
     * {@link ClassificationResult} with the confirmed classification and confidence.
     *
     * @param record the inbound partially-filled exception record from upstream
     * @param result the mutable accumulator to update
     */
    void evaluate(ExceptionRecord record, ClassificationResult result);
}

