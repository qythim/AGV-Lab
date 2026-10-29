package com.agvlab.rcs.map.service;

import com.agvlab.rcs.map.dto.MapDataDto;
import com.agvlab.rcs.map.dto.MapDataDto.*;
import java.util.*;

/** 不可變拓撲快照；每次尋路自行保存 visited、cost 等狀態。 */
public record MapGraph(long contentId, MapDataDto document, Map<String, PointDto> nodeByCode,
                       Map<String, List<LineDto>> outgoingLines) {
    public static MapGraph from(long contentId, MapDataDto data) {
        Map<String, PointDto> nodes = new LinkedHashMap<>();
        Map<String, List<LineDto>> outgoing = new LinkedHashMap<>();
        data.pointList().forEach(p -> { nodes.put(p.pointCode(), p); outgoing.put(p.pointCode(), new ArrayList<>()); });
        data.lineList().forEach(l -> outgoing.get(l.startPointCode()).add(l));
        outgoing.replaceAll((key, lines) -> List.copyOf(lines));
        return new MapGraph(contentId, data, Map.copyOf(nodes), Map.copyOf(outgoing));
    }
}
