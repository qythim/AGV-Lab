package com.agvlab.rcs.pathfinding.controller;
 
import com.agvlab.rcs.pathfinding.dto.FindPathRequest;
import com.agvlab.rcs.pathfinding.dto.FindPathResponse;
import com.agvlab.rcs.pathfinding.service.FindPathService;

import org.springframework.web.bind.annotation.*;


@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/path")
public class FindPathController {

    private final FindPathService findPathService;

    public FindPathController(FindPathService findPathService) {
        this.findPathService = findPathService;
    }

    @PostMapping(value="/BFS")
    public FindPathResponse findPath_BFS (@RequestBody FindPathRequest request){
        return findPathService.findPath_BFS(request.mapCode(), request.startPointCode(), request.endPointCode());
    }

    @PostMapping(value="/Dijkstra")
    public FindPathResponse findPath_Dijkstra (@RequestBody FindPathRequest request){
        return findPathService.findPath_Dijkstra(request.mapCode(), request.startPointCode(), request.endPointCode());
    }

    @PostMapping(value="/Astar")
    public FindPathResponse findPath_Astar (@RequestBody FindPathRequest request){
        return findPathService.findPath_Astar(request.mapCode(), request.startPointCode(), request.endPointCode());
    }
}