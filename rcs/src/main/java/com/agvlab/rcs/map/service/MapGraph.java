package com.agvlab.rcs.map.service;

import com.agvlab.rcs.map.dto.MapDataDto;
import com.agvlab.rcs.map.dto.MapDataDto.*;
import java.util.*;

/** 不可變拓撲快照；每次尋路自行保存 visited、cost 等狀態。 */
public record MapGraph(long contentId, MapDataDto document, Map<String, PointDto> nodeByCode,
                       Map<String, List<LineDto>> outgoingLines) {

    /** 解析後的合法路徑快照：包含地圖代碼、有序點位清單與相鄰路線清單 */
    public record ResolvedPath(String mapCode, List<PointDto> nodes, List<LineDto> edges) {}

    public static MapGraph from(long contentId, MapDataDto data) {
        Map<String, PointDto> nodes = new LinkedHashMap<>();
        Map<String, List<LineDto>> outgoing = new LinkedHashMap<>();
        data.pointList().forEach(p -> { nodes.put(p.pointCode(), p); outgoing.put(p.pointCode(), new ArrayList<>()); });
        data.lineList().forEach(l -> outgoing.get(l.startPointCode()).add(l));
        outgoing.replaceAll((key, lines) -> List.copyOf(lines));
        return new MapGraph(contentId, data, Map.copyOf(nodes), Map.copyOf(outgoing));
    }

    /** 將點位代碼清單解析為完整的點位與相鄰路線物件，若點位或路線不存在則立即拋出例外 */
    public ResolvedPath resolvePath(List<String> pointCodes) {
        if (pointCodes == null || pointCodes.isEmpty()) {
            throw new IllegalArgumentException("路徑點清單不可為空");
        }
        List<PointDto> nodes = new ArrayList<>(pointCodes.size());
        List<LineDto> edges = new ArrayList<>(Math.max(0, pointCodes.size() - 1));

        for (int i = 0; i < pointCodes.size(); i++) {
            String code = pointCodes.get(i);
            PointDto point = nodeByCode.get(code);
            if (point == null) {
                throw new IllegalArgumentException("地圖中找不到點位: " + code);
            }
            nodes.add(point);

            if (i + 1 < pointCodes.size()) {
                String nextCode = pointCodes.get(i + 1);
                LineDto edge = outgoingLines.getOrDefault(code, List.of()).stream()
                        .filter(line -> nextCode.equals(line.endPointCode()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "地圖中找不到合法路線: " + code + " -> " + nextCode));
                edges.add(edge);
            }
        }
        return new ResolvedPath(document.map().mapCode(), List.copyOf(nodes), List.copyOf(edges));
    }
}
