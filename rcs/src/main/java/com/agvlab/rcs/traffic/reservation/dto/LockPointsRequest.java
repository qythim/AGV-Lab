package com.agvlab.rcs.traffic.reservation.dto;

import java.util.List;

public record LockPointsRequest(
    String mapCode,
    String agvCode,                   
    boolean isRelease,                
    List<String> pathPoint
){}
