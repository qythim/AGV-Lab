package com.agvlab.rcs.agv.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import com.agvlab.rcs.agv.entity.RcsAgv;
import com.agvlab.rcs.agv.repository.RcsAgvRepository;
import com.agvlab.rcs.map.dto.MapDataDto;
import com.agvlab.rcs.map.dto.MapDataDto.*;
import java.util.List;
import com.agvlab.rcs.map.service.MapService;
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

    @Autowired
    private RcsAgvRepository agvRepository;

    @Autowired
    private MapService mapService;


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

    @Test
    void unplaceClearsPositionAndAllowsPlacementAgain() throws Exception {
        String mapCode = "TEST_MAP_" + UUID.randomUUID();
        String agvCode = "TEST_AGV_" + UUID.randomUUID();
        mapService.saveMapData(new MapDataDto(
                new MapInfo(mapCode, "測試地圖", 1, false, 20.0, 15.0, 0.0, 0.0, ""),
                List.of(new PointDto("P_01", "NORMAL", "測試點", 1.5, 2.5)), List.of(), List.of()));
        mockMvc.perform(post("/api/agvs").contentType(MediaType.APPLICATION_JSON)
                .content("{\"agvCode\":\"" + agvCode + "\",\"agvType\":\"STANDARD\"}"))
                .andExpect(status().isCreated());

        String placement = "{\"mapCode\":\"" + mapCode + "\",\"pointCode\":\"P_01\"}";
        mockMvc.perform(put("/api/agvs/{agvCode}/place", agvCode)
                .contentType(MediaType.APPLICATION_JSON).content(placement))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPoint").value("P_01"))
                .andExpect(jsonPath("$.angle").value(0.0));

        mockMvc.perform(delete("/api/agvs/{agvCode}/place", agvCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agvCode").value(agvCode));

        RcsAgv unplaced = agvRepository.findById(agvCode).orElseThrow();
        assertNull(unplaced.getCurrentMapCode());
        assertNull(unplaced.getCurrentPoint());
        assertNull(unplaced.getPosX());
        assertNull(unplaced.getPosY());
        assertEquals(0.0, unplaced.getAngle());

        mockMvc.perform(put("/api/agvs/{agvCode}/place", agvCode)
                .contentType(MediaType.APPLICATION_JSON).content(placement))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPoint").value("P_01"))
                .andExpect(jsonPath("$.angle").value(0.0));
    }

    @Test
    void rejectUnplaceWhenAgvIsRunningOrMissing() throws Exception {
        String mapCode = "TEST_MAP_" + UUID.randomUUID();
        String agvCode = "TEST_AGV_" + UUID.randomUUID();
        mapService.saveMapData(new MapDataDto(
                new MapInfo(mapCode, "測試地圖", 1, false, 20.0, 15.0, 0.0, 0.0, ""),
                List.of(new PointDto("P_01", "NORMAL", "測試點", 1.5, 2.5)), List.of(), List.of()));
        mockMvc.perform(post("/api/agvs").contentType(MediaType.APPLICATION_JSON)
                .content("{\"agvCode\":\"" + agvCode + "\",\"agvType\":\"STANDARD\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/agvs/{agvCode}/place", agvCode))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/agvs/{agvCode}/place", agvCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mapCode\":\"" + mapCode + "\",\"pointCode\":\"P_01\"}"))
                .andExpect(status().isOk());
        RcsAgv agv = agvRepository.findById(agvCode).orElseThrow();
        agv.setStatus("RUNNING");
        agvRepository.saveAndFlush(agv);

        mockMvc.perform(delete("/api/agvs/{agvCode}/place", agvCode))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").exists());
        assertEquals(mapCode, agvRepository.findById(agvCode).orElseThrow().getCurrentMapCode());

        mockMvc.perform(delete("/api/agvs/{agvCode}/place", "MISSING_" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
