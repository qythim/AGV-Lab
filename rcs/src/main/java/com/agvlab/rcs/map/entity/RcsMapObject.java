package com.agvlab.rcs.map.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "rcs_map_object")
public class RcsMapObject extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "map_code", length = 64, nullable = false)
    private String mapCode;

    @Column(name = "object_type", length = 32, nullable = false)
    private String objectType;

    @Column(name = "pos_x", nullable = false)
    private Double posX;

    @Column(name = "pos_y", nullable = false)
    private Double posY;

    @Column(name = "pos_z", nullable = false)
    private Double posZ = 0.0;

    @Column(name = "rotation", nullable = false)
    private Double rotation = 0.0;

    @Column(name = "length", nullable = false)
    private Double length;

    @Column(name = "width", nullable = false)
    private Double width;

    @Column(name = "height", nullable = false)
    private Double height;

    @Column(name = "rack_levels", nullable = false)
    private Integer rackLevels = 1;

    public RcsMapObject() {}

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

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
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

    public Double getPosZ() {
        return posZ;
    }

    public void setPosZ(Double posZ) {
        this.posZ = posZ;
    }

    public Double getRotation() {
        return rotation;
    }

    public void setRotation(Double rotation) {
        this.rotation = rotation;
    }

    public Double getLength() {
        return length;
    }

    public void setLength(Double length) {
        this.length = length;
    }

    public Double getWidth() {
        return width;
    }

    public void setWidth(Double width) {
        this.width = width;
    }

    public Double getHeight() {
        return height;
    }

    public void setHeight(Double height) {
        this.height = height;
    }

    public Integer getRackLevels() {
        return rackLevels;
    }

    public void setRackLevels(Integer rackLevels) {
        this.rackLevels = rackLevels;
    }
}
