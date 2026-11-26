package com.agvlab.rcs.seed;

import com.agvlab.rcs.map.dto.MapDataDto;
import com.agvlab.rcs.map.repository.MapDocumentRepository;
import com.agvlab.rcs.map.service.MapParseJson;
import com.agvlab.rcs.map.service.MapService;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 系統首次啟動預設地圖匯入器：
 * 當資料庫尚無任何地圖時，自動匯入並啟用內建的範例倉儲地圖 (warehouse_120.json)。
 */
@Component
public class DefaultMapSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultMapSeeder.class);
    private static final String SEED_MAP_PATH = "seed/warehouse_120.json";

    private final MapDocumentRepository repository;
    private final MapService mapService;
    private final MapParseJson jsonParser;

    public DefaultMapSeeder(MapDocumentRepository repository, MapService mapService, MapParseJson jsonParser) {
        this.repository = repository;
        this.mapService = mapService;
        this.jsonParser = jsonParser;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 若資料庫已有地圖則跳過，避免覆蓋使用者自行建立或修改的地圖
        if (!repository.mapCodes().isEmpty()) {
            return;
        }

        ClassPathResource resource = new ClassPathResource(SEED_MAP_PATH);
        if (!resource.exists()) {
            log.warn("找不到預設地圖範本檔案：{}", SEED_MAP_PATH);
            return;
        }

        try {
            String json = resource.getContentAsString(StandardCharsets.UTF_8);
            MapDataDto mapDto = jsonParser.parseJson(json);

            // 透過既有 MapService 執行驗證、GZIP 壓縮、SHA-256 雜湊與啟用流程
            MapDataDto saved = mapService.saveMapData(mapDto);
            mapService.activate(saved.map().mapCode(), saved.map().version());

            log.info("已自動匯入並啟用預設範例地圖：{} (版本 {})",
                    saved.map().mapCode(), saved.map().version());
        } catch (Exception e) {
            log.error("自動匯入預設範例地圖失敗", e);
        }
    }
}