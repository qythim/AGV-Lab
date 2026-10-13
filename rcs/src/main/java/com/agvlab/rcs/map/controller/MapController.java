package com.agvlab.rcs.map.controller;

import com.agvlab.rcs.map.dto.MapDataDto;
import com.agvlab.rcs.map.service.MapService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

/**
 * 地圖 RESTful API 控制器 (MapController)：
 * 提供 HTTP 介面供瀏覽器或前端 (Three.js) 直接查詢完整地圖 JSON 資料。
 */
@RestController
@RequestMapping("/api/maps")
public class MapController {

    private final MapService mapService;

    public MapController(MapService mapService) {
        this.mapService = mapService;
    }

    /**
     * 1. 依據地圖代碼取得地圖 JSON 資料
     * 瀏覽器測試網址：http://localhost:8080/api/maps/FACTORY_01
     *
     * @param mapCode 地圖代碼
     * @return 完整地圖資料物件 (Spring Boot 會自動序列化為 JSON)
     */
    @GetMapping(value = "/{mapCode}", produces = MediaType.APPLICATION_JSON_VALUE)
    public MapDataDto getMapByCode(@PathVariable String mapCode) {
        return mapService.getMapData(mapCode);
    }

    /**
     * 2. 取得目前系統啟用的有效完整地圖 JSON 資料
     * 瀏覽器測試網址：http://localhost:8080/api/maps/active
     *
     * @return 目前啟用地圖的完整 JSON
     */
    @GetMapping(value = "/active", produces = MediaType.APPLICATION_JSON_VALUE)
    public MapDataDto getActiveMap() {
        return mapService.getActiveMapData();
    }
}
