package com.workspace.repository;

import com.workspace.entity.OfficeFurniture;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OfficeFurnitureRepository extends JpaRepository<OfficeFurniture, Long>, JpaSpecificationExecutor<OfficeFurniture> {

    Optional<OfficeFurniture> findByFurnitureCode(String furnitureCode);

    List<OfficeFurniture> findByFloorNum(Integer floorNum);

    List<OfficeFurniture> findByStationCode(String stationCode);

    /** 行锁加载家具，搬迁执行时防止与绑定/解绑/其他搬迁并发覆盖 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM OfficeFurniture f WHERE f.id = :id")
    Optional<OfficeFurniture> findByIdForUpdate(@Param("id") Long id);

    /**
     * 行锁加载占用某工位的全部家具。搬迁执行时先锁住目标工位上的家具行，
     * 使"目标工位被批次外家具占用"的判断与随后写入在数据库层面串行化。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM OfficeFurniture f WHERE f.stationCode = :stationCode")
    List<OfficeFurniture> findByStationCodeForUpdate(@Param("stationCode") String stationCode);

    @Query("SELECT DISTINCT f.floorNum FROM OfficeFurniture f ORDER BY f.floorNum")
    List<Integer> findAllDistinctFloors();

    @Query("SELECT f.floorNum, COUNT(f) FROM OfficeFurniture f GROUP BY f.floorNum ORDER BY f.floorNum")
    List<Object[]> countByFloor();

    @Query("SELECT f.floorNum, SUM(CASE WHEN f.bindStatus = 1 THEN 1 ELSE 0 END), COUNT(f) FROM OfficeFurniture f GROUP BY f.floorNum ORDER BY f.floorNum")
    List<Object[]> countBindStatusByFloor();

    boolean existsByFurnitureCode(String furnitureCode);

    boolean existsByStationCodeAndIdNot(String stationCode, Long id);
}
