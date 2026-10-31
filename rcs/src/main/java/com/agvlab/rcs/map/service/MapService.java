package com.agvlab.rcs.map.service;

import com.agvlab.rcs.map.dto.MapDataDto;
import com.agvlab.rcs.map.dto.MapDataDto.MapInfo;
import com.agvlab.rcs.map.repository.MapDocumentRepository;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class MapService {
    private final MapDocumentRepository repository;
    private final MapParseJson jsonParser;
    private final MapDocumentValidator validator;
    private final MapContentStore contentStore;
    private volatile MapGraph activeGraph;

    public MapService(MapDocumentRepository repository, MapParseJson jsonParser,
                      MapDocumentValidator validator, MapContentStore contentStore) {
        this.repository = repository;
        this.jsonParser = jsonParser;
        this.validator = validator;
        this.contentStore = contentStore;
    }

    public void lockMapChanges() { repository.lockChanges(); }

    @Transactional(readOnly = true)
    public List<MapDataDto> getAllMaps() {
        return repository.mapCodes().stream().map(this::getMapData).toList();
    }

    @Transactional(readOnly = true)
    public MapDataDto getMapData(String mapCode) { return loadCurrent(mapCode).document(); }

    /** 讀取指定地圖；mapCode 為 null 時讀取目前啟用的地圖。 */
    private MapGraph loadCurrent(String mapCode) {
        var contentRow = repository.current(mapCode)
                .orElseThrow(() -> new NoSuchElementException(
                        mapCode == null ? "目前沒有啟用地圖" : "找不到地圖：" + mapCode));

        MapGraph cachedGraph = activeGraph;
        boolean sameVersionIsCached = cachedGraph != null
                && cachedGraph.contentId() == contentRow.id();
        if (contentRow.active() && sameVersionIsCached) return cachedGraph;

        String jsonText = contentStore.read(
            contentRow.id(), 
            contentRow.content(), 
            contentRow.sha256());

        MapDataDto parsedData = jsonParser.parseJson(jsonText);
        
        MapDataDto mapData = withMetadata(
            parsedData, 
            parsedData.map().version(), 
            contentRow.active());

        validator.validate(mapData);
        return MapGraph.from(contentRow.id(), mapData);
    }

    @Transactional(readOnly = true)
    public MapGraph getActiveGraph() {
        MapGraph snapshot = loadCurrent(null);
        boolean canUpdateCache = !TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        if (canUpdateCache) activeGraph = snapshot;
        return snapshot;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmActiveMap() {
        if (!repository.activeMapCodes().isEmpty()) getActiveGraph();
    }

    @Transactional(readOnly = true)
    public MapDataDto getActiveMapData() { return getActiveGraph().document(); }

    @Transactional(readOnly = true)
    public String getMapJson(String mapCode) { return jsonParser.toJson(getMapData(mapCode)); }

    @Transactional(readOnly = true)
    public String getActiveMapJson() { return jsonParser.toJson(getActiveMapData()); }

    @Transactional
    public MapDataDto saveMapData(MapDataDto input) {
        validator.validate(input);
        lockMapChanges();

        String mapCode = input.map().mapCode();
        var currentVersion = repository.findVersion(mapCode);
        int nextVersion;
        boolean remainsActive = false;
        if (currentVersion.isEmpty()) {
            nextVersion = 1;
            repository.createMap(mapCode, input.map().mapName());
        } else {
            if (!currentVersion.orElseThrow().equals(input.map().version())) {
                throw new IllegalStateException("地圖版本已變更，請重新載入後再儲存");
            }
            ensureUnused(mapCode);
            nextVersion = Math.addExact(currentVersion.orElseThrow(), 1);
            remainsActive = repository.isActive(mapCode);
        }

        MapDataDto savedData = withMetadata(input, nextVersion, remainsActive);
        String jsonText = jsonParser.toJson(withMetadata(savedData, nextVersion, false));
        byte[] compressedContent = contentStore.compress(jsonText);
        String contentHash = contentStore.digest(jsonText);
        long contentId = repository.appendContent(mapCode, nextVersion, compressedContent, contentHash);
        repository.updateCurrent(savedData.map(), contentId);

        MapGraph newGraph = MapGraph.from(contentId, savedData);
        afterCommit(() -> {
            contentStore.cache(contentId, jsonText);
            if (Boolean.TRUE.equals(savedData.map().isActive())) activeGraph = newGraph;
        });
        return savedData;
    }

    @Transactional
    public MapDataDto activate(String mapCode, int expectedVersion) {
        lockMapChanges();
        MapGraph graph = loadCurrent(mapCode);
        if (graph.document().map().version() != expectedVersion) {
            throw new IllegalStateException("地圖版本不一致，無法啟用");
        }
        for (String activeMapCode : repository.activeMapCodes()) {
            if (!activeMapCode.equals(mapCode)) ensureUnused(activeMapCode);
        }
        if (repository.hasVehicleVersionMismatch(mapCode, graph.contentId())) {
            throw new IllegalStateException("AGV 使用的地圖版本不一致");
        }
        repository.activate(mapCode);
        MapDataDto activatedData = withMetadata(graph.document(), expectedVersion, true);
        MapGraph activatedGraph = MapGraph.from(graph.contentId(), activatedData);
        afterCommit(() -> activeGraph = activatedGraph);
        return activatedData;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> versions(String mapCode) { return repository.versions(mapCode); }

    @Transactional(readOnly = true)
    public MapDataDto getVersion(String mapCode, int version) {
        var contentRow = repository.version(mapCode, version)
                .orElseThrow(() -> new NoSuchElementException("找不到指定的地圖版本"));
        String jsonText = contentStore.read(contentRow.id(), contentRow.content(), contentRow.sha256());
        return jsonParser.parseJson(jsonText);
    }

    @Transactional(readOnly = true)
    public MapGraph getGraph(String mapCode) { return loadCurrent(mapCode); }

    @Transactional
    public MapDataDto importMapFromJson(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) throw new IllegalArgumentException("JSON 不可為空");
        return saveMapData(jsonParser.parseJson(jsonText));
    }

    private void ensureUnused(String mapCode) {
        if (repository.isInUse(mapCode)) throw new IllegalStateException("地圖正在被 AGV 或任務使用，無法修改");
    }

    private MapDataDto withMetadata(MapDataDto data, int version, boolean active) {
        MapInfo oldInfo = data.map();
        MapInfo newInfo = new MapInfo(oldInfo.mapCode(), oldInfo.mapName(), version, active,
                oldInfo.width(), oldInfo.height(),
                oldInfo.originX() == null ? 0.0 : oldInfo.originX(),
                oldInfo.originY() == null ? 0.0 : oldInfo.originY(), oldInfo.context());
        return new MapDataDto(newInfo, data.pointList(), data.lineList(), data.objects());
    }

    private void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }
}
