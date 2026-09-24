package com.workspace.repository;

import com.workspace.entity.RelocationItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RelocationItemRepository extends JpaRepository<RelocationItem, Long> {

    List<RelocationItem> findByBatchIdOrderBySortOrderAscIdAsc(Long batchId);

    @org.springframework.data.jpa.repository.Query("SELECT i.furnitureId FROM RelocationItem i WHERE i.batchId = :batchId")
    java.util.List<Long> findFurnitureIdsByBatchId(@org.springframework.data.repository.query.Param("batchId") Long batchId);

    long countByBatchIdAndStatus(Long batchId, String status);

    /**
     * 行锁加载条目：执行/重试链路的逐件幂等防线。
     * 两个管理员重复点击或超时重试时，同一件家具的处理在数据库行上串行化，
     * 成功态条目会被直接跳过，不会重复搬迁或重复生成台账。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM RelocationItem i WHERE i.id = :id")
    Optional<RelocationItem> findByIdForUpdate(@Param("id") Long id);
}
