package com.tcem.ingestion.validator;

import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;

/**
 * Encapsulates a single field or business rule validation error.
 */
public class ValidationError {

    private final String field;
    private final String message;
    private final ExceptionCategory category;
    private final ExceptionSubTheme subTheme;
    private final String reasonCode;

    public ValidationError(String field,
                           String message,
                           ExceptionCategory category,
                           ExceptionSubTheme subTheme,
                           String reasonCode) {
        this.field = field;
        this.message = message;
        this.category = category;
        this.subTheme = subTheme;
        this.reasonCode = reasonCode;
    }

    public String getField() {
        return field;
    }

    public String getMessage() {
        return message;
    }

    public ExceptionCategory getCategory() {
        return category;
    }

    public ExceptionSubTheme getSubTheme() {
        return subTheme;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    @Override
    public String toString() {
        return "ValidationError{" +
                "field='" + field + '\'' +
                ", message='" + message + '\'' +
                ", category=" + category +
                ", subTheme=" + subTheme +
                ", reasonCode='" + reasonCode + '\'' +
                '}';
    }
}
