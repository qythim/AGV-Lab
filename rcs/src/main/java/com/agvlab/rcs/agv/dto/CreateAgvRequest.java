package com.agvlab.rcs.agv.dto;


public record CreateAgvRequest(
        String agvCode,
        String agvName,
        String agvType
) {}