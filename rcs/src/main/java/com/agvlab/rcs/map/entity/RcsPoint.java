package com.agvlab.rcs.map.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "rcs_point")
public class RcsPoint extends BaseAuditEntity {

    @EmbeddedId
    private RcsPointId id;

    @Column(name = "x", nullable = false)
    private Double x;

    @Column(name = "y", nullable = false)
    private Double y;

    @Column(name = "z", nullable = false)
    private Double z = 0.0;

    @Column(name = "point_type", length = 32, nullable = false)
    private String pointType;

    @Column(name = "heading_angle", nullable = false)
    private Double headingAngle = 0.0;

    @Column(name = "neighbors", columnDefinition = "jsonb", nullable = false)
    private String neighbors = "[]";

    @Column(name = "max_speed", precision = 4, scale = 2, nullable = false)
    private BigDecimal maxSpeed = new BigDecimal("1.50");

    @Column(name = "is_disabled", nullable = false)
    private Boolean isDisabled = false;

    public RcsPoint() {}

    public RcsPoint(RcsPointId id, Double x, Double y, String pointType) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.pointType = pointType;
    }

    public RcsPoint(String mapCode, String pointCode, Double x, Double y, String pointType) {
        this.id = new RcsPointId(mapCode, pointCode);
        this.x = x;
        this.y = y;
        this.pointType = pointType;
    }

    public RcsPointId getId() {
        return id;
    }

    public void setId(RcsPointId id) {
        this.id = id;
    }

    public String getMapCode() {
        return id != null ? id.getMapCode() : null;
    }

    public String getPointCode() {
        return id != null ? id.getPointCode() : null;
    }

    public Double getX() {
        return x;
    }

    public void setX(Double x) {
        this.x = x;
    }

    public Double getY() {
        return y;
    }

    public void setY(Double y) {
        this.y = y;
    }

    public Double getZ() {
        return z;
    }

    public void setZ(Double z) {
        this.z = z;
    }

    public String getPointType() {
        return pointType;
    }

    public void setPointType(String pointType) {
        this.pointType = pointType;
    }

    public Double getHeadingAngle() {
        return headingAngle;
    }

    public void setHeadingAngle(Double headingAngle) {
        this.headingAngle = headingAngle;
    }

    public String getNeighbors() {
        return neighbors;
    }

    public void setNeighbors(String neighbors) {
        this.neighbors = neighbors;
    }

    public BigDecimal getMaxSpeed() {
        return maxSpeed;
    }

    public void setMaxSpeed(BigDecimal maxSpeed) {
        this.maxSpeed = maxSpeed;
    }

    public Boolean getIsDisabled() {
        return isDisabled;
    }

    public void setIsDisabled(Boolean disabled) {
        isDisabled = disabled;
    }
}
