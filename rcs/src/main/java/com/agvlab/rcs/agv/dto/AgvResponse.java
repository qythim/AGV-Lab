package com.agvlab.rcs.agv.dto;


public record AgvResponse(
        String agvCode,
        String agvName,
        String agvType,
        String status,
        String currentMapCode,
        String currentPoint,
        Double posX,
        Double posY,
        Double angle
) {}