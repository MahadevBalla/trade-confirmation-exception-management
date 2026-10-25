package com.tcem.exception.controller;

import com.tcem.common.constants.ExceptionCategory;
import com.tcem.exception.config.CouchDbProperties;
import com.tcem.exception.couchdb.CouchDbOperations;
import com.tcem.exception.couchdb.FindQueryRequest;
import com.tcem.exception.model.document.AuditDocument;
import com.tcem.exception.model.document.ExceptionDocument;
import com.tcem.exception.repository.AuditCouchDbRepository;
import com.tcem.exception.repository.ExceptionCouchDbRepository;
import com.tcem.exception.service.ExceptionMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for {@link ExceptionQueryController}.
 * Uses {@link MockMvc} standalone setup + mocked {@link CouchDbOperations}.
 */
@ExtendWith(MockitoExtension.class)
class ExceptionQueryControllerTest {

    @Mock
    private CouchDbOperations couchDb;

    private MockMvc mockMvc;
    private ExceptionMetricsService metricsService;

    @BeforeEach
    void setUp() {
        CouchDbProperties props = new CouchDbProperties();
        props.setExceptionsDatabase("exceptions");
        props.setAuditEventsDatabase("audit-events");

        ExceptionCouchDbRepository exceptionRepo = new ExceptionCouchDbRepository(couchDb, props);
        AuditCouchDbRepository auditRepo         = new AuditCouchDbRepository(couchDb, props);
        metricsService                           = new ExceptionMetricsService();

        ExceptionQueryController controller = new ExceptionQueryController(exceptionRepo, auditRepo, metricsService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // -------------------------------------------------------------------------
    // GET /exceptions/{id}
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /exceptions/{id} returns 200 with exception detail and audit trail when found")
    void getExceptionById_found_returns200() throws Exception {
        ExceptionDocument doc = buildExceptionDoc("EXC-30001", "TRD-001");
        AuditDocument audit   = buildAuditDoc("TRD-001", "EXC-30001");

        when(couchDb.getDocument("exceptions", "EXC-30001", ExceptionDocument.class))
                .thenReturn(Optional.of(doc));
        when(couchDb.find(eq("audit-events"), any(FindQueryRequest.class), eq(AuditDocument.class)))
                .thenReturn(List.of(audit));

        mockMvc.perform(get("/exceptions/EXC-30001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exception.exceptionId").value("EXC-30001"))
                .andExpect(jsonPath("$.exception.tradeId").value("TRD-001"))
                .andExpect(jsonPath("$.auditTrail[0].stage").value("CLASSIFICATION"));
    }

    @Test
    @DisplayName("GET /exceptions/{id} returns 404 when exception not found")
    void getExceptionById_notFound_returns404() throws Exception {
        when(couchDb.getDocument("exceptions", "EXC-99999", ExceptionDocument.class))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/exceptions/EXC-99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Exception not found"))
                .andExpect(jsonPath("$.exceptionId").value("EXC-99999"));
    }

    // -------------------------------------------------------------------------
    // GET /exceptions (list)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /exceptions returns paginated list with default params")
    void listExceptions_noFilter_returnsAll() throws Exception {
        ExceptionDocument doc = buildExceptionDoc("EXC-30001", "TRD-001");
        when(couchDb.find(eq("exceptions"), any(FindQueryRequest.class), eq(ExceptionDocument.class)))
                .thenReturn(List.of(doc));

        mockMvc.perform(get("/exceptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exceptions[0].exceptionId").value("EXC-30001"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    @DisplayName("GET /exceptions?tradeId=TRD-001 filters by tradeId")
    void listExceptions_tradeIdFilter_callsFindByTradeId() throws Exception {
        ExceptionDocument doc = buildExceptionDoc("EXC-30001", "TRD-001");
        when(couchDb.find(eq("exceptions"), any(FindQueryRequest.class), eq(ExceptionDocument.class)))
                .thenReturn(List.of(doc));

        mockMvc.perform(get("/exceptions?tradeId=TRD-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exceptions[0].tradeId").value("TRD-001"))
                .andExpect(jsonPath("$.tradeIdFilter").value("TRD-001"));
    }

    @Test
    @DisplayName("GET /exceptions?status=OPEN filters by status")
    void listExceptions_statusFilter_appliesCorrectly() throws Exception {
        when(couchDb.find(eq("exceptions"), any(FindQueryRequest.class), eq(ExceptionDocument.class)))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/exceptions?status=OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.statusFilter").value("OPEN"));
    }

    @Test
    @DisplayName("GET /exceptions?category=MISSING_ECONOMIC_DATA filters by category")
    void listExceptions_categoryFilter_appliesCorrectly() throws Exception {
        ExceptionDocument doc = buildExceptionDoc("EXC-30002", "TRD-002");
        doc.setCategory(ExceptionCategory.MISSING_ECONOMIC_DATA);
        when(couchDb.find(eq("exceptions"), any(FindQueryRequest.class), eq(ExceptionDocument.class)))
                .thenReturn(List.of(doc));

        mockMvc.perform(get("/exceptions?category=MISSING_ECONOMIC_DATA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.categoryFilter").value("MISSING_ECONOMIC_DATA"));
    }

    @Test
    @DisplayName("GET /exceptions?page=1&size=10 applies pagination")
    void listExceptions_pagination_appliesCorrectly() throws Exception {
        when(couchDb.find(eq("exceptions"), any(FindQueryRequest.class), eq(ExceptionDocument.class)))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/exceptions?page=1&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10));
    }

    // -------------------------------------------------------------------------
    // GET /exceptions/{id}/audit
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /exceptions/{id}/audit returns audit trail for exception")
    void getExceptionAuditTrail_returnsAuditList() throws Exception {
        AuditDocument audit1 = buildAuditDoc("TRD-001", "EXC-30001");
        AuditDocument audit2 = buildAuditDoc("TRD-001", "EXC-30001");
        audit2.setStage("ROUTING");

        when(couchDb.find(eq("audit-events"), any(FindQueryRequest.class), eq(AuditDocument.class)))
                .thenReturn(List.of(audit1, audit2));

        mockMvc.perform(get("/exceptions/EXC-30001/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].stage").value("CLASSIFICATION"))
                .andExpect(jsonPath("$[1].stage").value("ROUTING"));
    }

    @Test
    @DisplayName("GET /exceptions/{id}/audit returns empty list when no audit records found")
    void getExceptionAuditTrail_noRecords_returnsEmptyArray() throws Exception {
        when(couchDb.find(eq("audit-events"), any(FindQueryRequest.class), eq(AuditDocument.class)))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/exceptions/EXC-UNKNOWN/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // -------------------------------------------------------------------------
    // GET /exceptions/metrics/summary
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /exceptions/metrics/summary returns metrics snapshot")
    void getMetricsSummary_returnsCorrectFields() throws Exception {
        mockMvc.perform(get("/exceptions/metrics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReceived").exists())
                .andExpect(jsonPath("$.totalClassified").exists())
                .andExpect(jsonPath("$.totalManualReview").exists())
                .andExpect(jsonPath("$.totalDuplicates").exists())
                .andExpect(jsonPath("$.totalDeadLetter").exists())
                .andExpect(jsonPath("$.classificationRatePercent").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // -------------------------------------------------------------------------
    // GET /metrics/exception-rate
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /metrics/exception-rate returns rate map with all required fields")
    void getExceptionRate_returnsAllFields() throws Exception {
        mockMvc.perform(get("/metrics/exception-rate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("exception-service"))
                .andExpect(jsonPath("$.classificationRatePercent").exists())
                .andExpect(jsonPath("$.totalReceived").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ExceptionDocument buildExceptionDoc(String exceptionId, String tradeId) {
        return ExceptionDocument.builder()
                .exceptionId(exceptionId)
                .tradeId(tradeId)
                .status("OPEN")
                .category(ExceptionCategory.MISSING_ECONOMIC_DATA)
                .capturedAt(Instant.now())
                .build();
    }

    private AuditDocument buildAuditDoc(String tradeId, String exceptionId) {
        return AuditDocument.builder()
                .tradeId(tradeId)
                .exceptionId(exceptionId)
                .stage("CLASSIFICATION")
                .timestamp(Instant.now())
                .build();
    }
}
