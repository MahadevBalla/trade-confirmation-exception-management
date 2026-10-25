package com.tcem.exception.model;

/**
 * Outcome enum for the exception processing pipeline (FR7).
 * <p>
 * Returned by {@link com.tcem.exception.service.ExceptionProcessingService#process(String, String)}
 * to indicate how the inbound {@code exception.created} message was handled.
 * </p>
 *
 * <ul>
 *   <li>{@link #CLASSIFIED}    — exception fully classified and published to {@code exception.classified}.</li>
 *   <li>{@link #MANUAL_REVIEW} — low-confidence or always-manual category; published to {@code manual.review.required}.</li>
 *   <li>{@link #DUPLICATE}     — idempotency check detected a previously processed message; skipped.</li>
 *   <li>{@link #DEAD_LETTER}   — irrecoverable failure (parse error, null payload); routed to {@code deadletter.trade}.</li>
 * </ul>
 */
public enum ExceptionProcessingResult {

    /** Exception classified with confidence ≥ 0.6; published to {@code exception.classified}. */
    CLASSIFIED,

    /** Exception classified with confidence < 0.6, or category always requires manual review;
     *  published to {@code manual.review.required}. */
    MANUAL_REVIEW,

    /** Duplicate message detected via idempotency store; processing skipped. */
    DUPLICATE,

    /** Irrecoverable failure; event sent to {@code deadletter.trade}. */
    DEAD_LETTER
}

