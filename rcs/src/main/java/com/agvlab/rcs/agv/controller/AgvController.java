package com.agvlab.rcs.agv.controller;

import com.agvlab.rcs.agv.dto.AgvResponse;
import com.agvlab.rcs.agv.dto.CreateAgvRequest;
import com.agvlab.rcs.agv.dto.PlaceAgvRequest;
import com.agvlab.rcs.agv.entity.RcsAgv;
import com.agvlab.rcs.agv.service.AgvService;
import com.agvlab.rcs.agv.vda5050.Vda5050MqttService;
import com.agvlab.rcs.pathfinding.dto.FindPathResponse;
import com.agvlab.rcs.pathfinding.service.FindPathService;
import com.agvlab.rcs.traffic.reservation.dto.ProgressUpdateRequest;
import com.agvlab.rcs.traffic.reservation.dto.ProgressUpdateResponse;
import com.agvlab.rcs.traffic.reservation.service.ReservationService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/agvs")
public class AgvController {

    private final AgvService agvService;
    private final FindPathService findPathService;
    private final ReservationService reservationService;
    private final Vda5050MqttService vda5050MqttService;
    public AgvController(
            AgvService agvService,
            FindPathService findPathService,
            ReservationService reservationService,
            Vda5050MqttService vda5050MqttService) {
        this.agvService = agvService;
        this.findPathService = findPathService;
        this.reservationService = reservationService;
        this.vda5050MqttService = vda5050MqttService;
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
        // 2. 註冊完整路徑至緩存，供後續 MQTT 狀態回報觸發滑動窗口使用
        vda5050MqttService.registerActivePath(request.agvCode(), fullPath);
        // 3. 申請起點安全視窗 (windowSize = 2)
        ProgressUpdateResponse progressRes = reservationService.updateProgress(
                new ProgressUpdateRequest(
                        request.mapCode(),
                        request.agvCode(),
                        request.startPoint(),
                        fullPath,
                        2
                )
        );
        // 4. 若允許通行，發送首段 VDA 5050 Order 至車輛
        if (progressRes.canMove() && !progressRes.holdingPoints().isEmpty()) {
            vda5050MqttService.sendOrder(
                    request.agvCode(),
                    "order_" + request.agvCode(),
                    progressRes.holdingPoints()
            );
        }
        return progressRes;
    }
}
