package com.agvlab.rcs.map.dto;

import com.agvlab.rcs.map.entity.RcsMap;
import com.agvlab.rcs.map.entity.RcsMapObject;
import com.agvlab.rcs.map.entity.RcsPoint;
import java.util.ArrayList;
import java.util.List;

/**
 * 靜態地圖資料傳輸物件 (DTO)：
 * 整合地圖核心設定、導航路徑點拓撲清單以及 3D 障礙物/物件清單。
 */
public class MapDataDto {

    private RcsMap map;
    private List<RcsPoint> points = new ArrayList<>();
    private List<RcsMapObject> objects = new ArrayList<>();

    public MapDataDto() {}

    public MapDataDto(RcsMap map, List<RcsPoint> points, List<RcsMapObject> objects) {
        this.map = map;
        this.points = points != null ? points : new ArrayList<>();
        this.objects = objects != null ? objects : new ArrayList<>();
    }

    public RcsMap getMap() {
        return map;
    }

    public void setMap(RcsMap map) {
        this.map = map;
    }

    public List<RcsPoint> getPoints() {
        return points;
    }

    public void setPoints(List<RcsPoint> points) {
        this.points = points;
    }

    public List<RcsMapObject> getObjects() {
        return objects;
    }

    public void setObjects(List<RcsMapObject> objects) {
        this.objects = objects;
    }
}
