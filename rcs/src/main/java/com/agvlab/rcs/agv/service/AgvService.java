package com.agvlab.rcs.agv.service;
import org.springframework.stereotype.Service;
import com.agvlab.rcs.agv.repository.RcsAgvRepository;
import com.agvlab.rcs.map.repository.RcsPointRepository;
import com.agvlab.rcs.map.entity.RcsPoint;
import com.agvlab.rcs.map.entity.RcsPointId;
import com.agvlab.rcs.agv.entity.RcsAgv;
import com.agvlab.rcs.agv.dto.AgvResponse;
import com.agvlab.rcs.agv.dto.PlaceAgvRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class AgvService {
    private final RcsAgvRepository rcsAgvRepository;
    private final RcsPointRepository rcsPointRepository;

    public AgvService(RcsAgvRepository rcsAgvRepository, RcsPointRepository rcsPointRepository) {
        this.rcsAgvRepository = rcsAgvRepository;
        this.rcsPointRepository = rcsPointRepository;   
    }

    public RcsAgv createAgv(String agvCode, String agvName, String agvType) {
        if (agvCode == null || agvCode.isBlank()) {
            throw new IllegalArgumentException("AGV 編碼不可為空");
        }
        String normalizedCode = agvCode.strip();
        if (normalizedCode.length() > 64) {
            throw new IllegalArgumentException("AGV 編碼不可超過 64 個字元");
        }
        if (!"STANDARD".equals(agvType) && !"TELESCOPIC".equals(agvType)) {
            throw new IllegalArgumentException("AGV 類型無效");
        }
        String normalizedName = agvName == null ? null : agvName.strip();
        if (normalizedName != null && normalizedName.length() > 128) {
            throw new IllegalArgumentException("AGV 名稱不可超過 128 個字元");
        }
        if (rcsAgvRepository.existsById(normalizedCode)) {
            throw new IllegalStateException("AGV " + normalizedCode + " 已存在");
        }

        RcsAgv agv = new RcsAgv(normalizedCode, agvType);
        agv.setAgvName(normalizedName);
        try {
            return rcsAgvRepository.saveAndFlush(agv);
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException("AGV " + normalizedCode + " 已存在", exception);
        }
    }


    @Transactional(readOnly = true)
    public List<AgvResponse> getAllAgvs() {
        List<RcsAgv> agvs = rcsAgvRepository.findAll();
        return agvs.stream()
                .map(agv -> new AgvResponse(
                        agv.getAgvCode(),
                        agv.getAgvName(),
                        agv.getAgvType(),
                        agv.getStatus(),
                        agv.getCurrentMapCode(),
                        agv.getCurrentPoint(),
                        agv.getPosX(),
                        agv.getPosY(),
                        agv.getAngle()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public AgvResponse getAgv(String agvCode) {
        RcsAgv agv = rcsAgvRepository.findById(agvCode)
                .orElseThrow(() -> new NoSuchElementException("找不到指定的 AGV 代碼: " + agvCode));
        return new AgvResponse(
                agv.getAgvCode(),
                agv.getAgvName(),
                agv.getAgvType(),
                agv.getStatus(),
                agv.getCurrentMapCode(),
                agv.getCurrentPoint(),
                agv.getPosX(),
                agv.getPosY(),
                agv.getAngle()
        );
    }
    @Transactional
    public RcsAgv placeAgv(String agvCode, PlaceAgvRequest placeAgvRequest) {
        if(placeAgvRequest == null) {
            throw new IllegalArgumentException("放置 AGV 的請求不可為空");
        }
        if (placeAgvRequest.mapCode() == null || placeAgvRequest.mapCode().isBlank()) {
            throw new IllegalArgumentException("地圖代碼不可為空");
        }
        if (placeAgvRequest.pointCode() == null || placeAgvRequest.pointCode().isBlank()) {
            throw new IllegalArgumentException("點位代碼不可為空");
        }
        RcsAgv agv = rcsAgvRepository.findById(agvCode)
            .orElseThrow(() -> new NoSuchElementException("找不到指定的 AGV 代碼: " + agvCode));

        if(agv.getCurrentMapCode() != null) {
            throw new IllegalStateException("AGV " + agvCode + " 已經在地圖上，無法重新放置");
        }
        RcsPoint point = rcsPointRepository.findById(
        new RcsPointId(placeAgvRequest.mapCode(), placeAgvRequest.pointCode())
            ).orElseThrow(() -> new NoSuchElementException("找不到指定的點位代碼"));

        if (Boolean.TRUE.equals(point.getIsDisabled())) {
            throw new IllegalStateException("點位已停用，無法放置 AGV");
        }
        if (rcsAgvRepository.existsByCurrentMapCodeAndCurrentPoint(placeAgvRequest.mapCode(), placeAgvRequest.pointCode())) {
            throw new IllegalStateException("點位已有 AGV，無法放置");
        }

        agv.setPosX(point.getX());
        agv.setPosY(point.getY());
        agv.setAngle(point.getHeadingAngle());
        agv.setCurrentMapCode(placeAgvRequest.mapCode());
        agv.setCurrentPoint(placeAgvRequest.pointCode());
        return rcsAgvRepository.saveAndFlush(agv);
    }

    @Transactional
    public RcsAgv unplaceAgv(String agvCode) {
        RcsAgv agv = rcsAgvRepository.findById(agvCode)
            .orElseThrow(() -> new NoSuchElementException("找不到指定的 AGV 代碼: " + agvCode));

        if (agv.getCurrentMapCode() == null) {
            throw new IllegalStateException("AGV " + agvCode + " 尚未放置在地圖上");
        }
        if (agv.getCurrentTaskId() != null || rcsAgvRepository.hasOpenTask(agvCode)) {
            throw new IllegalStateException("AGV " + agvCode + " 尚有未完成的任務，無法移出地圖");
        }
        if (!"IDLE".equals(agv.getStatus())) {
            throw new IllegalStateException("AGV " + agvCode + " 目前不是待命狀態，無法移出地圖");
        }
        if (rcsAgvRepository.hasPointLock(agvCode)) {
            throw new IllegalStateException("AGV " + agvCode + " 尚有點位鎖定，無法移出地圖");
        }

        agv.setCurrentMapCode(null);
        agv.setCurrentPoint(null);
        agv.setPosX(null);
        agv.setPosY(null);
        agv.setAngle(0.0);
        return rcsAgvRepository.saveAndFlush(agv);
    }
}
