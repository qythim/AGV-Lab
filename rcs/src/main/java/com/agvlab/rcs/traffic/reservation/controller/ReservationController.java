package com.agvlab.rcs.traffic.reservation.controller;

import java.util.Set;
import org.springframework.web.bind.annotation.*;

import com.agvlab.rcs.traffic.reservation.service.RedisService;
import com.agvlab.rcs.traffic.reservation.service.ReservationService;
import com.agvlab.rcs.traffic.reservation.dto.LockPointsRequest;
import com.agvlab.rcs.traffic.reservation.dto.LockPointsResponse;
import com.agvlab.rcs.traffic.reservation.dto.ProgressUpdateRequest;
import com.agvlab.rcs.traffic.reservation.dto.ProgressUpdateResponse;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/reservation")
public class ReservationController {

    private final RedisService redis;
    private final ReservationService reservationService;

    public ReservationController(RedisService redis, ReservationService reservationService){
        this.redis = redis;
        this.reservationService = reservationService;
    }

    @GetMapping(value="/redis")
    public Set<String> getAll(){
        return redis.getAllKeys();
    }

    @PostMapping
    public LockPointsResponse reserve(@RequestBody LockPointsRequest request) {
        return reservationService.reserve(request);
    }

    @PostMapping("/progress")
    public ProgressUpdateResponse updateProgress(@RequestBody ProgressUpdateRequest request) {
        return reservationService.updateProgress(request);
    }
}
