package com.agvlab.rcs.map.service;

import com.agvlab.rcs.map.dto.MapDataDto;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class MapDocumentValidator {
    public void validate(MapDataDto data) {
        if (data == null || data.map() == null || data.pointList() == null || data.lineList() == null || data.objects() == null)
            throw new IllegalArgumentException("地圖必須包含 map、pointList、lineList、objects；空清單請使用 []");
        var map = data.map();
        code(map.mapCode());
        if (map.mapName() == null || map.mapName().isBlank() || map.mapName().length() > 128)
            throw new IllegalArgumentException("地圖名稱不可為空且不可超過 128 字元");
        if (map.version() == null || map.version() < 1) throw new IllegalArgumentException("地圖版本必須大於零");
        positive(map.width()); positive(map.height());
        if (map.originX() != null) finite(map.originX());
        if (map.originY() != null) finite(map.originY());
        Set<String> points = new HashSet<>();
        for (var point : data.pointList()) {
            code(point.pointCode()); finite(point.x()); finite(point.y());
            if (!points.add(point.pointCode())) throw new IllegalArgumentException("點位代碼重複：" + point.pointCode());
            if (!Set.of("NORMAL", "CHARGER", "RACK", "STATION").contains(Objects.toString(point.pointType(), "")))
                throw new IllegalArgumentException("點位類型無效");
        }
        Set<String> lines = new HashSet<>();
        for (var line : data.lineList()) {
            code(line.lineCode());
            if (!lines.add(line.lineCode())) throw new IllegalArgumentException("路線代碼重複：" + line.lineCode());
            if (!points.contains(line.startPointCode()) || !points.contains(line.endPointCode()))
                throw new IllegalArgumentException("路線端點不存在：" + line.lineCode());
            if (line.startPointCode().equals(line.endPointCode())) throw new IllegalArgumentException("路線起終點不可相同");
            if (!Set.of("ALL", "STANDARD", "TELESCOPIC").contains(Objects.toString(line.robotType(), "")))
                throw new IllegalArgumentException("路線車型只允許 ALL、STANDARD、TELESCOPIC");
            choices(line.robotCarrierState(), Set.of("EMPTY", "LOADED"));
            choices(line.allowedTravelModes(), Set.of("FORWARD", "REVERSE"));
            positive(line.maxSpeed());
            if (line.stoppable() == null) throw new IllegalArgumentException("stoppable 不可為空");
        }
        Set<Long> objects = new HashSet<>();
        for (var object : data.objects()) {
            if (object.id() == null || object.id() < 0 || !objects.add(object.id()))
                throw new IllegalArgumentException("物件必須有唯一非負整數 id");
            if (!map.mapCode().equals(object.mapCode())) throw new IllegalArgumentException("物件地圖代碼不一致");
            if (!Set.of("WALL", "RACK", "STATION", "CHARGER", "CONVEYOR").contains(Objects.toString(object.objectType(), "")))
                throw new IllegalArgumentException("物件類型無效");
            finite(object.posX()); finite(object.posY());
            if (object.posZ() != null) finite(object.posZ());
            if (object.rotation() != null) finite(object.rotation());
            positive(object.length()); positive(object.width()); positive(object.height());
            if (object.rackLevels() != null && object.rackLevels() < 1) throw new IllegalArgumentException("貨架層數必須大於零");
        }
    }
    private void code(String value) {
        if (value == null || value.isBlank() || !value.equals(value.strip()) || value.length() > 64)
            throw new IllegalArgumentException("代碼不可為空、包含首尾空白或超過 64 字元");
    }
    private void finite(Double value) {
        if (value == null || !Double.isFinite(value)) throw new IllegalArgumentException("座標與數值必須是有限數字");
    }
    private void positive(Double value) {
        finite(value);
        if (value <= 0) throw new IllegalArgumentException("尺寸與速度必須大於零");
    }
    private void choices(List<String> values, Set<String> allowed) {
        if (values == null || values.isEmpty() || !allowed.containsAll(values) || new HashSet<>(values).size() != values.size())
            throw new IllegalArgumentException("路線條件不得為空、重複或包含未知值");
    }
}
