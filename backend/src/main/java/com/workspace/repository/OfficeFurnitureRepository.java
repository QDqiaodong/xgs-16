package com.workspace.repository;

import com.workspace.entity.OfficeFurniture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OfficeFurnitureRepository extends JpaRepository<OfficeFurniture, Long>, JpaSpecificationExecutor<OfficeFurniture> {

    Optional<OfficeFurniture> findByFurnitureCode(String furnitureCode);

    List<OfficeFurniture> findByFloorNum(Integer floorNum);

    List<OfficeFurniture> findByStationCode(String stationCode);

    @Query("SELECT DISTINCT f.floorNum FROM OfficeFurniture f ORDER BY f.floorNum")
    List<Integer> findAllDistinctFloors();

    @Query("SELECT f.floorNum, COUNT(f) FROM OfficeFurniture f GROUP BY f.floorNum ORDER BY f.floorNum")
    List<Object[]> countByFloor();

    @Query("SELECT f.floorNum, SUM(CASE WHEN f.bindStatus = 1 THEN 1 ELSE 0 END), COUNT(f) FROM OfficeFurniture f GROUP BY f.floorNum ORDER BY f.floorNum")
    List<Object[]> countBindStatusByFloor();

    boolean existsByFurnitureCode(String furnitureCode);

    boolean existsByStationCodeAndIdNot(String stationCode, Long id);
}
