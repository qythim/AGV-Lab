package com.agvlab.rcs.map.repository;

import com.agvlab.rcs.map.entity.RcsMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * 地圖核心設定資料存取介面 (Repository)
 */
@Repository
public interface RcsMapRepository extends JpaRepository<RcsMap, String> {

    /**
     * 查詢目前啟用的唯一有效地圖
     *
     * @return 啟用的地圖實體 (Optional)
     */
    Optional<RcsMap> findByIsActiveTrue();
}
