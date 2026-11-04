package com.tcem.workitem.config;

import com.tcem.workitem.controller.DlqController;
import com.tcem.workitem.controller.WorkItemController;
import com.tcem.workitem.repository.DlqEntryRepository;
import com.tcem.workitem.repository.WorkItemRepository;
import com.tcem.workitem.service.ManualReviewService;
import com.tcem.workitem.service.ReplayService;
import com.tcem.workitem.service.WorkItemService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {WorkItemController.class, DlqController.class})
@Import(SecurityConfig.class)
class SecurityConfigTest {

    private static final String REVIEW = "{\"decision\":\"APPROVE\",\"notes\":\"ok\",\"reviewer\":\"someone-else\"}";

    @Autowired private MockMvc mvc;
    @MockBean private WorkItemRepository workItemRepository;
    @MockBean private WorkItemService workItemService;
    @MockBean private ManualReviewService manualReviewService;
    @MockBean private DlqEntryRepository dlqRepository;
    @MockBean private ReplayService replayService;

    private static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private void expect(MockHttpServletRequestBuilder request, String user, ResultMatcher result) throws Exception {
        if (user != null) {
            request.header("Authorization", basic(user, user.equals("ops") ? "ops-demo" : user + "-demo"));
        }
        mvc.perform(request.contentType(MediaType.APPLICATION_JSON)).andExpect(result);
    }

    private MockHttpServletRequestBuilder review() {
        return post("/workitems/WI-1/review").content(REVIEW);
    }

    @Test
    @DisplayName("reads stay open")
    void readsAreOpen() throws Exception {
        expect(get("/workitems"), null, status().isOk());
        expect(get("/dlq"), null, status().isOk());
    }

    @Test
    @DisplayName("every change needs a signed-in user, and no browser password dialog is requested")
    void writesNeedAUser() throws Exception {
        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
                review(), post("/dlq/dlq:X/replay"),
                post("/workitems").content("{\"tradeId\":\"T\",\"type\":\"DATA_FIX\",\"title\":\"t\"}"),
                patch("/workitems/WI-1").content("{}")}) {
            mvc.perform(request.contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().doesNotExist("WWW-Authenticate"))
                    .andExpect(jsonPath("$.error").exists());
        }
    }

    @Test
    @DisplayName("a wrong password is rejected")
    void wrongPassword() throws Exception {
        mvc.perform(review().header("Authorization", basic("supervisor", "wrong")).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("only supervisors and admins can decide a manual review")
    void reviewRoles() throws Exception {
        expect(review(), "ops", status().isForbidden());
        expect(review(), "supervisor", status().isOk());
        expect(review(), "admin", status().isOk());
    }

    @Test
    @DisplayName("only admins can replay")
    void replayRoles() throws Exception {
        expect(post("/dlq/dlq:X/replay"), "ops", status().isForbidden());
        expect(post("/dlq/dlq:X/replay"), "supervisor", status().isForbidden());
        expect(post("/dlq/dlq:X/replay"), "admin", status().isOk());
    }

    @Test
    @DisplayName("any signed-in user can work on work items")
    void workItemsForAnyUser() throws Exception {
        expect(patch("/workitems/WI-1").content("{\"status\":\"DONE\"}"), "ops", status().isOk());
    }

    @Test
    @DisplayName("the signed-in user is recorded as reviewer, not whatever the request claims")
    void reviewerComesFromTheLogin() throws Exception {
        expect(review(), "supervisor", status().isOk());
        verify(manualReviewService).decide(eq("WI-1"), eq("APPROVE"), eq("ok"), eq("supervisor"));
    }

    @Test
    @DisplayName("the signed-in user is recorded as the actor on updates")
    void actorComesFromTheLogin() throws Exception {
        expect(patch("/workitems/WI-1").content("{\"status\":\"DONE\",\"actor\":\"someone-else\"}"), "ops", status().isOk());
        verify(workItemService).update(eq("WI-1"), eq("DONE"), any(), any(), any(), eq("ops"));
    }
}
