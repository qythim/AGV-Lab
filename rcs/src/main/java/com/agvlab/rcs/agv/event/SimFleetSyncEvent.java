package com.agvlab.rcs.agv.event;

import java.util.List;

/**
 * 模擬器車隊同步事件：當車輛於地圖上放置 (PLACE)、移出 (DESTROY) 或系統啟動時發布，
 * 由 Vda5050MqttService 轉發至 MQTT sim/fleet/control 與 sim/fleet/snapshot 主題。
 */
public record SimFleetSyncEvent(
        String action,
        String agvId,
        String mapId,
        String pointId,
        double x,
        double y,
        double theta,
        List<SimAgvItem> snapshot
) {
    /** 單台已放置於地圖上的車輛快照資料 */
    public record SimAgvItem(
            String agvId,
            String mapId,
            String pointId,
            double x,
            double y,
            double theta
    ) {}
}

