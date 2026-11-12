package com.agvlab.rcs.traffic.reservation.dto;

import java.util.List;

public record ProgressUpdateRequest(
    String mapCode,
    String agvCode,
    String currentPoint,
    List<String> fullPath,
    int windowSize
) {}