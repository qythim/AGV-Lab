package com.agvlab.rcs.pathfinding.dto;

import java.util.List;

public record FindPathResponse(
        String mapCode,
        List<String> pointCodes
) {}