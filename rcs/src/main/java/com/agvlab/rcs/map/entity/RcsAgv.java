package com.agvlab.rcs.map.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "rcs_agv")
public class RcsAgv extends BaseAuditEntity {

    @Id
    @Column(name = "agv_code", length = 64, nullable = false)
    private String agvCode;

    @Column(name = "agv_name", length = 128)
    private String agvName;

    @Column(name = "agv_type", length = 32, nullable = false)
    private String agvType;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "port")
    private Integer port;

    @Column(name = "current_map_code", length = 64)
    private String currentMapCode;

    @Column(name = "current_point", length = 64)
    private String currentPoint;

    @Column(name = "pos_x")
    private Double posX;

    @Column(name = "pos_y")
    private Double posY;

    @Column(name = "angle", nullable = false)
    private Double angle = 0.0;

    @Column(name = "lift_height", nullable = false)
    private Double liftHeight = 0.0;

    @Column(name = "battery_level", nullable = false)
    private Integer batteryLevel = 100;

    @Column(name = "status", length = 32, nullable = false)
    private String status = "IDLE";

    @Column(name = "current_task_id", length = 64)
    private String currentTaskId;

    public RcsAgv() {}

    public RcsAgv(String agvCode, String agvType) {
        this.agvCode = agvCode;
        this.agvType = agvType;
    }

    public String getAgvCode() {
        return agvCode;
    }

    public void setAgvCode(String agvCode) {
        this.agvCode = agvCode;
    }

    public String getAgvName() {
        return agvName;
    }

    public void setAgvName(String agvName) {
        this.agvName = agvName;
    }

    public String getAgvType() {
        return agvType;
    }

    public void setAgvType(String agvType) {
        this.agvType = agvType;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getCurrentMapCode() {
        return currentMapCode;
    }

    public void setCurrentMapCode(String currentMapCode) {
        this.currentMapCode = currentMapCode;
    }

    public String getCurrentPoint() {
        return currentPoint;
    }

    public void setCurrentPoint(String currentPoint) {
        this.currentPoint = currentPoint;
    }

    public Double getPosX() {
        return posX;
    }

    public void setPosX(Double posX) {
        this.posX = posX;
    }

    public Double getPosY() {
        return posY;
    }

    public void setPosY(Double posY) {
        this.posY = posY;
    }

    public Double getAngle() {
        return angle;
    }

    public void setAngle(Double angle) {
        this.angle = angle;
    }

    public Double getLiftHeight() {
        return liftHeight;
    }

    public void setLiftHeight(Double liftHeight) {
        this.liftHeight = liftHeight;
    }

    public Integer getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(Integer batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurrentTaskId() {
        return currentTaskId;
    }

    public void setCurrentTaskId(String currentTaskId) {
        this.currentTaskId = currentTaskId;
    }
}
