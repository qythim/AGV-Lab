package com.agvlab.rcs.map.service;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class MapControllerTests {
    @Autowired WebApplicationContext context;
    MockMvc mvc;
    @BeforeEach void setup() { mvc=MockMvcBuilders.webAppContextSetup(context).build(); }
    @Test void importsSavesActivatesAndReadsHistory() throws Exception {
        String code="HTTP_"+UUID.randomUUID();
        String payload="""
                {"map":{"mapCode":"%s","mapName":"測試","version":1,"isActive":true,"width":20,"height":15},
                 "pointList":[{"pointCode":"A","pointType":"NORMAL","x":1,"y":1},{"pointCode":"B","pointType":"NORMAL","x":2,"y":1}],
                 "lineList":[{"lineCode":"L","startPointCode":"A","endPointCode":"B","robotType":"STANDARD",
                 "robotCarrierState":["EMPTY"],"maxSpeed":1,"stoppable":false,"allowedTravelModes":["FORWARD"]}],"objects":[]}
                """.formatted(code);
        String saved=mvc.perform(post("/api/maps/import").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk()).andExpect(jsonPath("$.map.isActive").value(false))
                .andExpect(jsonPath("$.lineList.length()").value(1)).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/maps").contentType(MediaType.APPLICATION_JSON).content(saved))
                .andExpect(status().isOk()).andExpect(jsonPath("$.map.version").value(2));
        mvc.perform(post("/api/maps").contentType(MediaType.APPLICATION_JSON).content(saved))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").exists());
        mvc.perform(put("/api/maps/{code}/activate",code).contentType(MediaType.APPLICATION_JSON).content("{\"version\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.map.isActive").value(true));
        mvc.perform(get("/api/maps/active")).andExpect(status().isOk()).andExpect(jsonPath("$.map.mapCode").value(code));
        mvc.perform(get("/api/maps/{code}/versions/1",code)).andExpect(status().isOk()).andExpect(jsonPath("$.map.version").value(1));
        mvc.perform(get("/api/maps/{code}/versions",code)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        var malformed=JsonMapper.builder().build().readTree(saved).deepCopy();
        ((tools.jackson.databind.node.ObjectNode) malformed).putArray("pointList").addNull();
        mvc.perform(post("/api/maps").contentType(MediaType.APPLICATION_JSON).content(malformed.toString()))
                .andExpect(status().isBadRequest());
    }
    @Test void oldPayloadAndMalformedJsonAreRejected() throws Exception {
        mvc.perform(post("/api/maps/import").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/maps/import").contentType(MediaType.APPLICATION_JSON)
                .content("{\"map\":{\"mapCode\":\"OLD\"},\"points\":[],\"objects\":[]}"))
                .andExpect(status().isBadRequest());
    }
}
