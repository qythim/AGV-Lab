package com.agvlab.rcs.agv.controller;

import com.agvlab.rcs.agv.dto.AgvResponse;
import com.agvlab.rcs.agv.dto.CreateAgvRequest;
import com.agvlab.rcs.agv.dto.PlaceAgvRequest;
import com.agvlab.rcs.agv.entity.RcsAgv;
import com.agvlab.rcs.agv.service.AgvService;
import com.agvlab.rcs.pathfinding.dto.FindPathResponse;
import com.agvlab.rcs.pathfinding.service.FindPathService;
import com.agvlab.rcs.traffic.manager.TrafficManagerService;
import com.agvlab.rcs.traffic.reservation.dto.ProgressUpdateResponse;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/agvs")
public class AgvController {

    private final AgvService agvService;
    private final FindPathService findPathService;
    private final TrafficManagerService trafficManagerService;

    public AgvController(
            AgvService agvService,
            FindPathService findPathService,
            TrafficManagerService trafficManagerService) {
        this.agvService = agvService;
        this.findPathService = findPathService;
        this.trafficManagerService = trafficManagerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgvResponse createAgv(@RequestBody CreateAgvRequest request) {
        RcsAgv agv = agvService.createAgv(
                request.agvCode(),
                request.agvName(),
                request.agvType()
        );

        return new AgvResponse(
            agv.getAgvCode(),
            agv.getAgvName(),
            agv.getAgvType(),
            agv.getStatus(),
            agv.getCurrentMapCode(),
            agv.getCurrentPoint(),
            agv.getPosX(),
            agv.getPosY(),
            agv.getAngle()
        );
    }

    @GetMapping
    public List<AgvResponse> getAllAgvs() {
        return agvService.getAllAgvs();
    }
    @GetMapping(value = "/{agvCode}")
    public AgvResponse getAgvByCode(@PathVariable String agvCode) {
        return agvService.getAgv(agvCode);
    }

    @PutMapping(value = "/{agvCode}/place")
    public AgvResponse placeAgv(@PathVariable String agvCode, @RequestBody PlaceAgvRequest request) {
        RcsAgv agv = agvService.placeAgv(agvCode, request);
        return new AgvResponse(
            agv.getAgvCode(),
            agv.getAgvName(),
            agv.getAgvType(),
            agv.getStatus(),
            agv.getCurrentMapCode(),
            agv.getCurrentPoint(),
            agv.getPosX(),
            agv.getPosY(),
            agv.getAngle()
        );
    }

    @DeleteMapping(value = "/{agvCode}/place")
    public AgvResponse unplaceAgv(@PathVariable String agvCode) {
        RcsAgv agv = agvService.unplaceAgv(agvCode);
        return new AgvResponse(
            agv.getAgvCode(),
            agv.getAgvName(),
            agv.getAgvType(),
            agv.getStatus(),
            agv.getCurrentMapCode(),
            agv.getCurrentPoint(),
            agv.getPosX(),
            agv.getPosY(),
            agv.getAngle()
        );
    }

     // 臨時派車測試請求 DTO (未來將由 TaskService 取代)
    public record DispatchRequest(String mapCode, String agvCode, String startPoint, String targetPoint) {}
    /**
     * 臨時派車測試端點：串聯 A* 尋路 -> 註冊路徑 -> 滑動窗口預約 -> 下發 VDA 5050 訂單
     */
    @PostMapping("/dispatch")
    public ProgressUpdateResponse dispatch(@RequestBody DispatchRequest request) {
        // 1. 透過 A* 演算法計算完整路徑
        FindPathResponse pathRes = findPathService.findPath_Astar(
                request.mapCode(), request.startPoint(), request.targetPoint());
        List<String> fullPath = pathRes.pointCodes();
        if (fullPath == null || fullPath.isEmpty()) {
            throw new IllegalArgumentException("找不到可行路徑");
        }
        // 2. 交由 TrafficManagerService 註冊路徑、申請起點安全視窗並視權限下發首段訂單或排隊
        return trafficManagerService.startNavigation(
                request.mapCode(),
                request.agvCode(),
                request.startPoint(),
                fullPath
        );
    }
}
