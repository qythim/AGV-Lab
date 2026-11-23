package com.agvlab.rcs.task.dto;

/**
 * 建立搬運任務請求 DTO
 *
 * @param mapCode     地圖代碼
 * @param agvCode     指定車輛代碼 (若為 null 或空白，系統將自動指派距離起點最近的 IDLE 車輛)
 * @param startPoint  取貨點 / 任務起點代碼
 * @param targetPoint 卸貨點 / 任務終點代碼
 * @param targetLevel 目標貨架層級 (預設為 0)
 */
public record CreateTaskRequest(
        String mapCode,
        String agvCode,
        String startPoint,
        String targetPoint,
        Integer targetLevel
) {}

