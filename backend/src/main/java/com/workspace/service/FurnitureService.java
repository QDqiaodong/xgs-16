package com.workspace.service;

import com.workspace.dto.BindStationDTO;
import com.workspace.dto.FurnitureCreateDTO;
import com.workspace.dto.FurnitureUpdateDTO;
import com.workspace.dto.UnbindStationDTO;
import com.workspace.entity.BindRecord;
import com.workspace.entity.OfficeFurniture;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface FurnitureService {

    OfficeFurniture create(FurnitureCreateDTO dto);

    OfficeFurniture update(FurnitureUpdateDTO dto);

    void delete(Long id);

    OfficeFurniture getById(Long id);

    OfficeFurniture getByCode(String code);

    Page<OfficeFurniture> list(String keyword, Integer floorNum, Integer bindStatus, String furnitureType, Pageable pageable);

    OfficeFurniture bindStation(BindStationDTO dto);

    OfficeFurniture unbindStation(UnbindStationDTO dto);

    List<OfficeFurniture> getByFloor(Integer floorNum);

    List<OfficeFurniture> getByStationCode(String stationCode);

    Map<Integer, List<OfficeFurniture>> getGroupByFloor();

    List<Integer> getAllFloors();

    Map<String, Object> getStatistics();

    List<BindRecord> getRecordsByFurnitureId(Long furnitureId);

    List<BindRecord> getRecordsByFurnitureCode(String furnitureCode);

    Page<BindRecord> pageRecords(String furnitureCode, String operateType, String keyword,
                                 String relocationBatchNo, Pageable pageable);

    void saveSpecTemplateToCache(String templateKey, Map<String, String> spec);

    Map<String, String> getSpecTemplateFromCache(String templateKey);

    List<Map<String, Object>> getAllSpecTemplatesFromCache();

    void deleteSpecTemplateFromCache(String templateKey);
}
