package com.tcem.ingestion.config;

import com.tcem.ingestion.controller.AuthController;
import com.tcem.ingestion.controller.TradeProducerController;
import com.tcem.ingestion.service.TradeSeedService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {TradeProducerController.class, AuthController.class})
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired private MockMvc mvc;
    @MockBean private TradeSeedService tradeSeedService;

    private static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("publishing and seeding need a signed-in user; reads stay open")
    void writesNeedAUser() throws Exception {
        mvc.perform(post("/trades/seed")).andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("WWW-Authenticate"));
        mvc.perform(post("/trades/generate/scenario").param("scenario", "VALID_STANDARD_STP")).andExpect(status().isUnauthorized());
        mvc.perform(post("/trades/seed").header("Authorization", basic("ops", "ops-demo"))).andExpect(status().isOk());
        mvc.perform(get("/trades/scenarios")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("/auth/me returns the user and role for good credentials, and 401 otherwise")
    void me() throws Exception {
        mvc.perform(get("/auth/me").header("Authorization", basic("supervisor", "supervisor-demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("supervisor"))
                .andExpect(jsonPath("$.role").value("SUPERVISOR"));
        mvc.perform(get("/auth/me").header("Authorization", basic("supervisor", "wrong"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
    }
}
