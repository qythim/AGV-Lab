package com.agvlab.rcs.agv.vda5050;

// 車輛位置狀態回報事件：由 MQTT 接收層發布，供 TrafficManager 訂閱處理交通調度
public record AgvStateReportEvent(String mapCode, String agvCode, String currentPoint) {}

