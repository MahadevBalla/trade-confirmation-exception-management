package com.tcem.enrichment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.model.message.MessageHeader;
import com.tcem.common.model.message.TradeEvent;
import com.tcem.enrichment.producer.DeadLetterProducer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class KafkaErrorHandlingConfig {
    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandlingConfig.class);
    @Bean
    DefaultErrorHandler enrichmentKafkaErrorHandler(DeadLetterProducer deadLetterProducer, ObjectMapper objectMapper) {
        return new DefaultErrorHandler((record, error) -> {
            log.error("Retries exhausted; dead-lettering enrichment failure [key={}, topic={}, partition={}, offset={}]: {}",
                    record.key(), record.topic(), record.partition(), record.offset(), error.getMessage());
            deadLetterProducer.publish((String) record.key(), (String) record.value(),
                    header((String) record.value(), objectMapper), error, true);
        }, new FixedBackOff(250L, 2L));
    }

    private MessageHeader header(String payload, ObjectMapper mapper) {
        try { return mapper.readValue(payload, TradeEvent.class).getMessageHeader(); }
        catch (Exception ignored) { return null; }
    }
}
