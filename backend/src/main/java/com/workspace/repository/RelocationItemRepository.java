package com.workspace.repository;

import com.workspace.entity.RelocationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RelocationItemRepository extends JpaRepository<RelocationItem, Long> {

    List<RelocationItem> findByBatchIdOrderByIdAsc(Long batchId);

    List<RelocationItem> findByBatchIdAndResultStatusOrderByIdAsc(Long batchId, String resultStatus);

    boolean existsByBatchIdAndTargetStationCode(Long batchId, String targetStationCode);
}
