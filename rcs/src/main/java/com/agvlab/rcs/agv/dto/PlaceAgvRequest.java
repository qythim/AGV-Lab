package com.agvlab.rcs.agv.dto;


public record PlaceAgvRequest(
        String mapCode,
        String pointCode
) {}