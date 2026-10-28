package com.agvlab.rcs.agv.repository;
import com.agvlab.rcs.agv.entity.RcsAgv;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RcsAgvRepository extends JpaRepository<RcsAgv, String> {
    List<RcsAgv> findByCurrentMapCode(String mapCode);
    boolean existsByCurrentMapCodeAndCurrentPoint(String mapCode, String pointCode);
}
