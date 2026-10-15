package com.tcem.workitem.controller;

import com.tcem.workitem.DlqTestSupport;
import com.tcem.workitem.TestWiring;
import com.tcem.workitem.model.document.WorkItemDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WorkItemControllerTest {

    @Mock
    private KafkaOperations<String, String> kafka;

    private TestWiring wiring;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        wiring = new TestWiring(kafka);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WorkItemController(wiring.workItemRepository, wiring.workItemService,
                        wiring.manualReviewService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(DlqTestSupport.objectMapper()))
                .build();
    }

    private WorkItemDocument item(String tradeId, String type) {
        return wiring.workItemService.createManual(tradeId, type, type + " " + tradeId, null, null, null, null);
    }

    @Test
    @DisplayName("GET /workitems filters by status, type and trade")
    void listWithFilters() throws Exception {
        item("TRD-1", "DATA_FIX");
        item("TRD-1", "TEMPLATE_FIX");
        WorkItemDocument done = item("TRD-2", "DATA_FIX");
        wiring.workItemService.update(done.getWorkItemId(), "DONE", null, null, null, null);

        mockMvc.perform(get("/workitems")).andExpect(jsonPath("$.count").value(3));
        mockMvc.perform(get("/workitems").param("status", "OPEN")).andExpect(jsonPath("$.count").value(2));
        mockMvc.perform(get("/workitems").param("type", "DATA_FIX")).andExpect(jsonPath("$.count").value(2));
        mockMvc.perform(get("/workitems").param("tradeId", "TRD-1").param("type", "TEMPLATE_FIX"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.content[0].type").value("TEMPLATE_FIX"));
    }

    @Test
    @DisplayName("GET /workitems/summary and /workitems/{id}")
    void summaryAndGet() throws Exception {
        WorkItemDocument a = item("TRD-1", "DATA_FIX");

        mockMvc.perform(get("/workitems/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.byType.DATA_FIX").value(1));
        mockMvc.perform(get("/workitems/" + a.getWorkItemId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workItemId").value(a.getWorkItemId()));
        mockMvc.perform(get("/workitems/WI-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("POST /workitems creates (201) or rejects invalid input (400)")
    void create() throws Exception {
        mockMvc.perform(post("/workitems").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tradeId\":\"TRD-1\",\"type\":\"ENHANCEMENT\",\"title\":\"Add retry\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.priority").value("HIGH"));
        mockMvc.perform(post("/workitems").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"BUG\",\"title\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
        mockMvc.perform(post("/workitems").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /workitems/{id} updates, then 409s once the item is terminal")
    void patchUpdatesUntilTerminal() throws Exception {
        WorkItemDocument a = item("TRD-1", "DATA_FIX");
        String url = "/workitems/" + a.getWorkItemId();

        mockMvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\",\"ownerQueue\":\"REFERENCE_DATA\",\"actor\":\"ops-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.ownerQueue").value("REFERENCE_DATA"));
        mockMvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\",\"notes\":\"fixed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolvedAt").exists());
        mockMvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content("{\"priority\":\"URGENT\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /workitems/{id}/review decides manual reviews; other items 409")
    void review() throws Exception {
        WorkItemDocument review = item("TRD-1", "MANUAL_REVIEW");
        WorkItemDocument dataFix = item("TRD-2", "DATA_FIX");

        mockMvc.perform(post("/workitems/" + review.getWorkItemId() + "/review").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"notes\":\"ok\",\"reviewer\":\"sup-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.reviewDecision").value("APPROVED"));
        mockMvc.perform(post("/workitems/" + dataFix.getWorkItemId() + "/review").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/workitems/" + review.getWorkItemId() + "/review").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"MAYBE\"}"))
                .andExpect(status().isBadRequest());
    }
}
