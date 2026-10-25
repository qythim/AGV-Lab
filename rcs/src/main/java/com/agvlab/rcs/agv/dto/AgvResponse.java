package com.agvlab.rcs.agv.dto;


public record AgvResponse(
        String agvCode,
        String agvName,
        String agvType,
        String status
) {}