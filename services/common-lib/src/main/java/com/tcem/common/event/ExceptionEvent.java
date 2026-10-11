package com.tcem.common.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tcem.common.model.exception.ExceptionRecord;
import com.tcem.common.model.message.MessageHeader;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kafka event envelope for {@code exception.created} and {@code exception.classified} topics.
 * <p>
 * The same DTO is used for both topics:
 * <ul>
 *   <li>{@code exception.created} – produced by validation-service (FR7);
 *       {@code category}/{@code subTheme}/{@code recommendedAction} may be null
 *       at this stage.</li>
 *   <li>{@code exception.classified} – produced by exception-service (FR8, FR9);
 *       all classification and recommendation fields are populated.</li>
 * </ul>
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExceptionEvent {

    /** Standard envelope header. */
    private MessageHeader messageHeader;

    /** The exception record (partially filled on creation, fully filled after classification). */
    private ExceptionRecord exception;

    public ExceptionEvent() {}

    public ExceptionEvent(MessageHeader messageHeader, ExceptionRecord exception) {
        this.messageHeader = messageHeader;
        this.exception = exception;
    }

    public static Builder builder() {
        return new Builder();
    }

    public MessageHeader getMessageHeader() {
        return messageHeader;
    }

    public void setMessageHeader(MessageHeader messageHeader) {
        this.messageHeader = messageHeader;
    }

    public ExceptionRecord getException() {
        return exception;
    }

    public void setException(ExceptionRecord exception) {
        this.exception = exception;
    }

    public static class Builder {
        private MessageHeader messageHeader;
        private ExceptionRecord exception;

        public Builder messageHeader(MessageHeader messageHeader) {
            this.messageHeader = messageHeader;
            return this;
        }

        public Builder exception(ExceptionRecord exception) {
            this.exception = exception;
            return this;
        }

        public ExceptionEvent build() {
            return new ExceptionEvent(messageHeader, exception);
        }
    }
}
