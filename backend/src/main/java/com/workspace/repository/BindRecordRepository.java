package com.workspace.repository;

import com.workspace.entity.BindRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BindRecordRepository extends JpaRepository<BindRecord, Long> {

    List<BindRecord> findByFurnitureIdOrderByRecordTimeDesc(Long furnitureId);

    List<BindRecord> findByFurnitureCodeOrderByRecordTimeDesc(String furnitureCode);
}
