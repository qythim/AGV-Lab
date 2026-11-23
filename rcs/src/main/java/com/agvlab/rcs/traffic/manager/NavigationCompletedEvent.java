package com.agvlab.rcs.traffic.manager;

/**
 * 導航路段完成事件：當車輛抵達當前導航路徑的終點時由 TrafficManagerService 發布，供 TaskService 推進任務狀態
 */
public record NavigationCompletedEvent(String agvCode, String orderId, String arrivedPoint) {}

