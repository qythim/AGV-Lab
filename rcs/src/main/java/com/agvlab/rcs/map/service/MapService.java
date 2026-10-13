package com.agvlab.rcs.map.service;

import com.agvlab.rcs.map.dto.MapDataDto;
import com.agvlab.rcs.map.entity.RcsMap;
import com.agvlab.rcs.map.entity.RcsMapObject;
import com.agvlab.rcs.map.entity.RcsPoint;
import com.agvlab.rcs.map.repository.RcsMapObjectRepository;
import com.agvlab.rcs.map.repository.RcsMapRepository;
import com.agvlab.rcs.map.repository.RcsPointRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * 地圖業務邏輯服務 (MapService)：
 * 負責從資料庫讀取地圖靜態資料，封裝為 MapDataDto，並呼叫 MapParseJson 序列化為 JSON。
 */
@Service
public class MapService {

    private final RcsMapRepository mapRepository;
    private final RcsPointRepository pointRepository;
    private final RcsMapObjectRepository mapObjectRepository;
    private final MapParseJson MapParseJson;

    public MapService(
            RcsMapRepository mapRepository,
            RcsPointRepository pointRepository,
            RcsMapObjectRepository mapObjectRepository,
            MapParseJson MapParseJson) {
        this.mapRepository = mapRepository;
        this.pointRepository = pointRepository;
        this.mapObjectRepository = mapObjectRepository;
        this.MapParseJson = MapParseJson;
    }

    /**
     * 依據地圖代碼取得完整的靜態地圖資料物件 (MapDataDto)
     *
     * @param mapCode 地圖代碼 (如 FACTORY_01)
     * @return 整合後的 MapDataDto 封裝物件
     */
    @Transactional(readOnly = true)
    public MapDataDto getMapData(String mapCode) {
        // 1. 查詢地圖基本資訊
        RcsMap map = mapRepository.findById(mapCode)
                .orElseThrow(() -> new RuntimeException("找不到指定的地圖代碼: " + mapCode));

        // 2. 查詢該地圖的所有路徑點
        List<RcsPoint> points = pointRepository.findByMapCode(mapCode);

        // 3. 查詢該地圖的所有 3D 物件
        List<RcsMapObject> objects = mapObjectRepository.findByMapCode(mapCode);

        // 4. 打包為 DTO 物件回傳
        return new MapDataDto(map, points, objects);
    }

    /**
     * 依據地圖代碼取得格式化 JSON 字串 (直接供前端 Three.js 呼叫使用)
     *
     * @param mapCode 地圖代碼
     * @return 完整地圖的 JSON 字串
     */
    @Transactional(readOnly = true)
    public String getMapJson(String mapCode) {
        MapDataDto mapData = getMapData(mapCode);
        return MapParseJson.toJson(mapData);
    }

    /**
     * 取得目前系統啟用的有效完整地圖資料物件
     *
     * @return 啟用狀態的地圖 MapDataDto
     */
    @Transactional(readOnly = true)
    public MapDataDto getActiveMapData() {
        RcsMap activeMap = mapRepository.findByIsActiveTrue()
                .orElseThrow(() -> new RuntimeException("目前系統中沒有處於啟用狀態的地圖"));

        return getMapData(activeMap.getMapCode());
    }

    /**
     * 取得目前系統啟用的地圖 JSON 字串
     *
     * @return 啟用狀態地圖的格式化 JSON 字串
     */
    @Transactional(readOnly = true)
    public String getActiveMapJson() {
        MapDataDto mapData = getActiveMapData();
        return MapParseJson.toJson(mapData);
    }
}
