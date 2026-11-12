package com.agvlab.rcs.traffic.reservation.service;

import com.agvlab.rcs.map.service.MapGraph;
import com.agvlab.rcs.map.service.MapService;
import com.agvlab.rcs.traffic.reservation.service.RedisService;
import com.agvlab.rcs.traffic.reservation.service.PointLockMemoryManager;
import com.agvlab.rcs.traffic.reservation.dto.LockPointsRequest;
import com.agvlab.rcs.traffic.reservation.dto.LockPointsResponse;
import com.agvlab.rcs.traffic.reservation.dto.ProgressUpdateRequest;
import com.agvlab.rcs.traffic.reservation.dto.ProgressUpdateResponse;

import org.springframework.stereotype.Service;
import java.util.List;
import java.util.ArrayList;

@Service    
public class ReservationService{
    private final MapService maps;
    private final RedisService redis;
    private final PointLockMemoryManager memoryManager;
    public ReservationService(MapService maps, RedisService redis, PointLockMemoryManager memoryManager) { 
        this.maps = maps; 
        this.redis = redis;
        this.memoryManager= memoryManager;
    }

    private MapGraph getValidActiveGraph(String mapCode){
        MapGraph graph = maps.getActiveGraph();
        if (graph == null) {
            throw new IllegalArgumentException("沒找到啟用的地圖");
        }
        String activeMapCode = graph.document().map().mapCode();
        if (!activeMapCode.equals(mapCode)) {
            throw new IllegalArgumentException("地圖代碼不相符，當前啟用地圖為: " + activeMapCode);
        }
        return graph;
    }

    public LockPointsResponse reserve(LockPointsRequest request){
        // MapGraph graph = getValidActiveGraph(request.mapCode()); // 以後升級會用到 MapGraph 的功能：
                                                                    // 1. 點位合法性驗證：防止車輛申請不存在於地圖中的無效點位。
                                                                    // 2. 動態交通決策：根據節點類型（十字路口、旋轉區、充電站）動態計算 isCanFollow。
        
                                                                    // 3. 拓撲連通性檢查：確保申請的路徑點符合路線方向（防止逆向行駛或跨點跳躍）。
        if (request.isRelease()) {
            return release(request); // 指向放棄預約點位
        }
        boolean memoryLocked = memoryManager.tryLockList(request.pathPoint(), request.agvCode());
        if(memoryLocked == false){
            return new LockPointsResponse(request.agvCode(), false,-1,List.of(), false);
        }
        List<String> lockedPoints = new ArrayList<>();
        boolean permit = true;
        int maxIndex = -1;
        boolean isCanFollow = true;
        
        try {
            for (int i = 0; i < request.pathPoint().size(); i++) {
                String point = request.pathPoint().get(i);
                String holder = redis.getPoint(point);
                // 1. 如果有其他車輛占據->停止
                if (holder != null && !holder.equals(request.agvCode())) {
                    permit = false;
                    maxIndex = i - 1;
                    break;
                }
                // 2. 如果還沒有人占據
                if (holder == null) {
                    boolean lockOk = redis.lockPoint(point, request.agvCode());
                    if (!lockOk) {
                        permit = false;
                        maxIndex = i - 1;
                        break;
                    }
                }
                // 3. 成功佔據, 寫入redis
                lockedPoints.add(point);
                redis.addAgvPoint(request.agvCode(), point);
                redis.addPointLock(point);
                maxIndex = i;
            }

            if (!permit && !lockedPoints.isEmpty()) {
                for (String p : lockedPoints) {
                    redis.unlockPoint(p);
                    redis.removeAgvPoint(request.agvCode(), p);
                    redis.removePointLock(p);
                }
                lockedPoints.clear();
            }

        }
        finally{
            memoryManager.release(request.pathPoint(), request.agvCode());
        }
        return new LockPointsResponse(request.agvCode(), permit,maxIndex,lockedPoints, isCanFollow);
    }

    private LockPointsResponse release(LockPointsRequest request){
        List<String> releasePoints = new ArrayList<>();
        boolean permit = true;
        int maxIndex = -1;
        boolean isCanFollow = true;
        for (int i = 0; i < request.pathPoint().size(); i++) {
            String point = request.pathPoint().get(i);
            String holder = redis.getPoint(point);
            if (holder != null && holder.equals(request.agvCode())) {
                redis.unlockPoint(point);
                redis.removeAgvPoint(request.agvCode(), point);
                redis.removePointLock(point);
                releasePoints.add(point);
            }
        }
        return new LockPointsResponse(request.agvCode(), true, releasePoints.size() - 1, releasePoints, true);
    }

    public ProgressUpdateResponse updateProgress(ProgressUpdateRequest request){
        int currentIndex = request.fullPath().indexOf(request.currentPoint());
        if (currentIndex == -1) {
            return new ProgressUpdateResponse(request.agvCode(), request.currentPoint(), false, List.of(), List.of());
        }

        int endIndex = Math.min(currentIndex + request.windowSize(), request.fullPath().size());
        List<String> windowPoints = request.fullPath().subList(currentIndex, endIndex);
        List<String> pointsToRelease = request.fullPath().subList(0, currentIndex);
        
        //釋放已經過的點位
        LockPointsRequest releaseReq = new LockPointsRequest(request.mapCode(), request.agvCode(), true, pointsToRelease);
        LockPointsResponse releaseResponse = release(releaseReq);

        // 依照windowSize 參數申請後續的點位
        LockPointsRequest reserveReq = new LockPointsRequest(request.mapCode(), request.agvCode(), false, windowPoints);
        LockPointsResponse reserveResponse = reserve(reserveReq);        
        boolean canMove= reserveResponse.resultPermit();

        return new ProgressUpdateResponse(
            request.agvCode(),
            request.currentPoint(),
            canMove,
            releaseResponse.pathPoint(),  // 已經成功釋放的點位
            reserveResponse.pathPoint()   // 當前成功鎖定並保持的安全點位
        );
    }
}