package com.agvlab.rcs.map.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(
    name = "rcs_lock_point",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_map_point", columnNames = {"map_code", "point_code"})
    }
)
public class RcsLockPoint {

    @Column(name = "map_content_id", nullable = false)
    private Long mapContentId;

    public Long getMapContentId() { return mapContentId; }
    public void setMapContentId(Long value) { mapContentId = value; }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "map_code", length = 64, nullable = false)
    private String mapCode;

    @Column(name = "point_code", length = 64, nullable = false)
    private String pointCode;

    @Column(name = "agv_code", length = 64, nullable = false)
    private String agvCode;

    @Column(name = "task_id", length = 64)
    private String taskId;

    @Column(name = "lock_type", length = 32, nullable = false)
    private String lockType;

    @Column(name = "locked_at", nullable = false)
    private OffsetDateTime lockedAt = OffsetDateTime.now();

    @Column(name = "expire_at", nullable = false)
    private OffsetDateTime expireAt;

    public RcsLockPoint() {}

    public RcsLockPoint(String mapCode, String pointCode, String agvCode, String lockType, OffsetDateTime expireAt) {
        this.mapCode = mapCode;
        this.pointCode = pointCode;
        this.agvCode = agvCode;
        this.lockType = lockType;
        this.expireAt = expireAt;
    }

    @PrePersist
    protected void onCreate() {
        if (this.lockedAt == null) this.lockedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMapCode() {
        return mapCode;
    }

    public void setMapCode(String mapCode) {
        this.mapCode = mapCode;
    }

    public String getPointCode() {
        return pointCode;
    }

    public void setPointCode(String pointCode) {
        this.pointCode = pointCode;
    }

    public String getAgvCode() {
        return agvCode;
    }

    public void setAgvCode(String agvCode) {
        this.agvCode = agvCode;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getLockType() {
        return lockType;
    }

    public void setLockType(String lockType) {
        this.lockType = lockType;
    }

    public OffsetDateTime getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(OffsetDateTime lockedAt) {
        this.lockedAt = lockedAt;
    }

    public OffsetDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(OffsetDateTime expireAt) {
        this.expireAt = expireAt;
    }
}
