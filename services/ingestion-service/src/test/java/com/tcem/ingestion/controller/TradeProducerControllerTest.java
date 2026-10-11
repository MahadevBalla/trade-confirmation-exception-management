package com.tcem.ingestion.controller;

import com.tcem.ingestion.generator.TradeScenarioType;
import com.tcem.ingestion.service.TradeSeedService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TradeProducerControllerTest {

    @Mock
    private TradeSeedService tradeSeedService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TradeProducerController controller = new TradeProducerController(tradeSeedService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void generateTrades_returnsSuccessResponse() throws Exception {
        when(tradeSeedService.generateAndPublish(10, false))
                .thenReturn(Map.of("status", "SUCCESS", "publishedCount", 0, "publishedToKafka", false));

        mockMvc.perform(post("/trades/generate?count=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.publishedToKafka").value(false));

        verify(tradeSeedService).generateAndPublish(10, false);
    }

    @Test
    void generateScenario_triggersSingleScenario() throws Exception {
        when(tradeSeedService.generateSingleScenario(eq(TradeScenarioType.MISSING_ECONOMIC_STRIKE), eq(1), eq(false)))
                .thenReturn(Map.of("status", "SUCCESS", "scenario", "MISSING_ECONOMIC_STRIKE", "publishedToKafka", false));

        mockMvc.perform(post("/trades/generate/scenario?scenario=MISSING_ECONOMIC_STRIKE&sequence=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.scenario").value("MISSING_ECONOMIC_STRIKE"));

        verify(tradeSeedService).generateSingleScenario(eq(TradeScenarioType.MISSING_ECONOMIC_STRIKE), eq(1), eq(false));
    }

    @Test
    void seedTrades_invokesLoadSeedData() throws Exception {
        when(tradeSeedService.loadSeedData(false))
                .thenReturn(Map.of("status", "SUCCESS", "totalSeedTrades", 500, "publishedToKafka", false));

        mockMvc.perform(post("/trades/seed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSeedTrades").value(500));

        verify(tradeSeedService).loadSeedData(false);
    }

    @Test
    void listScenarios_returnsAllScenarios() throws Exception {
        mockMvc.perform(get("/trades/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(TradeScenarioType.values().length));
    }
}
