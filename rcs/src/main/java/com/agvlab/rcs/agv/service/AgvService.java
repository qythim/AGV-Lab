package com.agvlab.rcs.agv.service;
import org.springframework.stereotype.Service;
import com.agvlab.rcs.agv.repository.RcsAgvRepository;
import com.agvlab.rcs.agv.entity.RcsAgv;
import com.agvlab.rcs.agv.dto.AgvResponse;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class AgvService {
    private final RcsAgvRepository rcsAgvRepository;

    public AgvService(RcsAgvRepository rcsAgvRepository) {
        this.rcsAgvRepository = rcsAgvRepository;
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
                        agv.getStatus()
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
                agv.getStatus()
        );
    }
}
