package com.agvlab.rcs.map.repository;

import com.agvlab.rcs.map.entity.RcsMapObject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * 地圖 3D 障礙物與實體物件資料存取介面 (Repository)
 */
@Repository
public interface RcsMapObjectRepository extends JpaRepository<RcsMapObject, Long> {

    /**
     * 依據地圖代碼查詢所有 3D 物件清單 (如牆體、料架、充電樁等)
     *
     * @param mapCode 地圖代碼
     * @return 該地圖下的所有 3D 物件清單
     */
    List<RcsMapObject> findByMapCode(String mapCode);
}
