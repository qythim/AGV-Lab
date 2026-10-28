package com.agvlab.rcs.agv.repository;
import com.agvlab.rcs.agv.entity.RcsAgv;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface RcsAgvRepository extends JpaRepository<RcsAgv, String> {
    List<RcsAgv> findByCurrentMapCode(String mapCode);
    boolean existsByCurrentMapCodeAndCurrentPoint(String mapCode, String pointCode);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM rcs_task WHERE agv_code = :agvCode AND status IN ('PENDING', 'RUNNING'))", nativeQuery = true)
    boolean hasOpenTask(@Param("agvCode") String agvCode);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM rcs_lock_point WHERE agv_code = :agvCode)", nativeQuery = true)
    boolean hasPointLock(@Param("agvCode") String agvCode);
}
