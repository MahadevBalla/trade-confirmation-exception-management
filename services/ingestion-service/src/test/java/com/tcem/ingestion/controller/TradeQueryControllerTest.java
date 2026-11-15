package com.tcem.ingestion.controller;

import com.tcem.common.constants.ProcessingStatus;
import com.tcem.ingestion.config.CouchDbProperties;
import com.tcem.ingestion.couchdb.CouchDbOperations;
import com.tcem.ingestion.couchdb.FindQueryRequest;
import com.tcem.ingestion.model.document.AuditDocument;
import com.tcem.ingestion.model.document.TradeDocument;
import com.tcem.ingestion.repository.AuditCouchDbRepository;
import com.tcem.ingestion.repository.TradeCouchDbRepository;
import com.tcem.ingestion.service.IngestionMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TradeQueryControllerTest {

    @Mock
    private CouchDbOperations couchDb;

    private MockMvc mockMvc;
    private IngestionMetricsService metricsService;

    @BeforeEach
    void setUp() {
        CouchDbProperties props = new CouchDbProperties();
        props.setTradesDatabase("trades");
        props.setAuditEventsDatabase("audit-events");

        TradeCouchDbRepository tradeRepository = new TradeCouchDbRepository(couchDb, props);
        AuditCouchDbRepository auditRepository = new AuditCouchDbRepository(couchDb, props);
        metricsService = new IngestionMetricsService();

        TradeQueryController controller = new TradeQueryController(tradeRepository, auditRepository, metricsService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /trades/{id} returns 200 with trade detail and audit trail when found")
    void testGetTradeByIdFound() throws Exception {
        TradeDocument doc = TradeDocument.builder()
                .id("TRD-10001")
                .tradeId("TRD-10001")
                .processingStatus(ProcessingStatus.ACCEPTED)
                .build();

        AuditDocument audit = AuditDocument.builder()
                .tradeId("TRD-10001")
                .stage("SCHEMA_VALIDATION")
                .status(ProcessingStatus.ACCEPTED)
                .timestamp(Instant.now())
                .build();

        when(couchDb.getDocument("trades", "TRD-10001", TradeDocument.class))
                .thenReturn(Optional.of(doc));
        when(couchDb.find(eq("audit-events"), any(FindQueryRequest.class), eq(AuditDocument.class)))
                .thenReturn(List.of(audit));

        mockMvc.perform(get("/trades/TRD-10001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trade.tradeId").value("TRD-10001"))
                .andExpect(jsonPath("$.trade.processingStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.auditTrail[0].stage").value("SCHEMA_VALIDATION"));
    }

    @Test
    @DisplayName("GET /trades/{id} returns 404 when trade does not exist")
    void testGetTradeByIdNotFound() throws Exception {
        when(couchDb.getDocument("trades", "TRD-UNKNOWN", TradeDocument.class))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/trades/TRD-UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Trade not found"))
                .andExpect(jsonPath("$.tradeId").value("TRD-UNKNOWN"));
    }

    @Test
    @DisplayName("GET /trades lists paginated trades")
    void testListTrades() throws Exception {
        TradeDocument doc1 = TradeDocument.builder().tradeId("TRD-01").processingStatus(ProcessingStatus.ACCEPTED).build();
        TradeDocument doc2 = TradeDocument.builder().tradeId("TRD-02").processingStatus(ProcessingStatus.SCHEMA_INVALID).build();

        when(couchDb.find(eq("trades"), any(FindQueryRequest.class), eq(TradeDocument.class)))
                .thenReturn(List.of(doc1, doc2));

        mockMvc.perform(get("/trades?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.trades[0].tradeId").value("TRD-01"))
                .andExpect(jsonPath("$.trades[1].tradeId").value("TRD-02"));
    }

    @Test
    @DisplayName("GET /trades with status filter delegates to status-filtered search")
    void testListTradesWithStatus() throws Exception {
        TradeDocument doc1 = TradeDocument.builder().tradeId("TRD-01").processingStatus(ProcessingStatus.ACCEPTED).build();

        when(couchDb.find(eq("trades"), any(FindQueryRequest.class), eq(TradeDocument.class)))
                .thenReturn(List.of(doc1));

        mockMvc.perform(get("/trades?status=ACCEPTED&page=1&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.statusFilter").value("ACCEPTED"));
    }

    @Test
    @DisplayName("GET /trades/{id}/audit returns audit records")
    void testGetTradeAuditTrail() throws Exception {
        AuditDocument audit = AuditDocument.builder()
                .tradeId("TRD-10001")
                .stage("IDEMPOTENCY_CHECK")
                .status(ProcessingStatus.RECEIVED)
                .build();

        when(couchDb.find(eq("audit-events"), any(FindQueryRequest.class), eq(AuditDocument.class)))
                .thenReturn(List.of(audit));

        mockMvc.perform(get("/trades/TRD-10001/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tradeId").value("TRD-10001"))
                .andExpect(jsonPath("$[0].stage").value("IDEMPOTENCY_CHECK"));
    }

    @Test
    @DisplayName("GET /audit returns a paginated list of audit events")
    void testListAudit() throws Exception {
        AuditDocument audit = AuditDocument.builder()
                .tradeId("TRD-10001")
                .stage("SCHEMA_VALIDATION")
                .status(ProcessingStatus.ACCEPTED)
                .timestamp(Instant.now())
                .build();
        when(couchDb.find(eq("audit-events"), any(FindQueryRequest.class), eq(AuditDocument.class)))
                .thenReturn(List.of(audit));

        mockMvc.perform(get("/audit?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.content[0].tradeId").value("TRD-10001"))
                .andExpect(jsonPath("$.content[0].stage").value("SCHEMA_VALIDATION"));
    }

    @Test
    @DisplayName("GET /trades/metrics/summary returns current metrics")
    void testGetMetricsSummary() throws Exception {
        metricsService.recordReceived();
        metricsService.recordAccepted();

        mockMvc.perform(get("/trades/metrics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReceived").value(1))
                .andExpect(jsonPath("$.totalAccepted").value(1))
                .andExpect(jsonPath("$.acceptanceRatePercent").value(100.0));
    }

    @Test
    @DisplayName("GET /metrics/stp-rate returns STP rate metrics map for React UI")
    void testGetStpRate() throws Exception {
        metricsService.recordAccepted();
        metricsService.recordAccepted();
        metricsService.recordSchemaInvalid();

        mockMvc.perform(get("/metrics/stp-rate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("ingestion-service"))
                .andExpect(jsonPath("$.stpRatePercent").value(66.67))
                .andExpect(jsonPath("$.totalTradesEvaluated").value(3))
                .andExpect(jsonPath("$.acceptedTrades").value(2))
                .andExpect(jsonPath("$.schemaInvalidTrades").value(1));
    }
}
