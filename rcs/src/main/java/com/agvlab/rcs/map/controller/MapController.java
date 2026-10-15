package com.agvlab.rcs.map.controller;

import com.agvlab.rcs.map.dto.MapDataDto;
import com.agvlab.rcs.map.entity.RcsMap;
import com.agvlab.rcs.map.service.MapService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 地圖 RESTful API 控制器 (MapController)：
 * 提供 HTTP 介面供瀏覽器或前端 (Three.js) 直接查詢完整地圖 JSON 資料。
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/maps")
public class MapController {

    private final MapService mapService;

    public MapController(MapService mapService) {
        this.mapService = mapService;
    }

    /**
     * 1. 取得系統內所有地圖清單 (供前端 MapManagementPage 表格列表與統計展示)
     * 瀏覽器測試網址：http://localhost:8080/api/maps
     *
     * @return 所有地圖主檔資料清單
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<RcsMap> getAllMaps() {
        return mapService.getAllMaps();
    }

    /**
     * 2. 依據地圖代碼取得地圖 JSON 資料
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

    /**
     * 3. 儲存或更新完整地圖 (接收標準 JSON 物件)
     * 供前端 Three.js 編輯器點擊「儲存地圖」時發送使用
     *
     * @param mapData 地圖傳輸物件 (包含 map, points, objects)
     * @return 儲存成功後的完整地圖資料
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public MapDataDto saveMap(@RequestBody MapDataDto mapData) {
        return mapService.saveMapData(mapData);
    }

    /**
     * 4. 原始 JSON 字串直接匯入地圖
     * 測試網址：POST http://localhost:8080/api/maps/import
     *
     * @param jsonString 原始 JSON 地圖文字內容
     * @return 匯入成功後的完整地圖資料
     */
    @PostMapping(value = "/import", consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE}, produces = MediaType.APPLICATION_JSON_VALUE)
    public MapDataDto importMap(@RequestBody String jsonString) {
        return mapService.importMapFromJson(jsonString);
    }
}
