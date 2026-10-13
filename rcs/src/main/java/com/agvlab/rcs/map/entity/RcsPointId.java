package com.agvlab.rcs.map.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class RcsPointId implements Serializable {

    @Column(name = "map_code", length = 64, nullable = false)
    private String mapCode;

    @Column(name = "point_code", length = 64, nullable = false)
    private String pointCode;

    public RcsPointId() {}

    public RcsPointId(String mapCode, String pointCode) {
        this.mapCode = mapCode;
        this.pointCode = pointCode;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RcsPointId that = (RcsPointId) o;
        return Objects.equals(mapCode, that.mapCode) && Objects.equals(pointCode, that.pointCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mapCode, pointCode);
    }
}
