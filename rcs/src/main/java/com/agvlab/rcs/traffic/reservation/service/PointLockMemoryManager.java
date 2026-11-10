package com.agvlab.rcs.traffic.reservation.service;

import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;

@Component
public class PointLockMemoryManager {

    // 內存互斥鎖表：Key = 點位代碼, Value = AGV 代碼
    private final ConcurrentHashMap<String, String> pointLockMap = new ConcurrentHashMap<>();

    /**
     * 嘗試鎖定單一點位 (CPU CAS 原子操作)
     */
    public boolean tryLock(String point, String agvCode) {
        String existAgvCode = this.pointLockMap.putIfAbsent(point, agvCode);
        return existAgvCode == null || agvCode.equals(existAgvCode);
    }
    
    public boolean tryLockList(List<String> points, String agvCode) {
        List<String> lockedPoints = new ArrayList<>();
        for (String point : points){
            if(!tryLock(point, agvCode)){
                release(lockedPoints, agvCode);
                return false;
            }
           lockedPoints.add(point);
        }
        return true;
    }

    /**
     * 釋放內存中的點位鎖
     */
    public void release(List<String> points, String agvCode) {
        if (points == null || points.isEmpty()) {
            return;
        }
        for (String point : points) {
            this.pointLockMap.remove(point, agvCode);
        }
    }
}