package com.agvlab.rcs.map.dto;

import java.util.List;

/** 地圖文件與持久化實體分離；所有距離使用公尺。 */
public record MapDataDto(MapInfo map, List<PointDto> pointList, List<LineDto> lineList,
                         List<MapObjectDto> objects) {
    public MapDataDto {
        pointList = immutable(pointList);
        lineList = immutable(lineList);
        objects = immutable(objects);
    }
    private static <T> List<T> immutable(List<T> values) {
        if (values == null) return null;
        if (values.stream().anyMatch(java.util.Objects::isNull)) throw new IllegalArgumentException("清單項目不可為 null");
        return List.copyOf(values);
    }
    public record MapInfo(String mapCode, String mapName, Integer version, Boolean isActive,
                          Double width, Double height, Double originX, Double originY, String context) {}
    public record PointDto(String pointCode, String pointType, String pointName, Double x, Double y) {}
    public record LineDto(String lineCode, String startPointCode, String endPointCode,
                          String robotType, List<String> robotCarrierState, Double maxSpeed,
                          Boolean stoppable, List<String> allowedTravelModes) {
        public LineDto {
            robotCarrierState = immutable(robotCarrierState);
            allowedTravelModes = immutable(allowedTravelModes);
        }
    }
    public record MapObjectDto(Long id, String mapCode, String objectType, Double posX, Double posY,
                               Double posZ, Double rotation, Double length, Double width,
                               Double height, Integer rackLevels) {}
}
