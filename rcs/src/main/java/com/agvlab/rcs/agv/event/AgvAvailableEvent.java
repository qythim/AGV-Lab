package com.agvlab.rcs.agv.event;

/**
 * 車輛可用事件：當車輛被放置到地圖上或恢復為 IDLE 待命狀態時發布，通知 TaskService 派發等待中的任務
 */
public record AgvAvailableEvent(String agvCode, String mapCode) {}

