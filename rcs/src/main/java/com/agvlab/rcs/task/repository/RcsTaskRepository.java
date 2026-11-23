package com.agvlab.rcs.task.repository;

import com.agvlab.rcs.task.entity.RcsTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 搬運任務資料存取介面
 */
public interface RcsTaskRepository extends JpaRepository<RcsTask, String> {

    // 依建立時間由早到晚查詢指定狀態的任務 (用於 FIFO 派發 PENDING 任務)
    List<RcsTask> findByStatusOrderByCreatedAtAsc(String status);

    // 依建立時間由新到舊列出所有任務
    List<RcsTask> findAllByOrderByCreatedAtDesc();

    // 查詢指定車輛目前執行中的任務
    Optional<RcsTask> findFirstByAgvCodeAndStatus(String agvCode, String status);
}

