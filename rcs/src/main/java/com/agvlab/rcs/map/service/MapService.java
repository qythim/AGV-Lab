package com.agvlab.rcs.map.service;

import com.agvlab.rcs.map.dto.MapDataDto;
import com.agvlab.rcs.map.dto.MapDataDto.MapInfo;
import java.util.*;
import org.springframework.stereotype.Service;
import com.agvlab.rcs.map.repository.MapDocumentRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;

@Service
public class MapService {
    private final MapDocumentRepository repository;
    private final MapParseJson json;
    private final MapDocumentValidator validator;
    private final MapContentStore store;
    private volatile MapGraph activeGraph;

    public MapService(MapDocumentRepository repository, MapParseJson json, MapDocumentValidator validator, MapContentStore store) {
        this.repository = repository; this.json = json; this.validator = validator; this.store = store;
    }

    // 與放置 AGV 共用交易鎖，避免檢查參照後同時有人放車或切換版本。
    public void lockMapChanges() {
        repository.lockChanges();
    }

    @Transactional(readOnly = true)
    public List<MapDataDto> getAllMaps() {
        return repository.mapCodes()
                .stream().map(this::getMapData).toList();
    }

    @Transactional(readOnly = true)
    public MapDataDto getMapData(String mapCode) {
        return loadCurrent(mapCode).document();
    }

    private MapGraph loadCurrent(String mapCode) {
        var row = repository.current(mapCode).orElseThrow(() ->
                new NoSuchElementException(mapCode == null ? "目前沒有啟用地圖" : "找不到地圖：" + mapCode));
        MapGraph cached = activeGraph;
        if (row.active() && cached != null && cached.contentId() == row.id()) return cached;
        var data = json.parseJson(store.read(row.id(), row.content(), row.sha256()));
        var result = withMetadata(data, data.map().version(), row.active());
        validator.validate(result);
        return MapGraph.from(row.id(), result);
    }

    @Transactional(readOnly = true)
    public MapGraph getActiveGraph() {
        MapGraph snapshot = loadCurrent(null);
        // 未提交交易內的版本不可發布給其他請求。
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) activeGraph = snapshot;
        return snapshot;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmActiveMap() {
        if (!repository.activeMapCodes().isEmpty())
            getActiveGraph();
    }

    @Transactional(readOnly = true)
    public MapDataDto getActiveMapData() { return getActiveGraph().document(); }

    @Transactional(readOnly = true)
    public String getMapJson(String mapCode) { return json.toJson(getMapData(mapCode)); }

    @Transactional(readOnly = true)
    public String getActiveMapJson() { return json.toJson(getActiveMapData()); }

    @Transactional
    public MapDataDto saveMapData(MapDataDto input) {
        validator.validate(input);
        lockMapChanges();
        String code = input.map().mapCode();
        var currentVersion = repository.findVersion(code);
        int version;
        boolean active = false;
        if (currentVersion.isEmpty()) {
            version = 1;
            repository.createMap(code, input.map().mapName());
        } else {
            if (!currentVersion.orElseThrow().equals(input.map().version()))
                throw new IllegalStateException("地圖已被更新，請重新載入後再編輯");
            ensureUnused(code);
            version = Math.addExact(currentVersion.orElseThrow(), 1);
            active = repository.isActive(code);
        }
        MapDataDto saved = withMetadata(input, version, active);
        // 版本內容不儲存可變的啟用狀態；回傳時以主檔狀態覆蓋。
        String text = json.toJson(withMetadata(saved, version, false));
        long contentId = repository.appendContent(code, version, store.compress(text), store.digest(text));
        repository.updateCurrent(saved.map(), contentId);
        MapGraph graph = MapGraph.from(contentId, saved);
        afterCommit(() -> {
            store.cache(contentId, text);
            if (Boolean.TRUE.equals(saved.map().isActive())) activeGraph = graph;
        });
        return saved;
    }

    @Transactional
    public MapDataDto activate(String code, int expectedVersion) {
        lockMapChanges();
        MapGraph graph = loadCurrent(code);
        if (graph.document().map().version() != expectedVersion)
            throw new IllegalStateException("地圖版本已變更，請重新載入清單");
        var previous = repository.activeMapCodes();
        for (String old : previous) if (!old.equals(code)) ensureUnused(old);
        // 離線地圖也可能已放車；僅允許其參照目前版本。
        if (repository.hasVehicleVersionMismatch(code, graph.contentId()))
            throw new IllegalStateException("車輛參照的地圖版本不一致");
        repository.activate(code);
        MapDataDto result = withMetadata(graph.document(), expectedVersion, true);
        MapGraph online = MapGraph.from(graph.contentId(), result);
        afterCommit(() -> activeGraph = online);
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> versions(String code) {
        return repository.versions(code);
    }

    @Transactional(readOnly = true)
    public MapDataDto getVersion(String code, int version) {
        var row = repository.version(code, version).orElseThrow(() -> new NoSuchElementException("找不到指定地圖版本"));
        return json.parseJson(store.read(row.id(), row.content(), row.sha256()));
    }

    @Transactional(readOnly = true)
    public MapGraph getGraph(String code) { return loadCurrent(code); }

    @Transactional
    public MapDataDto importMapFromJson(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("地圖 JSON 不可為空");
        return saveMapData(json.parseJson(text));
    }

    private void ensureUnused(String code) {
        if (repository.isInUse(code)) throw new IllegalStateException("地圖仍有車輛、未完成任務或鎖定，請先移除後再修改或切換");
    }

    private MapDataDto withMetadata(MapDataDto data, int version, boolean active) {
        var m = data.map();
        return new MapDataDto(new MapInfo(m.mapCode(), m.mapName(), version, active, m.width(), m.height(),
                m.originX() == null ? 0.0 : m.originX(), m.originY() == null ? 0.0 : m.originY(), m.context()),
                data.pointList(), data.lineList(), data.objects());
    }

    private void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }
}
