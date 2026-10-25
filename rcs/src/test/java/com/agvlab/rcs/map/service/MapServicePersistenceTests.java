package com.agvlab.rcs.map.service;

import com.agvlab.rcs.map.dto.MapDataDto;
import com.agvlab.rcs.map.entity.RcsMap;
import com.agvlab.rcs.map.entity.RcsPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 使用真實 PostgreSQL 驗證外鍵；每項測試結束後回滾所有測試資料。 */
@SpringBootTest
@Transactional
class MapServicePersistenceTests {
    @Autowired MapService service;
    @Autowired JdbcTemplate jdbc;

    @Test
    void updatesReferencedPointWithoutDeletingItAndPreservesCreationTime() {
        String code = "TEST_" + UUID.randomUUID();
        MapDataDto original = service.saveMapData(map(code, point("P_01", 1)));
        OffsetDateTime createdAt = original.getPoints().getFirst().getCreatedAt();
        jdbc.update("INSERT INTO rcs_agv (agv_code, agv_type, current_map_code, current_point) VALUES (?, 'STANDARD', ?, 'P_01')",
                code, code);

        MapDataDto saved = service.saveMapData(map(code, point("P_01", 2), point("P_02", 3)));

        assertEquals(2, saved.getPoints().size());
        assertEquals(createdAt, saved.getPoints().getFirst().getCreatedAt());
        assertEquals(2.0, jdbc.queryForObject(
                "SELECT x FROM rcs_point WHERE map_code = ? AND point_code = 'P_01'", Double.class, code));
        assertEquals("P_01", jdbc.queryForObject(
                "SELECT current_point FROM rcs_agv WHERE agv_code = ?", String.class, code));
    }

    @Test
    void deletesOnlyOmittedUnreferencedPointsAndSupportsEmptyMap() {
        String code = "TEST_" + UUID.randomUUID();
        service.saveMapData(map(code, point("P_01", 1), point("P_02", 2)));
        service.saveMapData(map(code, point("P_02", 3), point("P_03", 4)));
        assertEquals(List.of("P_02", "P_03"), jdbc.queryForList(
                "SELECT point_code FROM rcs_point WHERE map_code = ? ORDER BY point_code", String.class, code));

        service.saveMapData(map(code));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM rcs_point WHERE map_code = ?", Integer.class, code));
    }

    @ParameterizedTest
    @ValueSource(strings = {"AGV", "TASK_START", "TASK_TARGET", "LOCK"})
    void rejectsDeletionForEachForeignKeyBeforeChangingMap(String referenceType) {
        String code = "TEST_" + UUID.randomUUID();
        service.saveMapData(map(code, point("P_01", 1), point("P_02", 2)));
        if (referenceType.equals("AGV")) {
            jdbc.update("INSERT INTO rcs_agv (agv_code, agv_type, current_map_code, current_point) VALUES (?, 'STANDARD', ?, 'P_01')",
                    code, code);
        } else if (referenceType.startsWith("TASK")) {
            // 已完成任務仍須保留參照完整性。
            jdbc.update("INSERT INTO rcs_task (task_code, map_code, start_point, target_point, status) VALUES (?, ?, ?, ?, 'COMPLETED')",
                    code, code, referenceType.equals("TASK_START") ? "P_01" : "P_02",
                    referenceType.equals("TASK_TARGET") ? "P_01" : "P_02");
        } else {
            jdbc.update("INSERT INTO rcs_agv (agv_code, agv_type) VALUES (?, 'STANDARD')", code);
            // 過期鎖定仍有外鍵，不能忽略。
            jdbc.update("INSERT INTO rcs_lock_point (map_code, point_code, agv_code, lock_type, locked_at, expire_at) VALUES (?, 'P_01', ?, 'RESERVED', CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP - INTERVAL '1 hour')",
                    code, code);
        }
        MapDataDto request = map(code, point("P_02", 99));
        request.getMap().setMapName("不應儲存的名稱");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.saveMapData(request));

        assertTrue(error.getMessage().contains("P_01"));
        assertEquals("測試倉庫", jdbc.queryForObject(
                "SELECT map_name FROM rcs_map WHERE map_code = ?", String.class, code));
        assertEquals(2.0, jdbc.queryForObject(
                "SELECT x FROM rcs_point WHERE map_code = ? AND point_code = 'P_02'", Double.class, code));
    }

    @Test
    void rejectsDuplicateCodesBeforeCreatingMap() {
        String code = "TEST_" + UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> service.saveMapData(map(code, point("P_01", 1), point("P_01", 2))));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM rcs_map WHERE map_code = ?", Integer.class, code));
    }

    @Test
    void switchesActiveMapWithoutViolatingUniqueIndex() {
        String code = "TEST_" + UUID.randomUUID();
        MapDataDto request = map(code);
        request.getMap().setIsActive(true);
        service.saveMapData(request);
        assertEquals(code, jdbc.queryForObject(
                "SELECT map_code FROM rcs_map WHERE is_active = true", String.class));
        String nextCode = "TEST_" + UUID.randomUUID();
        MapDataDto next = map(nextCode);
        next.getMap().setIsActive(true);
        service.saveMapData(next);
        assertEquals(nextCode, jdbc.queryForObject(
                "SELECT map_code FROM rcs_map WHERE is_active = true", String.class));
    }

    private MapDataDto map(String code, RcsPoint... points) {
        RcsMap map = new RcsMap(code, "測試倉庫");
        map.setWidth(BigDecimal.valueOf(20));
        map.setHeight(BigDecimal.valueOf(15));
        return new MapDataDto(map, List.of(points), List.of());
    }

    private RcsPoint point(String code, double x) {
        return new RcsPoint("IGNORED", code, x, 1.0, "NORMAL");
    }
}