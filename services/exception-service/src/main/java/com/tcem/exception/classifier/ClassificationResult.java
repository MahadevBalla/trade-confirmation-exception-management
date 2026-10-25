package com.tcem.exception.classifier;

import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;

/**
 * Mutable accumulator that collects the outcome of classification rule evaluation.
 * <p>
 * After all rules have run, this object holds:
 * <ul>
 *   <li>The confirmed {@link ExceptionCategory} and {@link ExceptionSubTheme}.</li>
 *   <li>A {@code classificationConfidence} score (0.0–1.0).</li>
 *   <li>Optional diagnostic notes explaining any confidence deductions.</li>
 * </ul>
 * </p>
 *
 * <h3>Confidence scoring model</h3>
 * <ul>
 *   <li>{@code 1.0} — category + subTheme + affectedFields are fully consistent per rules.</li>
 *   <li>{@code 0.8} — category is correct but subTheme is {@code UNKNOWN} or mismatched.</li>
 *   <li>{@code 0.5} — category present but no rule can confirm subTheme against affectedFields.</li>
 *   <li>{@code 0.3} — category itself is null/unrecognised.</li>
 * </ul>
 */
public class ClassificationResult {

    private ExceptionCategory confirmedCategory;
    private ExceptionSubTheme confirmedSubTheme;
    private double classificationConfidence = 1.0;
    private String diagnosticNote;

    public ClassificationResult() {}

    // -----------------------------------------------------------------------
    // Fluent setters used by rule implementations
    // -----------------------------------------------------------------------

    public ClassificationResult confirmedCategory(ExceptionCategory category) {
        this.confirmedCategory = category;
        return this;
    }

    public ClassificationResult confirmedSubTheme(ExceptionSubTheme subTheme) {
        this.confirmedSubTheme = subTheme;
        return this;
    }

    public ClassificationResult withConfidence(double confidence) {
        this.classificationConfidence = Math.max(0.0, Math.min(1.0, confidence));
        return this;
    }

    public ClassificationResult withDiagnosticNote(String note) {
        this.diagnosticNote = note;
        return this;
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public ExceptionCategory getConfirmedCategory() { return confirmedCategory; }
    public ExceptionSubTheme getConfirmedSubTheme() { return confirmedSubTheme; }
    public double getClassificationConfidence() { return classificationConfidence; }
    public String getDiagnosticNote() { return diagnosticNote; }

    /**
     * Returns true if the classification confidence is below the manual-review threshold (0.6).
     * Matches workitem-service's {@code workitem.low-confidence-threshold}.
     */
    public boolean requiresManualReview() {
        return classificationConfidence < 0.6;
    }

    @Override
    public String toString() {
        return "ClassificationResult{category=" + confirmedCategory
                + ", subTheme=" + confirmedSubTheme
                + ", confidence=" + classificationConfidence
                + ", note='" + diagnosticNote + "'}";
    }
}

