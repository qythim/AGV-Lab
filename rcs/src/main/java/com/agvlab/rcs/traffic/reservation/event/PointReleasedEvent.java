package com.agvlab.rcs.traffic.reservation.event;

import java.util.List;

// 點位釋放事件：通知 TrafficManager 有點位已解鎖，可喚醒排隊車輛
public record PointReleasedEvent(String agvCode, List<String> releasedPoints) {}