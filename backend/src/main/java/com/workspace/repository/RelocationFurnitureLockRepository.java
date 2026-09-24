package com.workspace.repository;

import com.workspace.entity.RelocationFurnitureLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RelocationFurnitureLockRepository extends JpaRepository<RelocationFurnitureLock, Long> {

    boolean existsByFurnitureId(Long furnitureId);

    List<RelocationFurnitureLock> findByBatchId(Long batchId);

    void deleteByBatchId(Long batchId);
}
