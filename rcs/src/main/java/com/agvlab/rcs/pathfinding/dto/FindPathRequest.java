package com.agvlab.rcs.pathfinding.dto;


public record FindPathRequest(
        String mapCode,
        String startPointCode,
        String endPointCode
) {}