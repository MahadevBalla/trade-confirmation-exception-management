package com.tcem.workitem.controller;

import com.tcem.workitem.DlqTestSupport;
import com.tcem.workitem.TestWiring;
import com.tcem.workitem.model.document.DlqEntryDocument;
import com.tcem.workitem.repository.DlqEntryRepository;
import com.tcem.workitem.service.ReplayException;
import com.tcem.workitem.service.ReplayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DlqControllerTest {

    @Mock
    private ReplayService replayService;

    @Mock
    private KafkaOperations<String, String> kafka;

    private DlqEntryRepository dlqRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TestWiring wiring = new TestWiring(kafka);
        dlqRepository = wiring.dlqRepository;
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DlqController(dlqRepository, replayService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(DlqTestSupport.objectMapper()))
                .build();
    }

    private DlqEntryDocument entry(String id, String status) {
        DlqEntryDocument doc = new DlqEntryDocument();
        doc.setId(id);
        doc.setTradeId("TRD-1");
        doc.setDlqStatus(status);
        doc.setReplayable(true);
        doc.setReceivedAt(Instant.parse("2026-10-11T10:00:00Z"));
        return dlqRepository.save(doc);
    }

    @Test
    @DisplayName("GET /dlq lists entries and honours the status filter")
    void listWithFilter() throws Exception {
        entry("dlq:A", "PENDING_REPLAY");
        entry("dlq:B", "PERMANENTLY_FAILED");

        mockMvc.perform(get("/dlq"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
        mockMvc.perform(get("/dlq").param("status", "PERMANENTLY_FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.content[0]._id").value("dlq:B"));
    }

    @Test
    @DisplayName("GET /dlq/{id} returns the entry, follows merge aliases, or 404s")
    void getById() throws Exception {
        entry("dlq:A", "PENDING_REPLAY");
        dlqRepository.save(DlqEntryDocument.alias("dlq:LATER-FAILURE", "dlq:A"));

        mockMvc.perform(get("/dlq/dlq:A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeId").value("TRD-1"));
        mockMvc.perform(get("/dlq/dlq:LATER-FAILURE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._id").value("dlq:A"));
        mockMvc.perform(get("/dlq/dlq:missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("alias markers never show up in the DLQ list")
    void aliasesHiddenFromList() throws Exception {
        entry("dlq:A", "PENDING_REPLAY");
        dlqRepository.save(DlqEntryDocument.alias("dlq:LATER-FAILURE", "dlq:A"));

        mockMvc.perform(get("/dlq"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    @DisplayName("POST /dlq/{id}/replay returns the updated entry")
    void replayOk() throws Exception {
        DlqEntryDocument replaying = entry("dlq:A", "REPLAYING");
        when(replayService.replay("dlq:A")).thenReturn(replaying);

        mockMvc.perform(post("/dlq/dlq:A/replay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dlqStatus").value("REPLAYING"));
    }

    @Test
    @DisplayName("POST /dlq/{id}/replay maps replay failures to HTTP status codes")
    void replayErrors() throws Exception {
        when(replayService.replay("dlq:nf")).thenThrow(new ReplayException(ReplayException.Reason.NOT_FOUND, "nf"));
        when(replayService.replay("dlq:no")).thenThrow(new ReplayException(ReplayException.Reason.NOT_REPLAYABLE, "no"));
        when(replayService.replay("dlq:max")).thenThrow(new ReplayException(ReplayException.Reason.MAX_ATTEMPTS_REACHED, "max"));
        when(replayService.replay("dlq:pub")).thenThrow(new ReplayException(ReplayException.Reason.PUBLISH_FAILED, "pub"));

        mockMvc.perform(post("/dlq/dlq:nf/replay")).andExpect(status().isNotFound());
        mockMvc.perform(post("/dlq/dlq:no/replay")).andExpect(status().isConflict());
        mockMvc.perform(post("/dlq/dlq:max/replay")).andExpect(status().isConflict());
        mockMvc.perform(post("/dlq/dlq:pub/replay")).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("pub"));
    }
}
