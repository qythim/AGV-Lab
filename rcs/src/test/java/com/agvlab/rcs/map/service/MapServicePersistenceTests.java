package com.agvlab.rcs.map.service;

import com.agvlab.rcs.map.dto.MapDataDto;
import com.agvlab.rcs.map.dto.MapDataDto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 使用隔離測試資料庫驗證版本、參照及有向拓撲。 */
@SpringBootTest
@Transactional
class MapServicePersistenceTests {
    @Autowired MapService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MapContentStore store;
    @Autowired PlatformTransactionManager transactions;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rolledBackSaveDoesNotPublishNewRuntimeVersion() {
        String code = "COMMIT_" + UUID.randomUUID();
        var original = service.saveMapData(map(code));
        service.activate(code, 1);
        service.warmActiveMap();
        long firstId = service.getActiveGraph().contentId();
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).execute(status -> {
            service.saveMapData(new MapDataDto(original.map(), original.pointList(), List.of(), List.of()));
            throw new IllegalStateException("模擬交易回滾");
        }));
        assertEquals(firstId, service.getActiveGraph().contentId());
        assertEquals(1, service.versions(code).size());
        assertEquals(1, service.getActiveMapData().lineList().size());
    }

    @Test
    void savesDirectedLinesAndKeepsHistoricalVersions() {
        String code = "TEST_" + UUID.randomUUID();
        MapDataDto first = service.saveMapData(map(code));
        assertFalse(first.map().isActive());
        assertEquals(1, first.lineList().size());
        var graph = service.getGraph(code);
        assertEquals(1, graph.outgoingLines().get("P_01").size());
        assertEquals(0, graph.outgoingLines().get("P_02").size());
        MapDataDto second = service.saveMapData(new MapDataDto(first.map(), first.pointList(), List.of(), first.objects()));
        assertEquals(2, second.map().version());
        assertEquals(1, service.getVersion(code, 1).lineList().size());
        assertEquals(0, service.getMapData(code).lineList().size());
        assertEquals(2, service.versions(code).size());
        assertThrows(IllegalStateException.class, () -> service.saveMapData(first));
        assertEquals(2, service.versions(code).size());
    }

    @Test
    void acceptsEmptyMapAndNeverCreatesEdgesFromNearbyCoordinates() {
        String code = "TEST_" + UUID.randomUUID();
        var data = map(code);
        service.saveMapData(new MapDataDto(data.map(), data.pointList(), List.of(), List.of()));
        assertTrue(service.getGraph(code).outgoingLines().values().stream().allMatch(List::isEmpty));
        var current = service.getMapData(code);
        service.saveMapData(new MapDataDto(current.map(), List.of(), List.of(), List.of()));
        assertTrue(service.getMapData(code).pointList().isEmpty());
    }

    @Test
    void rejectsMissingEndpointsDuplicateCodesAndInvalidVehicleType() {
        String code = "TEST_" + UUID.randomUUID();
        var data = map(code);
        var invalid = new LineDto("L_01","P_01","MISSING","ALL",List.of("EMPTY"),1.0,true,List.of("FORWARD"));
        assertThrows(IllegalArgumentException.class, () -> service.saveMapData(
                new MapDataDto(data.map(),data.pointList(),List.of(invalid),List.of())));
        var wrongType = new LineDto("L_01","P_01","P_02","CARRIER",List.of("EMPTY"),1.0,true,List.of("FORWARD"));
        assertThrows(IllegalArgumentException.class, () -> service.saveMapData(
                new MapDataDto(data.map(),data.pointList(),List.of(wrongType),List.of())));
        assertThrows(IllegalArgumentException.class, () -> service.saveMapData(
                new MapDataDto(data.map(),List.of(data.pointList().getFirst(),data.pointList().getFirst()),List.of(),List.of())));
        assertThrows(IllegalArgumentException.class, () -> service.saveMapData(
                new MapDataDto(data.map(),data.pointList(),List.of(data.lineList().getFirst(),data.lineList().getFirst()),List.of())));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM rcs_map WHERE map_code=?", Integer.class, code));
    }

    @Test
    void blocksEditingAndMapSwitchWhileOccupied() {
        String code = "TEST_" + UUID.randomUUID();
        var saved = service.saveMapData(map(code));
        service.activate(code, saved.map().version());
        long id = service.getGraph(code).contentId();
        jdbc.update("""
                INSERT INTO rcs_agv(agv_code,agv_type,current_map_code,current_point,map_content_id)
                VALUES (?,'STANDARD',?,'P_01',?)
                """, code, code, id);
        assertThrows(IllegalStateException.class, () -> service.saveMapData(saved));
        String next = "TEST_" + UUID.randomUUID();
        service.saveMapData(map(next));
        assertThrows(IllegalStateException.class, () -> service.activate(next,1));
        assertEquals(code, service.getActiveMapData().map().mapCode());
        assertEquals(1, service.versions(code).size());
    }

    @Test
    void completedTaskRetainsOriginalContentWhenMapChanges() {
        String code = "TEST_" + UUID.randomUUID();
        var saved = service.saveMapData(map(code));
        long id = service.getGraph(code).contentId();
        jdbc.update("""
                INSERT INTO rcs_task(task_code,map_code,start_point,target_point,status,map_content_id)
                VALUES (?,?,'P_01','P_02','COMPLETED',?)
                """, code, code, id);
        service.saveMapData(new MapDataDto(saved.map(),List.of(),List.of(),List.of()));
        assertEquals(id, jdbc.queryForObject("SELECT map_content_id FROM rcs_task WHERE task_code=?", Long.class, code));
        assertEquals(2, service.getVersion(code,1).pointList().size());
    }

    @Test
    void activationDoesNotCreateVersionAndRejectsStaleVersion() {
        String code = "TEST_" + UUID.randomUUID();
        service.saveMapData(map(code));
        assertThrows(IllegalStateException.class, () -> service.activate(code,2));
        assertTrue(service.activate(code,1).map().isActive());
        assertEquals(1, service.versions(code).size());
        assertEquals(code,service.getActiveMapData().map().mapCode());
    }

    private MapDataDto map(String code) {
        return new MapDataDto(new MapInfo(code,"測試地圖",1,true,20.0,15.0,0.0,0.0,""),
                List.of(new PointDto("P_01","NORMAL","起點",1.0,2.0),new PointDto("P_02","NORMAL","終點",3.0,2.0)),
                List.of(new LineDto("L_01","P_01","P_02","TELESCOPIC",List.of("EMPTY"),0.5,false,List.of("REVERSE"))),
                List.of());
    }
}
