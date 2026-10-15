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
     * 取得系統內所有地圖清單 (供前端 MapManagementPage 表格列表與統計展示)
     *
     * @return 所有地圖主檔資料清單
     */
    @Transactional(readOnly = true)
    public List<RcsMap> getAllMaps() {
        return mapRepository.findAll();
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

    /**
     * 儲存或更新完整地圖資料 (包含地圖主檔、所有路徑點、所有 3D 物件)
     * 使用 @Transactional 確保原子性：任一步驟失敗即全面自動回滾 (Rollback)
     *
     * @param mapData 待儲存的 MapDataDto 封裝物件
     * @return 儲存完成後的 MapDataDto 封裝物件
     */
    @Transactional
    public MapDataDto saveMapData(MapDataDto mapData) {
        if (mapData == null || mapData.getMap() == null) {
            throw new IllegalArgumentException("地圖資料或地圖主檔物件不可為空");
        }

        RcsMap map = mapData.getMap();
        String mapCode = map.getMapCode();
        if (mapCode == null || mapCode.trim().isEmpty()) {
            throw new IllegalArgumentException("地圖代碼 (mapCode) 不可為空");
        }

        // 1. 若此地圖設定為啟用 (is_active = true)，先將系統內既有的啟用地圖改為停用，避免違反唯一索引限制
        if (Boolean.TRUE.equals(map.getIsActive())) {
            mapRepository.findByIsActiveTrue().ifPresent(existingActive -> {
                if (!existingActive.getMapCode().equals(mapCode)) {
                    existingActive.setIsActive(false);
                    mapRepository.save(existingActive);
                }
            });
        }

        // 2. 儲存地圖基本主檔資訊
        RcsMap savedMap = mapRepository.save(map);

        // 3. 清理舊路徑點並批次儲存新路徑點清單
        pointRepository.deleteByMapCode(mapCode);
        List<RcsPoint> savedPoints = List.of();
        if (mapData.getPoints() != null && !mapData.getPoints().isEmpty()) {
            for (RcsPoint pt : mapData.getPoints()) {
                if (pt.getId() == null) {
                    pt.setId(new com.agvlab.rcs.map.entity.RcsPointId());
                }
                pt.getId().setMapCode(mapCode);
            }
            savedPoints = pointRepository.saveAll(mapData.getPoints());
        }

        // 4. 清理舊 3D 物件並批次儲存新 3D 物件清單
        mapObjectRepository.deleteByMapCode(mapCode);
        List<RcsMapObject> savedObjects = List.of();
        if (mapData.getObjects() != null && !mapData.getObjects().isEmpty()) {
            for (RcsMapObject obj : mapData.getObjects()) {
                obj.setMapCode(mapCode);
                // 重設 ID 為 null，交由資料庫自增序列 (BIGSERIAL) 產生全新主鍵
                obj.setId(null);
            }
            savedObjects = mapObjectRepository.saveAll(mapData.getObjects());
        }

        return new MapDataDto(savedMap, savedPoints, savedObjects);
    }

    /**
     * 從原始 JSON 字串直接匯入並儲存完整地圖資料
     *
     * @param jsonString 地圖原始 JSON 字串 (來自檔案或請求主體)
     * @return 儲存後的 MapDataDto 封裝物件
     */
    @Transactional
    public MapDataDto importMapFromJson(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            throw new IllegalArgumentException("匯入的地圖 JSON 字串不可為空");
        }
        MapDataDto mapData = MapParseJson.parseJson(jsonString);
        return saveMapData(mapData);
    }
}
