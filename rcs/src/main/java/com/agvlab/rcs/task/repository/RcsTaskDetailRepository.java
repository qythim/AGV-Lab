package com.agvlab.rcs.task.repository;

import com.agvlab.rcs.task.entity.RcsTaskDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 搬運任務階段明細資料存取介面
 */
public interface RcsTaskDetailRepository extends JpaRepository<RcsTaskDetail, Long> {

    // 依階段順序查詢指定任務的所有明細
    List<RcsTaskDetail> findByTaskCodeOrderByStepSeqAsc(String taskCode);

    // 查詢指定任務的特定階段明細 (例如 PICKUP 或 DELIVERY)
    Optional<RcsTaskDetail> findByTaskCodeAndStepType(String taskCode, String stepType);
}

