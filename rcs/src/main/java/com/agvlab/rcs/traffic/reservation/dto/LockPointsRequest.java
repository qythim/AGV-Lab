package com.agvlab.rcs.traffic.reservation.dto;

import java.util.List;

public record LockPointsRequest(
    String mapCode,
    String agvCode,                   
    boolean isRelease,       // true =釋放點位, false = 申請點位擁有權         
    List<String> pathPoint
){}
