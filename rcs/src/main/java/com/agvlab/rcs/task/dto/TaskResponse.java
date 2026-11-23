package com.agvlab.rcs.task.dto;

import com.agvlab.rcs.task.entity.RcsTask;
import java.time.OffsetDateTime;

/**
 * 搬運任務回應 DTO
 */
public record TaskResponse(
        String taskCode,
        String agvCode,
        String mapCode,
        String startPoint,
        String targetPoint,
        Integer targetLevel,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime finishedAt
) {
    public static TaskResponse from(RcsTask task) {
        return new TaskResponse(
                task.getTaskCode(),
                task.getAgvCode(),
                task.getMapCode(),
                task.getStartPoint(),
                task.getTargetPoint(),
                task.getTargetLevel(),
                task.getStatus(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getFinishedAt()
        );
    }
}

