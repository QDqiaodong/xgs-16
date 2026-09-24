package com.workspace.repository;

import com.workspace.entity.BindRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BindRecordRepository extends JpaRepository<BindRecord, Long>,
        JpaSpecificationExecutor<BindRecord> {

    List<BindRecord> findByFurnitureIdOrderByRecordTimeDesc(Long furnitureId);

    List<BindRecord> findByFurnitureCodeOrderByRecordTimeDesc(String furnitureCode);

    /** 搬迁台账幂等：同批次同家具最多一条 */
    Optional<BindRecord> findByRelocationBatchNoAndFurnitureId(String relocationBatchNo, Long furnitureId);

    List<BindRecord> findByRelocationBatchNoOrderByRecordTimeDesc(String relocationBatchNo);
}
