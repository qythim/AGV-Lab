package com.agvlab.rcs.agv.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class AgvControllerIntegrationTests {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void createThenReadAgvFromDatabase() throws Exception {
        String code = "TEST_AGV_" + UUID.randomUUID();
        String body = "{\"agvCode\":\"" + code + "\",\"agvName\":\"測試車\",\"agvType\":\"STANDARD\"}";

        mockMvc.perform(post("/api/agvs").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.agvCode").value(code))
                .andExpect(jsonPath("$.status").value("IDLE"));

        mockMvc.perform(get("/api/agvs/{agvCode}", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agvName").value("測試車"));

        String list = mockMvc.perform(get("/api/agvs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(list.contains(code));
    }

    @Test
    void rejectDuplicateAndInvalidRequests() throws Exception {
        String code = "TEST_AGV_" + UUID.randomUUID();
        String body = "{\"agvCode\":\"" + code + "\",\"agvType\":\"TELESCOPIC\"}";
        mockMvc.perform(post("/api/agvs").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/agvs").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").exists());

        mockMvc.perform(post("/api/agvs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agvCode\":\" \",\"agvType\":\"STANDARD\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/agvs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agvCode\":\"" + "X".repeat(65) + "\",\"agvType\":\"STANDARD\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/agvs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agvCode\":\"" + UUID.randomUUID() + "\",\"agvType\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnNotFoundForUnknownAgv() throws Exception {
        mockMvc.perform(get("/api/agvs/{agvCode}", "TEST_AGV_" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").exists());
    }
}
