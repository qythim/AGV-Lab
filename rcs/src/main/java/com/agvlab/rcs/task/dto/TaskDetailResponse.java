package com.agvlab.rcs.task.dto;

import com.agvlab.rcs.task.entity.RcsTaskDetail;
import java.time.OffsetDateTime;

/**
 * 搬運任務階段明細回應 DTO
 */
public record TaskDetailResponse(
        Long id,
        String taskCode,
        Integer stepSeq,
        String stepType,
        String agvCode,
        String fromPoint,
        String toPoint,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt
) {
    public static TaskDetailResponse from(RcsTaskDetail detail) {
        return new TaskDetailResponse(
                detail.getId(),
                detail.getTaskCode(),
                detail.getStepSeq(),
                detail.getStepType(),
                detail.getAgvCode(),
                detail.getFromPoint(),
                detail.getToPoint(),
                detail.getStatus(),
                detail.getStartedAt(),
                detail.getFinishedAt()
        );
    }
}

