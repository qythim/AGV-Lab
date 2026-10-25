package com.agvlab.rcs.agv.controller;

import com.agvlab.rcs.agv.dto.AgvResponse;
import com.agvlab.rcs.agv.dto.CreateAgvRequest;
import com.agvlab.rcs.agv.entity.RcsAgv;
import com.agvlab.rcs.agv.service.AgvService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/agvs")
public class AgvController {
    private final AgvService agvService;

    public AgvController(AgvService agvService) {
        this.agvService = agvService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgvResponse createAgv(@RequestBody CreateAgvRequest request) {
        RcsAgv agv = agvService.createAgv(
                request.agvCode(),
                request.agvName(),
                request.agvType()
        );

        return new AgvResponse(
                agv.getAgvCode(),
                agv.getAgvName(),
                agv.getAgvType(),
                agv.getStatus()
        );
    }

    @GetMapping
    public List<AgvResponse> getAllAgvs() {
        return agvService.getAllAgvs();
    }
    @GetMapping(value = "/{agvCode}")
    public AgvResponse getAgvByCode(@PathVariable String agvCode) {
        return agvService.getAgv(agvCode);
    }
}