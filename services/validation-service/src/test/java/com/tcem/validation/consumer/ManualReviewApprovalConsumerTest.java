package com.tcem.validation.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tcem.common.constants.EventType;
import com.tcem.common.constants.TopicNames;
import com.tcem.common.event.DeadLetterEvent;
import com.tcem.common.event.WorkItemEvent;
import com.tcem.common.model.message.MessageHeader;
import com.tcem.common.model.message.SchemaVersions;
import com.tcem.common.model.message.TradeEvent;
import com.tcem.common.model.trade.Trade;
import com.tcem.common.model.workitem.WorkItem;
import com.tcem.validation.config.ValidationProperties;
import com.tcem.validation.confirmation.ConfirmationService;
import com.tcem.validation.producer.DeadLetterProducer;
import com.tcem.validation.repository.HeldTradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualReviewApprovalConsumerTest {

    @Mock private HeldTradeRepository heldTradeRepository;
    @Mock private ConfirmationService confirmationService;
    @Mock private DeadLetterProducer deadLetterProducer;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private ManualReviewApprovalConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ManualReviewApprovalConsumer(objectMapper, heldTradeRepository, confirmationService,
                deadLetterProducer, new ValidationProperties());
    }

    private String workItemEvent(String type, String status, String decision) throws Exception {
        WorkItem item = WorkItem.builder().workItemId("WI-1").tradeId("TRD-1")
                .type(type).status(status).reviewDecision(decision).build();
        return objectMapper.writeValueAsString(new WorkItemEvent(new MessageHeader(), item));
    }

    private String heldTrade() throws Exception {
        MessageHeader header = new MessageHeader();
        header.setMessageId("MSG-1");
        header.setEventType(EventType.NEW_TRADE);
        header.setSchemaVersion(SchemaVersions.CURRENT);
        Trade trade = new Trade();
        trade.setTradeId("TRD-1");
        return objectMapper.writeValueAsString(new TradeEvent(header, trade));
    }

    @Test
    @DisplayName("an approved review confirms the held trade")
    void approved_generatesConfirmation() throws Exception {
        when(heldTradeRepository.find("TRD-1")).thenReturn(Optional.of(heldTrade()));

        consumer.onMessage(workItemEvent("MANUAL_REVIEW", "DONE", "APPROVED"));

        verify(confirmationService).processConfirmation(
                argThat(header -> "MSG-1".equals(header.getMessageId())),
                argThat(trade -> "TRD-1".equals(trade.getTradeId())));
        verifyNoInteractions(deadLetterProducer);
    }

    @Test
    @DisplayName("rejected, undecided, still-open and non-review items never produce a confirmation")
    void otherWorkItems_areIgnored() throws Exception {
        consumer.onMessage(workItemEvent("MANUAL_REVIEW", "DONE", "REJECTED"));
        consumer.onMessage(workItemEvent("MANUAL_REVIEW", "DONE", null));
        consumer.onMessage(workItemEvent("MANUAL_REVIEW", "OPEN", null));
        consumer.onMessage(workItemEvent("MANUAL_REVIEW", "OPEN", "APPROVED"));
        consumer.onMessage(workItemEvent("DATA_FIX", "DONE", "APPROVED"));
        consumer.onMessage("not json");

        verifyNoInteractions(heldTradeRepository, confirmationService, deadLetterProducer);
    }

    @Test
    @DisplayName("an approval with no held trade is dead-lettered, not dropped")
    void noHeldTrade_isDeadLettered() throws Exception {
        when(heldTradeRepository.find("TRD-1")).thenReturn(Optional.empty());

        consumer.onMessage(workItemEvent("MANUAL_REVIEW", "DONE", "APPROVED"));

        ArgumentCaptor<DeadLetterEvent> captor = ArgumentCaptor.forClass(DeadLetterEvent.class);
        verify(deadLetterProducer).sendDeadLetter(eq("TRD-1"), captor.capture());
        assertThat(captor.getValue().isReplayable()).isFalse();
        assertThat(captor.getValue().getFailedOnTopic()).isEqualTo(TopicNames.WORKITEM_LINKED);
        verify(confirmationService, never()).processConfirmation(any(), any());
    }

    @Test
    @DisplayName("a confirmation failure is dead-lettered, not dropped")
    void confirmationFailure_isDeadLettered() throws Exception {
        when(heldTradeRepository.find("TRD-1")).thenReturn(Optional.of(heldTrade()));
        when(confirmationService.processConfirmation(any(), any())).thenThrow(new IllegalStateException("couchdb down"));

        consumer.onMessage(workItemEvent("MANUAL_REVIEW", "DONE", "APPROVED"));

        verify(deadLetterProducer).sendDeadLetter(eq("TRD-1"), any(DeadLetterEvent.class));
    }
}
