package com.agvlab.rcs.pathfinding.service;

import com.agvlab.rcs.map.service.MapGraph;
import com.agvlab.rcs.map.service.MapService;
import com.agvlab.rcs.map.dto.MapDataDto.PointDto;
import com.agvlab.rcs.map.dto.MapDataDto.LineDto;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.agvlab.rcs.pathfinding.dto.FindPathResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Queue;
import java.util.ArrayDeque;
import java.util.PriorityQueue;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Collections;
import java.util.Comparator;

import tools.jackson.databind.json.JsonMapper;



@Service
public class FindPathService {
    private static final Logger log =LoggerFactory.getLogger(FindPathService.class);
    private final MapService maps;
    
    public FindPathService(MapService maps) { this.maps = maps; }
    
    private double Pythagoras(PointDto p1, PointDto p2){
        double dx = p2.x() - p1.x();
        double dy = p2.y() - p1.y();
        double sum = Math.sqrt(dx*dx+dy*dy);
        return sum;  
    }

    private List<String> buildPath(Map<String, String> cameFrom, String start, String end) {
        List<String> path = new ArrayList<>();
        path.add(end);
        String current = cameFrom.get(end);
        path.add(current);
        while (!current.equals(start)) {
            current = cameFrom.get(current);
            path.add(current);
        }
        log.info("路徑搜尋成功: {}", path);
        Collections.reverse(path);

        return path;
    }

    private MapGraph getValidGraph(String mapCode, String startPointCode, String endPointCode) {
        MapGraph graph = maps.getGraph(mapCode);
        if (graph == null) {
            throw new IllegalArgumentException("Map not found: " + mapCode);
        }
        if (!graph.nodeByCode().containsKey(startPointCode) || !graph.nodeByCode().containsKey(endPointCode)) {
            throw new IllegalArgumentException("Start or end point not found in map: " + mapCode);
        }
        return graph;
    }

    public FindPathResponse findPath_BFS(String mapCode, String startPointCode, String endPointCode) {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        Map<String, String> cameFrom = new LinkedHashMap<>(); 
        List<String> path = new ArrayList<>();

        MapGraph graph = getValidGraph(mapCode, startPointCode, endPointCode);


        if (startPointCode.equals(endPointCode)) {
            path.add(startPointCode);
            return new FindPathResponse(mapCode, path);
        }

        visited.add(startPointCode);
        queue.add(startPointCode);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if(current.equals(endPointCode)){
                log.info("Queue : {}", queue);
                log.info("Set : {}", visited);
                log.info("Map Came From : {}", cameFrom);
                break;
            }
            for (LineDto line : graph.outgoingLines().get(current)) {
                String neighbor = line.endPointCode();
                if(!visited.contains(neighbor)){
                    visited.add(neighbor);
                    queue.add(neighbor);
                    cameFrom.put(neighbor, current);
                }
            }
        }
        if (cameFrom.containsKey(endPointCode) ){
            path=buildPath( cameFrom, startPointCode, endPointCode);
        }
       
        
        return new FindPathResponse(mapCode, path);
    }
    public FindPathResponse findPath_Dijkstra(String mapCode, String startPointCode, String endPointCode) {
        Map<String, Double> distance = new HashMap<>();
        record NodeCost(String nodeCode, double cost) {}
        Queue<NodeCost> pq = new PriorityQueue<>(Comparator.comparingDouble(NodeCost::cost));
        Map<String, String> cameFrom = new LinkedHashMap<>(); 
        List<String> path = new ArrayList<>();

        MapGraph graph = getValidGraph(mapCode, startPointCode, endPointCode);

        if (startPointCode.equals(endPointCode)) {
            path.add(startPointCode);
            return new FindPathResponse(mapCode, path);
        }
        distance.put(startPointCode,0.0);
        pq.add(new NodeCost(startPointCode,0.0));
        while (!pq.isEmpty()){
            NodeCost currentItem = pq.poll();
            String current = currentItem.nodeCode();
            if(current.equals(endPointCode)){
                log.info("Queue : {}", pq);
                log.info("Set : {}", distance);
                log.info("Map Came From : {}", cameFrom);
                break;
            }
            for (LineDto line : graph.outgoingLines().get(current)) {
                String neighbor = line.endPointCode();
                PointDto p1 = graph.nodeByCode().get(line.startPointCode());
                PointDto p2 = graph.nodeByCode().get(line.endPointCode());
                double lineLength= Pythagoras(p1, p2);
                double newDist= currentItem.cost() + lineLength;
                if(!distance.containsKey(neighbor) || newDist < distance.get(neighbor)){
                    distance.put(neighbor, newDist);
                    pq.add(new NodeCost(neighbor, newDist));
                    cameFrom.put(neighbor, current);
                }
            }
        }
        if (cameFrom.containsKey(endPointCode) ){
            path=buildPath( cameFrom, startPointCode, endPointCode);
        }

        return new FindPathResponse(mapCode, path);
    }


     public FindPathResponse findPath_Astar(String mapCode, String startPointCode, String endPointCode) {
        Map<String, Double> distance = new HashMap<>();
        record NodeCost(String nodeCode, double gCost, double fScore) {}
        Queue<NodeCost> pq = new PriorityQueue<>(Comparator.comparingDouble(NodeCost::fScore));
        Map<String, String> cameFrom = new LinkedHashMap<>(); 
        List<String> path = new ArrayList<>();

        MapGraph graph = getValidGraph(mapCode, startPointCode, endPointCode);

        if (startPointCode.equals(endPointCode)) {
            path.add(startPointCode);
            return new FindPathResponse(mapCode, path);
        }
        
        PointDto target = graph.nodeByCode().get(endPointCode);
        distance.put(startPointCode,0.0);
        PointDto startPoint = graph.nodeByCode().get(startPointCode);
        double startH = Pythagoras(startPoint, target);
        pq.add(new NodeCost(startPointCode, 0.0, startH));
        while (!pq.isEmpty()){
            NodeCost currentItem = pq.poll();
            String current = currentItem.nodeCode();
            if (currentItem.gCost() > distance.get(current)) {
                continue;
            }
            if(current.equals(endPointCode)){
                log.info("Queue : {}", pq);
                log.info("Set : {}", distance);
                log.info("Map Came From : {}", cameFrom);
                break;
            }
            for (LineDto line : graph.outgoingLines().get(current)) {
                String neighbor = line.endPointCode();
                PointDto p1 = graph.nodeByCode().get(line.startPointCode());
                PointDto p2 = graph.nodeByCode().get(line.endPointCode());
                double lineLength= Pythagoras(p1, p2);
                double newDist= currentItem.gCost() + lineLength;
                double fScore=  (Pythagoras(p2, target))+ newDist;
                if(!distance.containsKey(neighbor) || newDist < distance.get(neighbor)){
                    distance.put(neighbor, newDist);
                    pq.add(new NodeCost(neighbor, newDist, fScore));
                    cameFrom.put(neighbor, current);
                }
            }
        }
        if (cameFrom.containsKey(endPointCode) ){
            path=buildPath( cameFrom, startPointCode, endPointCode);
        }

        return new FindPathResponse(mapCode, path);
    }
}
