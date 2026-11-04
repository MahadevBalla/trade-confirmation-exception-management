package com.tcem.validation.repository;

import com.tcem.validation.config.CouchDbProperties;
import com.tcem.validation.couchdb.CouchDbOperations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeldTradeRepositoryTest {

    @Mock private CouchDbOperations couchDb;

    private HeldTradeRepository repository;

    @BeforeEach
    void setUp() {
        repository = new HeldTradeRepository(couchDb, new CouchDbProperties());
    }

    @Test
    @DisplayName("hold stores the raw message under a docType confirmation queries do not select")
    @SuppressWarnings("unchecked")
    void hold() {
        repository.hold("TRD-1", "{\"raw\":true}");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(couchDb).putDocument(eq("confirmations"), eq("held-trade-TRD-1"), captor.capture());
        Map<String, Object> document = (Map<String, Object>) captor.getValue();
        assertThat(document).containsEntry("docType", "HELD_TRADE").containsEntry("payload", "{\"raw\":true}");
    }

    @Test
    @DisplayName("find returns the held message, or empty when there is none")
    void find() {
        when(couchDb.getDocument("confirmations", "held-trade-TRD-1", Map.class))
                .thenReturn(Optional.of(Map.of("payload", "{\"raw\":true}")));
        when(couchDb.getDocument("confirmations", "held-trade-TRD-2", Map.class)).thenReturn(Optional.empty());

        assertThat(repository.find("TRD-1")).contains("{\"raw\":true}");
        assertThat(repository.find("TRD-2")).isEmpty();
    }
}
