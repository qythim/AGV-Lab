package com.agvlab.rcs.map.repository;

import com.agvlab.rcs.map.entity.RcsPoint;
import com.agvlab.rcs.map.entity.RcsPointId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * 地圖導航路徑點資料存取介面 (Repository)
 */
@Repository
public interface RcsPointRepository extends JpaRepository<RcsPoint, RcsPointId> {

    /**
     * 依據地圖代碼查詢所有路徑拓撲點清單
     *
     * @param mapCode 地圖代碼 (如 FACTORY_01)
     * @return 該地圖下的所有路徑點清單
     */
    @Query("SELECT p FROM RcsPoint p WHERE p.id.mapCode = :mapCode")
    List<RcsPoint> findByMapCode(@Param("mapCode") String mapCode);

    /**
     * 依據地圖代碼批次刪除該地圖下的所有既有點位 (用於地圖覆寫與匯入)
     *
     * @param mapCode 地圖代碼
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM RcsPoint p WHERE p.id.mapCode = :mapCode")
    void deleteByMapCode(@Param("mapCode") String mapCode);

    /** 查詢所有實際外鍵參照，不依車輛、任務狀態或鎖定到期時間過濾。 */
    @Query(value = """
            SELECT current_point FROM rcs_agv
            WHERE current_map_code = :mapCode AND current_point IS NOT NULL
            UNION
            SELECT start_point FROM rcs_task WHERE map_code = :mapCode
            UNION
            SELECT target_point FROM rcs_task WHERE map_code = :mapCode
            UNION
            SELECT point_code FROM rcs_lock_point WHERE map_code = :mapCode
            """, nativeQuery = true)
    List<String> findReferencedPointCodes(@Param("mapCode") String mapCode);
}
