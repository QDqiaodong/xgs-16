package com.workspace.repository;

import com.workspace.entity.RelocationBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface RelocationBatchRepository extends JpaRepository<RelocationBatch, Long>,
        JpaSpecificationExecutor<RelocationBatch> {

    Page<RelocationBatch> findByStatus(String status, Pageable pageable);
}
