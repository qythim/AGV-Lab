package com.agvlab.rcs.traffic.reservation.dto;

import java.util.List;

public record LockPointsResponse(
     String agvCode,
     boolean resultPermit,  
     int index,
     List<String> pathPoint,
     boolean isCanFollow
)
{}