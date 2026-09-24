package com.workspace.repository;

import com.workspace.entity.RelocationBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RelocationBatchRepository extends JpaRepository<RelocationBatch, Long>,
        JpaSpecificationExecutor<RelocationBatch> {

    Optional<RelocationBatch> findByBatchNo(String batchNo);

    boolean existsByBatchNo(String batchNo);

    /**
     * 原子状态流转（CAS）：仅当当前状态在期望集合内时才更新。
     * 并发确认/执行时由数据库行锁兜底，防止重复流转。
     */
    @Modifying
    @Transactional
    @Query("UPDATE RelocationBatch b SET b.status = :toStatus, b.confirmedAt = :ts " +
            "WHERE b.id = :id AND b.status IN :expectStatuses")
    int casStatus(@Param("id") Long id,
                  @Param("toStatus") String toStatus,
                  @Param("expectStatuses") java.util.List<String> expectStatuses,
                  @Param("ts") LocalDateTime ts);

    @Modifying
    @Transactional
    @Query("UPDATE RelocationBatch b SET b.status = 'CANCELLED', b.revokedAt = :ts " +
            "WHERE b.id = :id AND b.status IN :expectStatuses")
    int casCancel(@Param("id") Long id,
                  @Param("expectStatuses") java.util.List<String> expectStatuses,
                  @Param("ts") LocalDateTime ts);

    @Modifying
    @Transactional
    @Query("UPDATE RelocationBatch b SET b.status = :toStatus, " +
            "b.executedAt = :now, b.confirmedAt = COALESCE(b.confirmedAt, :now) " +
            "WHERE b.id = :id AND b.status IN :expectStatuses")
    int casToRunning(@Param("id") Long id,
                     @Param("toStatus") String toStatus,
                     @Param("expectStatuses") java.util.List<String> expectStatuses,
                     @Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query("UPDATE RelocationBatch b SET b.status = :toStatus, b.completedAt = :now, " +
            "b.successCount = :successCount, b.failCount = :failCount " +
            "WHERE b.id = :id AND b.status IN :expectStatuses")
    int casFinish(@Param("id") Long id,
                  @Param("toStatus") String toStatus,
                  @Param("expectStatuses") java.util.List<String> expectStatuses,
                  @Param("successCount") int successCount,
                  @Param("failCount") int failCount,
                  @Param("now") LocalDateTime now);
}
