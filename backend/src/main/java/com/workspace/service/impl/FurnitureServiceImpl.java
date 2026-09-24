package com.workspace.service.impl;

import cn.hutool.core.util.StrUtil;
import com.workspace.dto.BindStationDTO;
import com.workspace.dto.FurnitureCreateDTO;
import com.workspace.dto.FurnitureUpdateDTO;
import com.workspace.dto.UnbindStationDTO;
import com.workspace.entity.BindRecord;
import com.workspace.entity.OfficeFurniture;
import com.workspace.repository.BindRecordRepository;
import com.workspace.repository.OfficeFurnitureRepository;
import com.workspace.service.FurnitureService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class FurnitureServiceImpl implements FurnitureService {

    private static final String SPEC_TEMPLATE_PREFIX = "furniture:spec:template:";

    private final OfficeFurnitureRepository furnitureRepository;
    private final BindRecordRepository bindRecordRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public FurnitureServiceImpl(OfficeFurnitureRepository furnitureRepository,
                                BindRecordRepository bindRecordRepository,
                                RedisTemplate<String, Object> redisTemplate) {
        this.furnitureRepository = furnitureRepository;
        this.bindRecordRepository = bindRecordRepository;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Transactional
    public OfficeFurniture create(FurnitureCreateDTO dto) {
        if (furnitureRepository.existsByFurnitureCode(dto.getFurnitureCode())) {
            throw new RuntimeException("桌椅编号已存在: " + dto.getFurnitureCode());
        }
        OfficeFurniture furniture = new OfficeFurniture();
        BeanUtils.copyProperties(dto, furniture);
        furniture.setBindStatus(0);
        if (StrUtil.isNotBlank(dto.getSpecTemplate())) {
            furniture.setSpecTemplate(dto.getSpecTemplate());
        }
        return furnitureRepository.save(furniture);
    }

    @Override
    @Transactional
    public OfficeFurniture update(FurnitureUpdateDTO dto) {
        OfficeFurniture furniture = furnitureRepository.findById(dto.getId())
                .orElseThrow(() -> new RuntimeException("家具不存在: " + dto.getId()));
        if (dto.getFurnitureType() != null) furniture.setFurnitureType(dto.getFurnitureType());
        if (dto.getStyleName() != null) furniture.setStyleName(dto.getStyleName());
        if (dto.getBrand() != null) furniture.setBrand(dto.getBrand());
        if (dto.getPrice() != null) furniture.setPrice(dto.getPrice());
        if (dto.getSizeSpec() != null) furniture.setSizeSpec(dto.getSizeSpec());
        if (dto.getRemark() != null) furniture.setRemark(dto.getRemark());
        if (dto.getImageUrl() != null) furniture.setImageUrl(dto.getImageUrl());
        if (dto.getSpecTemplate() != null) furniture.setSpecTemplate(dto.getSpecTemplate());
        if (dto.getFloorNum() != null) furniture.setFloorNum(dto.getFloorNum());
        if (dto.getAreaName() != null) furniture.setAreaName(dto.getAreaName());
        return furnitureRepository.save(furniture);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!furnitureRepository.existsById(id)) {
            throw new RuntimeException("家具不存在");
        }
        furnitureRepository.deleteById(id);
    }

    @Override
    public OfficeFurniture getById(Long id) {
        return furnitureRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("家具不存在"));
    }

    @Override
    public OfficeFurniture getByCode(String code) {
        return furnitureRepository.findByFurnitureCode(code)
                .orElseThrow(() -> new RuntimeException("家具不存在: " + code));
    }

    @Override
    public Page<OfficeFurniture> list(String keyword, Integer floorNum, Integer bindStatus, String furnitureType, Pageable pageable) {
        Specification<OfficeFurniture> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StrUtil.isNotBlank(keyword)) {
                String pattern = "%" + keyword + "%";
                predicates.add(cb.or(
                        cb.like(root.get("furnitureCode"), pattern),
                        cb.like(root.get("styleName"), pattern),
                        cb.like(root.get("stationCode"), pattern),
                        cb.like(root.get("employeeName"), pattern),
                        cb.like(root.get("brand"), pattern)
                ));
            }
            if (floorNum != null) {
                predicates.add(cb.equal(root.get("floorNum"), floorNum));
            }
            if (bindStatus != null) {
                predicates.add(cb.equal(root.get("bindStatus"), bindStatus));
            }
            if (StrUtil.isNotBlank(furnitureType)) {
                predicates.add(cb.equal(root.get("furnitureType"), furnitureType));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return furnitureRepository.findAll(spec, pageable);
    }

    @Override
    @Transactional
    public OfficeFurniture bindStation(BindStationDTO dto) {
        OfficeFurniture furniture = furnitureRepository.findById(dto.getFurnitureId())
                .orElseThrow(() -> new RuntimeException("家具不存在"));

        if (furnitureRepository.existsByStationCodeAndIdNot(dto.getStationCode(), dto.getFurnitureId())) {
            throw new RuntimeException("工位编号已被绑定: " + dto.getStationCode());
        }

        BindRecord record = new BindRecord();
        record.setFurnitureId(furniture.getId());
        record.setFurnitureCode(furniture.getFurnitureCode());
        record.setOperateType(furniture.getBindStatus() == 1 ? "REBIND" : "BIND");
        record.setOldStationCode(furniture.getStationCode());
        record.setOldEmployeeName(furniture.getEmployeeName());
        record.setOldDepartment(furniture.getDepartment());
        record.setNewStationCode(dto.getStationCode());
        record.setNewEmployeeName(dto.getEmployeeName());
        record.setNewDepartment(dto.getDepartment());
        record.setOperateReason(dto.getOperateReason());
        record.setOperatorName(dto.getOperatorName() != null ? dto.getOperatorName() : "系统管理员");
        bindRecordRepository.save(record);

        furniture.setStationCode(dto.getStationCode());
        furniture.setEmployeeName(dto.getEmployeeName());
        furniture.setDepartment(dto.getDepartment());
        furniture.setBindStatus(1);
        return furnitureRepository.save(furniture);
    }

    @Override
    @Transactional
    public OfficeFurniture unbindStation(UnbindStationDTO dto) {
        OfficeFurniture furniture = furnitureRepository.findById(dto.getFurnitureId())
                .orElseThrow(() -> new RuntimeException("家具不存在"));

        if (furniture.getBindStatus() == 0) {
            throw new RuntimeException("家具未绑定任何工位");
        }

        BindRecord record = new BindRecord();
        record.setFurnitureId(furniture.getId());
        record.setFurnitureCode(furniture.getFurnitureCode());
        record.setOperateType("UNBIND");
        record.setOldStationCode(furniture.getStationCode());
        record.setOldEmployeeName(furniture.getEmployeeName());
        record.setOldDepartment(furniture.getDepartment());
        record.setOperateReason(dto.getOperateReason());
        record.setOperatorName(dto.getOperatorName() != null ? dto.getOperatorName() : "系统管理员");
        bindRecordRepository.save(record);

        furniture.setStationCode(null);
        furniture.setEmployeeName(null);
        furniture.setDepartment(null);
        furniture.setBindStatus(0);
        return furnitureRepository.save(furniture);
    }

    @Override
    public List<OfficeFurniture> getByFloor(Integer floorNum) {
        return furnitureRepository.findByFloorNum(floorNum);
    }

    @Override
    public List<OfficeFurniture> getByStationCode(String stationCode) {
        return furnitureRepository.findByStationCode(stationCode);
    }

    @Override
    public Map<Integer, List<OfficeFurniture>> getGroupByFloor() {
        List<OfficeFurniture> all = furnitureRepository.findAll();
        return all.stream().collect(Collectors.groupingBy(
                OfficeFurniture::getFloorNum,
                TreeMap::new,
                Collectors.toList()
        ));
    }

    @Override
    public List<Integer> getAllFloors() {
        return furnitureRepository.findAllDistinctFloors();
    }

    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        long total = furnitureRepository.count();
        stats.put("total", total);

        List<OfficeFurniture> all = furnitureRepository.findAll();
        long bound = all.stream().filter(f -> f.getBindStatus() == 1).count();
        long unbound = total - bound;
        stats.put("bound", bound);
        stats.put("unbound", unbound);

        List<Integer> floors = furnitureRepository.findAllDistinctFloors();
        stats.put("floorCount", floors.size());
        stats.put("floors", floors);

        List<Map<String, Object>> floorStats = new ArrayList<>();
        for (Integer floor : floors) {
            Map<String, Object> fs = new HashMap<>();
            List<OfficeFurniture> floorFurnitures = all.stream()
                    .filter(f -> Objects.equals(f.getFloorNum(), floor))
                    .collect(Collectors.toList());
            fs.put("floor", floor);
            fs.put("total", floorFurnitures.size());
            fs.put("bound", floorFurnitures.stream().filter(f -> f.getBindStatus() == 1).count());
            fs.put("unbound", floorFurnitures.stream().filter(f -> f.getBindStatus() == 0).count());
            floorStats.add(fs);
        }
        stats.put("floorStats", floorStats);
        return stats;
    }

    @Override
    public List<BindRecord> getRecordsByFurnitureId(Long furnitureId) {
        return bindRecordRepository.findByFurnitureIdOrderByRecordTimeDesc(furnitureId);
    }

    @Override
    public List<BindRecord> getRecordsByFurnitureCode(String furnitureCode) {
        return bindRecordRepository.findByFurnitureCodeOrderByRecordTimeDesc(furnitureCode);
    }

    @Override
    public Page<BindRecord> pageRecords(String furnitureCode, String operateType, String keyword,
                                        String relocationBatchNo, Pageable pageable) {
        Specification<BindRecord> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StrUtil.isNotBlank(furnitureCode)) {
                predicates.add(cb.equal(root.get("furnitureCode"), furnitureCode.trim()));
            }
            if (StrUtil.isNotBlank(operateType)) {
                predicates.add(cb.equal(root.get("operateType"), operateType.trim()));
            }
            if (StrUtil.isNotBlank(relocationBatchNo)) {
                predicates.add(cb.equal(root.get("relocationBatchNo"), relocationBatchNo.trim()));
            }
            if (StrUtil.isNotBlank(keyword)) {
                String pattern = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("oldEmployeeName"), pattern),
                        cb.like(root.get("newEmployeeName"), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return bindRecordRepository.findAll(spec, pageable);
    }

    @Override
    public void saveSpecTemplateToCache(String templateKey, Map<String, String> spec) {
        String key = SPEC_TEMPLATE_PREFIX + templateKey;
        redisTemplate.opsForHash().putAll(key, spec);
        redisTemplate.expire(key, 30, TimeUnit.DAYS);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String> getSpecTemplateFromCache(String templateKey) {
        String key = SPEC_TEMPLATE_PREFIX + templateKey;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        Map<String, String> result = new LinkedHashMap<>();
        entries.forEach((k, v) -> result.put(String.valueOf(k), String.valueOf(v)));
        return result;
    }

    @Override
    public List<Map<String, Object>> getAllSpecTemplatesFromCache() {
        Set<String> keys = redisTemplate.keys(SPEC_TEMPLATE_PREFIX + "*");
        List<Map<String, Object>> templates = new ArrayList<>();
        if (keys != null) {
            for (String key : keys) {
                Map<String, Object> item = new HashMap<>();
                String templateName = key.substring(SPEC_TEMPLATE_PREFIX.length());
                item.put("name", templateName);
                Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
                Map<String, String> spec = new LinkedHashMap<>();
                entries.forEach((k, v) -> spec.put(String.valueOf(k), String.valueOf(v)));
                item.put("spec", spec);
                templates.add(item);
            }
        }
        return templates;
    }

    @Override
    public void deleteSpecTemplateFromCache(String templateKey) {
        String key = SPEC_TEMPLATE_PREFIX + templateKey;
        redisTemplate.delete(key);
    }
}
