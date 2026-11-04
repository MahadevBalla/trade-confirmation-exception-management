package com.tcem.validation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tcem.validation.repository.ConfirmationCouchDbRepository;
import com.tcem.validation.repository.ConfirmationDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc slice tests for {@link ConfirmationQueryController}.
 * <p>
 * Uses {@code @WebMvcTest} to load only the web layer; all repository
 * dependencies are mocked via {@code @MockBean}.
 * </p>
 */
@WebMvcTest(ConfirmationQueryController.class)
class ConfirmationQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConfirmationCouchDbRepository confirmationRepository;

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private ConfirmationDocument buildDoc(String id, String tradeId, int version) {
        ConfirmationDocument doc = new ConfirmationDocument();
        doc.setId(id);
        doc.setConfirmationId(id);
        doc.setTradeId(tradeId);
        doc.setTradeVersion(version);
        doc.setGeneratedAt(Instant.parse("2026-01-15T10:30:00Z"));
        doc.setGeneratedBy("validation-service");
        return doc;
    }

    // -----------------------------------------------------------------------
    // GET /confirmations/{id}
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("GET /confirmations/{id}: found → 200 with body")
    void getById_found_returns200() throws Exception {
        ConfirmationDocument doc = buildDoc("CONF-AABBCCDD", "TRD-001", 1);
        when(confirmationRepository.findById("CONF-AABBCCDD")).thenReturn(Optional.of(doc));

        mockMvc.perform(get("/confirmations/CONF-AABBCCDD")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmationId").value("CONF-AABBCCDD"))
                .andExpect(jsonPath("$.tradeId").value("TRD-001"))
                .andExpect(jsonPath("$.tradeVersion").value(1));
    }

    @Test
    @DisplayName("GET /confirmations/{id}: not found → 404")
    void getById_notFound_returns404() throws Exception {
        when(confirmationRepository.findById("CONF-MISSING")).thenReturn(Optional.empty());

        mockMvc.perform(get("/confirmations/CONF-MISSING")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // -----------------------------------------------------------------------
    // GET /trades/{tradeId}/confirmations
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("GET /trades/{tradeId}/confirmations: multiple docs → 200 with list")
    void getByTradeId_multipleFound_returns200() throws Exception {
        List<ConfirmationDocument> docs = List.of(
                buildDoc("CONF-V2", "TRD-002", 2),
                buildDoc("CONF-V1", "TRD-002", 1)
        );
        when(confirmationRepository.findByTradeId("TRD-002")).thenReturn(docs);

        mockMvc.perform(get("/trades/TRD-002/confirmations")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].tradeVersion").value(2))
                .andExpect(jsonPath("$[1].tradeVersion").value(1));
    }

    @Test
    @DisplayName("GET /trades/{tradeId}/confirmations: none found → 200 with empty list")
    void getByTradeId_noneFound_returns200EmptyList() throws Exception {
        when(confirmationRepository.findByTradeId("TRD-UNKNOWN")).thenReturn(List.of());

        mockMvc.perform(get("/trades/TRD-UNKNOWN/confirmations")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // -----------------------------------------------------------------------
    // GET /trades/{tradeId}/confirmation
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("GET /trades/{tradeId}/confirmation: found → 200 with latest doc")
    void getLatestForTrade_found_returns200() throws Exception {
        ConfirmationDocument doc = buildDoc("CONF-LATEST", "TRD-003", 3);
        when(confirmationRepository.findLatestForTrade("TRD-003")).thenReturn(Optional.of(doc));

        mockMvc.perform(get("/trades/TRD-003/confirmation")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmationId").value("CONF-LATEST"))
                .andExpect(jsonPath("$.tradeVersion").value(3));
    }

    @Test
    @DisplayName("GET /trades/{tradeId}/confirmation: not found → 404")
    void getLatestForTrade_notFound_returns404() throws Exception {
        when(confirmationRepository.findLatestForTrade("TRD-NONE")).thenReturn(Optional.empty());

        mockMvc.perform(get("/trades/TRD-NONE/confirmation")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // -----------------------------------------------------------------------
    // GET /metrics/stp-rate
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("GET /metrics/stp-rate: returns totalConfirmations count")
    void getStpRate_returnsMetrics() throws Exception {
        when(confirmationRepository.countAll()).thenReturn(42L);

        mockMvc.perform(get("/metrics/stp-rate")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalConfirmations").value(42));
    }

    @Test
    @DisplayName("GET /metrics/stp-rate: zero confirmations → returns 0")
    void getStpRate_zeroConfirmations_returnsZero() throws Exception {
        when(confirmationRepository.countAll()).thenReturn(0L);

        mockMvc.perform(get("/metrics/stp-rate")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalConfirmations").value(0));
    }

    @Test
    @DisplayName("GET /confirmations: returns a page, with the size capped")
    void listConfirmations_returnsPage() throws Exception {
        when(confirmationRepository.findPage(1000, 0)).thenReturn(List.of(buildDoc("CONF-1", "TRD-1", 1)));

        mockMvc.perform(get("/confirmations").param("size", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.content[0].confirmationId").value("CONF-1"));
    }
}
