package com.workspace.repository;

import com.workspace.entity.BindRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BindRecordRepository extends JpaRepository<BindRecord, Long> {

    List<BindRecord> findByFurnitureIdOrderByRecordTimeDesc(Long furnitureId);

    List<BindRecord> findByFurnitureCodeOrderByRecordTimeDesc(String furnitureCode);

    List<BindRecord> findByRelocationBatchIdOrderByRecordTimeDesc(Long relocationBatchId);

    Optional<BindRecord> findByRelocationItemId(Long relocationItemId);

    boolean existsByRelocationItemId(Long relocationItemId);
}
