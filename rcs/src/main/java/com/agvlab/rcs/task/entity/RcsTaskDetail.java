package com.agvlab.rcs.task.entity;

import com.agvlab.rcs.map.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 搬運任務階段明細實體 (對應資料表 rcs_task_detail)
 */
@Entity
@Table(name = "rcs_task_detail")
public class RcsTaskDetail extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_code", length = 64, nullable = false)
    private String taskCode;

    @Column(name = "step_seq", nullable = false)
    private Integer stepSeq;

    @Column(name = "step_type", length = 32, nullable = false)
    private String stepType;

    @Column(name = "agv_code", length = 64, nullable = false)
    private String agvCode;

    @Column(name = "from_point", length = 64, nullable = false)
    private String fromPoint;

    @Column(name = "to_point", length = 64, nullable = false)
    private String toPoint;

    @Column(name = "status", length = 32, nullable = false)
    private String status = "PENDING";

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    public RcsTaskDetail() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskCode() {
        return taskCode;
    }

    public void setTaskCode(String taskCode) {
        this.taskCode = taskCode;
    }

    public Integer getStepSeq() {
        return stepSeq;
    }

    public void setStepSeq(Integer stepSeq) {
        this.stepSeq = stepSeq;
    }

    public String getStepType() {
        return stepType;
    }

    public void setStepType(String stepType) {
        this.stepType = stepType;
    }

    public String getAgvCode() {
        return agvCode;
    }

    public void setAgvCode(String agvCode) {
        this.agvCode = agvCode;
    }

    public String getFromPoint() {
        return fromPoint;
    }

    public void setFromPoint(String fromPoint) {
        this.fromPoint = fromPoint;
    }

    public String getToPoint() {
        return toPoint;
    }

    public void setToPoint(String toPoint) {
        this.toPoint = toPoint;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(OffsetDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }
}

