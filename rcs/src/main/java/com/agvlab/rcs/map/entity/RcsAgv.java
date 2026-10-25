package com.agvlab.rcs.map.entity;

/**
 * 舊版實體類別相容橋接：
 * 實體已正式遷移至獨立模組 {@link com.agvlab.rcs.agv.entity.RcsAgv}。
 *
 * @deprecated 請改用 {@link com.agvlab.rcs.agv.entity.RcsAgv}
 */
@Deprecated
public class RcsAgv extends com.agvlab.rcs.agv.entity.RcsAgv {

    public RcsAgv() {
        super();
    }

    public RcsAgv(String agvCode, String agvType) {
        super(agvCode, agvType);
    }
}
