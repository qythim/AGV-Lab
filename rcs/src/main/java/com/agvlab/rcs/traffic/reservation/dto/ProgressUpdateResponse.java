package com.agvlab.rcs.traffic.reservation.dto;

import java.util.List;

public record ProgressUpdateResponse(
    String agvCode,
    String currentPoint,
    boolean canMove,             // 是否能繼續前進?
    List<String> releasedPoints, // 已解鎖的點位
    List<String> holdingPoints   // 目前擁有權的點位
) {}