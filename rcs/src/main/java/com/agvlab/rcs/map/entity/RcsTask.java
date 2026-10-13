package com.agvlab.rcs.map.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "rcs_task")
public class RcsTask extends BaseAuditEntity {

    @Id
    @Column(name = "task_code", length = 64, nullable = false)
    private String taskCode;

    @Column(name = "agv_code", length = 64)
    private String agvCode;

    @Column(name = "map_code", length = 64, nullable = false)
    private String mapCode;

    @Column(name = "start_point", length = 64, nullable = false)
    private String startPoint;

    @Column(name = "target_point", length = 64, nullable = false)
    private String targetPoint;

    @Column(name = "target_level", nullable = false)
    private Integer targetLevel = 0;

    @Column(name = "status", length = 32, nullable = false)
    private String status = "PENDING";

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    public RcsTask() {}

    public RcsTask(String taskCode, String mapCode, String startPoint, String targetPoint) {
        this.taskCode = taskCode;
        this.mapCode = mapCode;
        this.startPoint = startPoint;
        this.targetPoint = targetPoint;
    }

    public String getTaskCode() {
        return taskCode;
    }

    public void setTaskCode(String taskCode) {
        this.taskCode = taskCode;
    }

    public String getAgvCode() {
        return agvCode;
    }

    public void setAgvCode(String agvCode) {
        this.agvCode = agvCode;
    }

    public String getMapCode() {
        return mapCode;
    }

    public void setMapCode(String mapCode) {
        this.mapCode = mapCode;
    }

    public String getStartPoint() {
        return startPoint;
    }

    public void setStartPoint(String startPoint) {
        this.startPoint = startPoint;
    }

    public String getTargetPoint() {
        return targetPoint;
    }

    public void setTargetPoint(String targetPoint) {
        this.targetPoint = targetPoint;
    }

    public Integer getTargetLevel() {
        return targetLevel;
    }

    public void setTargetLevel(Integer targetLevel) {
        this.targetLevel = targetLevel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(OffsetDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }
}
