package com.agvlab.rcs.map.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "rcs_map")
public class RcsMap extends BaseAuditEntity {

    @Id
    @Column(name = "map_code", length = 64, nullable = false)
    private String mapCode;

    @Column(name = "map_name", length = 128, nullable = false)
    private String mapName;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = false;

    @Column(name = "width", precision = 10, scale = 2)
    private BigDecimal width;

    @Column(name = "height", precision = 10, scale = 2)
    private BigDecimal height;

    @Column(name = "origin_x", precision = 10, scale = 2, nullable = false)
    private BigDecimal originX = BigDecimal.ZERO;

    @Column(name = "origin_y", precision = 10, scale = 2, nullable = false)
    private BigDecimal originY = BigDecimal.ZERO;

    /**
     * 地圖上下文擴充資訊 (長文本格式，儲存自訂配置或附加資訊)
     */
    @Column(name = "context", columnDefinition = "text")
    private String context;

    public RcsMap() {}

    public RcsMap(String mapCode, String mapName) {
        this.mapCode = mapCode;
        this.mapName = mapName;
    }

    public String getMapCode() {
        return mapCode;
    }

    public void setMapCode(String mapCode) {
        this.mapCode = mapCode;
    }

    public String getMapName() {
        return mapName;
    }

    public void setMapName(String mapName) {
        this.mapName = mapName;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public BigDecimal getWidth() {
        return width;
    }

    public void setWidth(BigDecimal width) {
        this.width = width;
    }

    public BigDecimal getHeight() {
        return height;
    }

    public void setHeight(BigDecimal height) {
        this.height = height;
    }

    public BigDecimal getOriginX() {
        return originX;
    }

    public void setOriginX(BigDecimal originX) {
        this.originX = originX;
    }

    public BigDecimal getOriginY() {
        return originY;
    }

    public void setOriginY(BigDecimal originY) {
        this.originY = originY;
    }

    /**
     * 取得地圖上下文長文本資訊
     *
     * @return 上下文字串
     */
    public String getContext() {
        return context;
    }

    /**
     * 設定地圖上下文長文本資訊
     *
     * @param context 上下文字串
     */
    public void setContext(String context) {
        this.context = context;
    }
}
