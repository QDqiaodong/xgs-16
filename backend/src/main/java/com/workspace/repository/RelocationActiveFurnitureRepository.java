package com.workspace.repository;

import com.workspace.entity.RelocationActiveFurniture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface RelocationActiveFurnitureRepository extends JpaRepository<RelocationActiveFurniture, Long> {

    boolean existsByFurnitureId(Long furnitureId);

    List<RelocationActiveFurniture> findByBatchId(Long batchId);

    List<RelocationActiveFurniture> findByFurnitureIdIn(Collection<Long> furnitureIds);

    void deleteByBatchId(Long batchId);
}
