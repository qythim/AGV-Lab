package com.agvlab.rcs.map.service;

import com.agvlab.rcs.map.dto.MapDataDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/**
 * 地圖解析與序列化服務：
 * 1. 將地圖 JSON 字串解析為 Java 實體資料傳輸物件 (MapDataDto)。
 * 2. 將地圖資料物件序列化為格式化 JSON 字串，以供前端 (Three.js) 渲染使用。
 */
@Service
public class MapParse {

    private final ObjectMapper objectMapper;

    public MapParse(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 函式 1：解析地圖 JSON 字串為 MapDataDto 物件
     *
     * @param jsonString 來自檔案上傳或 API 請求的地圖 JSON 字串
     * @return 包含地圖資訊、路徑點與 3D 物件的 MapDataDto
     */
    public MapDataDto parseJson(String jsonString) {
        try {
            return objectMapper.readValue(jsonString, MapDataDto.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("地圖 JSON 解析失敗: " + e.getMessage(), e);
        }
    }

    /**
     * 函式 2：將 MapDataDto 物件序列化為標準格式 JSON 字串
     *
     * @param mapData 地圖資料傳輸物件
     * @return 格式化後的 JSON 字串
     */
    public String toJson(MapDataDto mapData) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapData);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("地圖資料轉為 JSON 失敗: " + e.getMessage(), e);
        }
    }
}
