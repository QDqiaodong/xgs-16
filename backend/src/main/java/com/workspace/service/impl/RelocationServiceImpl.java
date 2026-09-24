package com.workspace.service.impl;

import cn.hutool.core.util.StrUtil;
import com.workspace.common.RelocationFailCode;
import com.workspace.common.StationFloorRule;
import com.workspace.dto.RelocationBatchSaveDTO;
import com.workspace.dto.RelocationItemRequestDTO;
import com.workspace.dto.RelocationValidationVO;
import com.workspace.entity.OfficeFurniture;
import com.workspace.entity.RelocationActiveFurniture;
import com.workspace.entity.RelocationBatch;
import com.workspace.entity.RelocationItem;
import com.workspace.repository.OfficeFurnitureRepository;
import com.workspace.repository.RelocationActiveFurnitureRepository;
import com.workspace.repository.RelocationBatchRepository;
import com.workspace.repository.RelocationItemRepository;
import com.workspace.service.RelocationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RelocationServiceImpl implements RelocationService {

    private static final String EXEC_LOCK_PREFIX = "relocation:execute:lock:";

    private final RelocationBatchRepository batchRepository;
    private final RelocationItemRepository itemRepository;
    private final RelocationActiveFurnitureRepository lockRepository;
    private final OfficeFurnitureRepository furnitureRepository;
    private final RelocationItemExecutor itemExecutor;
    private final RedisTemplate<String, Object> redisTemplate;
    private final EntityManager entityManager;

    public RelocationServiceImpl(RelocationBatchRepository batchRepository,
                                 RelocationItemRepository itemRepository,
                                 RelocationActiveFurnitureRepository lockRepository,
                                 OfficeFurnitureRepository furnitureRepository,
                                 RelocationItemExecutor itemExecutor,
                                 RedisTemplate<String, Object> redisTemplate,
                                 EntityManager entityManager) {
        this.batchRepository = batchRepository;
        this.itemRepository = itemRepository;
        this.lockRepository = lockRepository;
        this.furnitureRepository = furnitureRepository;
        this.itemExecutor = itemExecutor;
        this.redisTemplate = redisTemplate;
        this.entityManager = entityManager;
    }

    // ============================ 创建 / 编辑 ============================

    @Override
    @Transactional
    public RelocationBatch createBatch(RelocationBatchSaveDTO dto) {
        normalize(dto);
        validateStructure(dto);

        List<Long> furnitureIds = dto.getItems().stream()
                .map(RelocationItemRequestDTO::getFurnitureId)
                .distinct()
                .toList();

        // 同家具不能同时处于两个未结束批次（锁表唯一约束强校验）
        List<RelocationActiveFurniture> blocked = lockRepository.findByFurnitureIdIn(furnitureIds);
        if (!blocked.isEmpty()) {
            throw new RuntimeException(buildLockedMessage(blocked));
        }

        // 目标工位必须属于迁入楼层（创建即拦截）
        for (RelocationItemRequestDTO it : dto.getItems()) {
            if (!StationFloorRule.matchesFloor(it.getTargetStationCode(), dto.getTargetFloorNum())) {
                throw new RuntimeException("目标工位[" + it.getTargetStationCode() + "]不属于迁入楼层"
                        + dto.getTargetFloorNum() + "层");
            }
        }

        Map<Long, OfficeFurniture> furnitureMap = furnitureRepository.findAllById(furnitureIds).stream()
                .collect(Collectors.toMap(OfficeFurniture::getId, f -> f));

        RelocationBatch batch = new RelocationBatch();
        batch.setBatchNo(generateBatchNo());
        applyDtoToBatch(batch, dto);
        batch.setStatus(RelocationBatch.STATUS_PENDING);
        batch.setTotalCount(dto.getItems().size());
        batch.setSuccessCount(0);
        batch.setFailCount(0);
        batch = batchRepository.save(batch);

        List<RelocationActiveFurniture> locks = new ArrayList<>();
        int order = 0;
        for (RelocationItemRequestDTO req : dto.getItems()) {
            OfficeFurniture f = furnitureMap.get(req.getFurnitureId());
            if (f == null) {
                throw new RuntimeException("家具不存在: " + req.getFurnitureId());
            }
            RelocationItem item = new RelocationItem();
            item.setBatchId(batch.getId());
            item.setFurnitureId(f.getId());
            item.setFurnitureCode(f.getFurnitureCode());
            // 保留创建时的原绑定快照，后续撤回/重试期间始终可追溯
            item.setSnapshotFloorNum(f.getFloorNum());
            item.setSnapshotStationCode(f.getStationCode());
            item.setSnapshotEmployeeName(f.getEmployeeName());
            item.setSnapshotDepartment(f.getDepartment());
            item.setSnapshotBindStatus(f.getBindStatus());
            item.setTargetStationCode(req.getTargetStationCode());
            item.setTargetEmployeeName(req.getTargetEmployeeName());
            item.setTargetDepartment(req.getTargetDepartment());
            item.setStatus(RelocationItem.STATUS_PENDING);
            item.setSortOrder(order++);
            itemRepository.save(item);
            batch.getItems().add(item);

            RelocationActiveFurniture lock = new RelocationActiveFurniture();
            lock.setFurnitureId(f.getId());
            lock.setBatchId(batch.getId());
            locks.add(lock);
        }
        // 批量插入锁；家具ID唯一约束在并发建批次时由数据库拒绝
        try {
            lockRepository.saveAll(locks);
            lockRepository.flush();
        } catch (Exception e) {
            throw new RuntimeException("部分家具已存在于其他未结束批次中，请刷新后重新选择");
        }
        return getDetail(batch.getId());
    }

    @Override
    @Transactional
    public RelocationBatch updateBatch(Long id, RelocationBatchSaveDTO dto) {
        normalize(dto);
        validateStructure(dto);
        RelocationBatch batch = getRequired(id);
        if (!RelocationBatch.STATUS_PENDING.equals(batch.getStatus())) {
            throw new RuntimeException("当前状态为" + statusText(batch.getStatus())
                    + "，批次内容不可直接改动；如需调整请先撤回确认");
        }

        List<RelocationItemRequestDTO> requests = dto.getItems();
        List<Long> newFurnitureIds = requests.stream()
                .map(RelocationItemRequestDTO::getFurnitureId).distinct().toList();

        for (RelocationItemRequestDTO it : requests) {
            if (!StationFloorRule.matchesFloor(it.getTargetStationCode(), dto.getTargetFloorNum())) {
                throw new RuntimeException("目标工位[" + it.getTargetStationCode() + "]不属于迁入楼层"
                        + dto.getTargetFloorNum() + "层");
            }
        }

        List<RelocationItem> existingItems = itemRepository.findByBatchIdOrderBySortOrderAscIdAsc(id);
        Map<Long, RelocationItem> existingByFurniture = existingItems.stream()
                .collect(Collectors.toMap(RelocationItem::getFurnitureId, i -> i));

        Set<Long> retainedFurnitureIds = new HashSet<>(newFurnitureIds);
        // 被移除的家具：释放在途占用
        List<RelocationActiveFurniture> batchLocks = lockRepository.findByBatchId(id);
        for (RelocationActiveFurniture lock : batchLocks) {
            if (!retainedFurnitureIds.contains(lock.getFurnitureId())) {
                lockRepository.delete(lock);
            }
        }
        // 删除被移除的条目
        for (RelocationItem item : existingItems) {
            if (!retainedFurnitureIds.contains(item.getFurnitureId())) {
                itemRepository.delete(item);
            }
        }
        entityManager.flush();

        // 新加入家具的在途占用校验
        for (Long fid : newFurnitureIds) {
            if (!existingByFurniture.containsKey(fid)) {
                if (lockRepository.existsByFurnitureId(fid)) {
                    throw new RuntimeException("家具ID " + fid + " 已在其他未结束批次中，无法加入");
                }
            }
        }

        Map<Long, OfficeFurniture> furnitureMap = furnitureRepository.findAllById(newFurnitureIds).stream()
                .collect(Collectors.toMap(OfficeFurniture::getId, f -> f));

        int order = 0;
        for (RelocationItemRequestDTO req : requests) {
            OfficeFurniture f = furnitureMap.get(req.getFurnitureId());
            if (f == null) {
                throw new RuntimeException("家具不存在: " + req.getFurnitureId());
            }
            RelocationItem item = existingByFurniture.get(f.getId());
            if (item == null) {
                item = new RelocationItem();
                item.setBatchId(id);
                item.setFurnitureId(f.getId());
                item.setFurnitureCode(f.getFurnitureCode());
                item.setSnapshotFloorNum(f.getFloorNum());
                item.setSnapshotStationCode(f.getStationCode());
                item.setSnapshotEmployeeName(f.getEmployeeName());
                item.setSnapshotDepartment(f.getDepartment());
                item.setSnapshotBindStatus(f.getBindStatus());
                item.setStatus(RelocationItem.STATUS_PENDING);

                RelocationActiveFurniture lock = new RelocationActiveFurniture();
                lock.setFurnitureId(f.getId());
                lock.setBatchId(id);
                try {
                    lockRepository.saveAndFlush(lock);
                } catch (Exception e) {
                    throw new RuntimeException("家具[" + f.getFurnitureCode() + "]已在其他未结束批次中");
                }
            }
            item.setTargetStationCode(req.getTargetStationCode());
            item.setTargetEmployeeName(req.getTargetEmployeeName());
            item.setTargetDepartment(req.getTargetDepartment());
            item.setSortOrder(order++);
            itemRepository.save(item);
        }

        applyDtoToBatch(batch, dto);
        batch.setTotalCount(requests.size());
        batchRepository.save(batch);
        return getDetail(id);
    }

    // ============================ 查询 ============================

    @Override
    public Page<RelocationBatch> pageBatches(String keyword, String status, Integer sourceFloorNum,
                                             Integer targetFloorNum, Pageable pageable) {
        return batchRepository.findAll((root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (StrUtil.isNotBlank(keyword)) {
                String p = "%" + keyword.trim() + "%";
                ps.add(cb.or(cb.like(root.get("batchNo"), p), cb.like(root.get("batchName"), p),
                        cb.like(root.get("department"), p)));
            }
            if (StrUtil.isNotBlank(status)) {
                ps.add(cb.equal(root.get("status"), status.trim()));
            }
            if (sourceFloorNum != null) {
                ps.add(cb.equal(root.get("sourceFloorNum"), sourceFloorNum));
            }
            if (targetFloorNum != null) {
                ps.add(cb.equal(root.get("targetFloorNum"), targetFloorNum));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        }, pageable);
    }

    @Override
    public RelocationBatch getDetail(Long id) {
        return getRequired(id);
    }

    // ============================ 校验 / 确认 / 撤回 / 撤销 ============================

    @Override
    public RelocationValidationVO validate(Long id) {
        RelocationBatch batch = getRequired(id);
        if (RelocationBatch.STATUS_COMPLETED.equals(batch.getStatus())
                || RelocationBatch.STATUS_CANCELLED.equals(batch.getStatus())) {
            throw new RuntimeException(statusText(batch.getStatus()) + "的批次无需校验");
        }
        return doValidate(batch);
    }

    @Override
    @Transactional
    public RelocationValidationVO confirm(Long id, String operatorName) {
        RelocationBatch batch = getRequired(id);
        if (RelocationBatch.STATUS_READY.equals(batch.getStatus())
                || RelocationBatch.STATUS_RUNNING.equals(batch.getStatus())
                || RelocationBatch.STATUS_PARTIAL_FAILED.equals(batch.getStatus())
                || RelocationBatch.STATUS_COMPLETED.equals(batch.getStatus())) {
            throw new RuntimeException("批次已确认，内容不可直接改动；如需调整请先撤回确认");
        }
        if (RelocationBatch.STATUS_CANCELLED.equals(batch.getStatus())) {
            throw new RuntimeException("已撤销批次不能确认");
        }

        RelocationValidationVO vo = doValidate(batch);
        if (Boolean.TRUE.equals(vo.getPassable())) {
            // 操作人先落库并 flush，随后再用批量CAS改状态，避免托管实体在提交时把状态覆盖回旧值
            if (StrUtil.isNotBlank(operatorName)) {
                batch.setOperatorName(operatorName);
                batchRepository.saveAndFlush(batch);
            }
            int updated = batchRepository.casStatus(id, RelocationBatch.STATUS_READY,
                    List.of(RelocationBatch.STATUS_PENDING, RelocationBatch.STATUS_READY),
                    LocalDateTime.now());
            if (updated == 0) {
                throw new RuntimeException("批次状态已变化，请刷新后重试");
            }
            entityManager.clear();
            vo.setPassable(true);
            return vo;
        }
        // 存在冲突：保持待确认，不得进入可执行
        return vo;
    }

    @Override
    @Transactional
    public RelocationBatch withdraw(Long id, String operatorName) {
        RelocationBatch batch = getRequired(id);
        if (!RelocationBatch.STATUS_READY.equals(batch.getStatus())) {
            throw new RuntimeException("仅可执行状态的批次可以撤回确认");
        }
        int updated = batchRepository.casStatus(id, RelocationBatch.STATUS_PENDING,
                List.of(RelocationBatch.STATUS_READY), LocalDateTime.now());
        if (updated == 0) {
            throw new RuntimeException("批次状态已变化，请刷新后重试");
        }
        entityManager.clear();
        log.info("relocation batch {} withdrawn by {}", batch.getBatchNo(), operatorName);
        return getRequired(id);
    }

    @Override
    @Transactional
    public RelocationBatch cancel(Long id, String operatorName) {
        RelocationBatch batch = getRequired(id);
        if (RelocationBatch.STATUS_COMPLETED.equals(batch.getStatus())) {
            throw new RuntimeException("已完成的批次不得撤销");
        }
        if (RelocationBatch.STATUS_CANCELLED.equals(batch.getStatus())) {
            return batch;
        }
        if (RelocationBatch.STATUS_RUNNING.equals(batch.getStatus())) {
            throw new RuntimeException("批次执行中，不能撤销");
        }
        if (batch.getSuccessCount() != null && batch.getSuccessCount() > 0) {
            throw new RuntimeException("批次已产生成功搬迁条目，不得撤销");
        }
        // 仅待确认/可执行允许撤销
        if (!RelocationBatch.STATUS_PENDING.equals(batch.getStatus())
                && !RelocationBatch.STATUS_READY.equals(batch.getStatus())) {
            throw new RuntimeException("当前状态不允许撤销");
        }
        int updated = batchRepository.casCancel(id,
                List.of(RelocationBatch.STATUS_PENDING, RelocationBatch.STATUS_READY),
                LocalDateTime.now());
        if (updated == 0) {
            throw new RuntimeException("批次状态已变化，请刷新后重试");
        }
        entityManager.clear();
        // 释放全部在途家具占用；条目与快照保留可追溯
        lockRepository.deleteByBatchId(id);
        entityManager.flush();
        log.info("relocation batch {} cancelled by {}", batch.getBatchNo(), operatorName);
        return getRequired(id);
    }

    // ============================ 执行 / 重试 ============================

    @Override
    public RelocationBatch execute(Long id, String operatorName) {
        RelocationBatch batch = getRequired(id);
        String status = batch.getStatus();
        if (RelocationBatch.STATUS_PENDING.equals(status)) {
            throw new RuntimeException("批次尚未确认，不能执行");
        }
        if (RelocationBatch.STATUS_COMPLETED.equals(status)) {
            return batch;
        }
        if (RelocationBatch.STATUS_CANCELLED.equals(status)) {
            throw new RuntimeException("已撤销批次不能执行");
        }
        // READY / RUNNING / PARTIAL_FAILED 允许进入执行链路（RUNNING 为超时重试/崩溃恢复入口）

        String lockKey = EXEC_LOCK_PREFIX + id;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey,
                StrUtil.blankToDefault(operatorName, "admin"), Duration.ofMinutes(10));
        if (!Boolean.TRUE.equals(acquired)) {
            // 两名管理员重复点击：第二个请求等待在途执行完成后返回最新状态，而非再执行一遍
            RelocationBatch latest = waitForOtherExecution(id, 60);
            if (latest != null) {
                return latest;
            }
            throw new RuntimeException("批次正在执行中，请勿重复提交，可刷新查看最新结果");
        }
        try {
            // READY -> RUNNING（CAS）；RUNNING/PARTIAL_FAILED 说明是重试/恢复，保持状态继续处理
            if (RelocationBatch.STATUS_READY.equals(status)) {
                int moved = batchRepository.casToRunning(id, RelocationBatch.STATUS_RUNNING,
                        List.of(RelocationBatch.STATUS_READY), LocalDateTime.now());
                if (moved == 0) {
                    // 已被另一请求翻状态：等待其完成
                    RelocationBatch latest = waitForOtherExecution(id, 60);
                    if (latest != null) {
                        return latest;
                    }
                }
            }
            if (StrUtil.isNotBlank(operatorName) && StrUtil.isBlank(batch.getOperatorName())) {
                // 忽略并发保存失败
                try {
                    RelocationBatch fresh = getRequired(id);
                    fresh.setOperatorName(operatorName);
                    batchRepository.save(fresh);
                } catch (Exception ignore) {
                    log.debug("set operator name skipped", ignore);
                }
            }

            List<RelocationItem> items = itemRepository.findByBatchIdOrderBySortOrderAscIdAsc(id);
            for (RelocationItem item : items) {
                // 已成功条目不重复搬迁；部分失败后只处理失败/待执行项
                if (RelocationItem.STATUS_SUCCESS.equals(item.getStatus())) {
                    continue;
                }
                try {
                    itemExecutor.process(item.getId());
                } catch (Exception e) {
                    // 逐件独立事务：未预期异常也只影响该件，成功件不回滚
                    log.error("relocation item {} process error: {}", item.getId(), e.getMessage(), e);
                    itemExecutor.markUnexpectedFailure(item.getId(), e.getMessage());
                }
            }

            // 以库内逐项最终结果为准汇总（保证刷新/离开再回来看到的成功数、失败原因与服务端一致）
            List<RelocationItem> finalItems = itemRepository.findByBatchIdOrderBySortOrderAscIdAsc(id);
            long success = finalItems.stream().filter(i -> RelocationItem.STATUS_SUCCESS.equals(i.getStatus())).count();
            long failed = finalItems.size() - success;
            String toStatus = failed == 0
                    ? RelocationBatch.STATUS_COMPLETED
                    : RelocationBatch.STATUS_PARTIAL_FAILED;

            // RUNNING -> COMPLETED / PARTIAL_FAILED 原子收口，同时落最终成功/失败计数，
            // 保证用户刷新页面、离开再回来看到的状态与计数严格以服务端落库结果为准
            int finished = batchRepository.casFinish(id, toStatus,
                    List.of(RelocationBatch.STATUS_RUNNING,
                            RelocationBatch.STATUS_PARTIAL_FAILED,
                            RelocationBatch.STATUS_COMPLETED),
                    (int) success, (int) failed,
                    failed == 0 ? LocalDateTime.now() : null);
            if (finished == 0) {
                log.warn("batch {} finish cas skipped, status changed concurrently", id);
            }

            if (toStatus.equals(RelocationBatch.STATUS_COMPLETED)) {
                // 全部成功：释放家具在途占用，允许家具进入新的搬迁批次
                itemExecutor.releaseBatchLocks(id);
            }
            return getRequired(id);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    /** 等待其他执行请求完成（状态离开 RUNNING 或锁释放），返回最新批次；超时返回 null */
    private RelocationBatch waitForOtherExecution(Long id, long maxWaitSeconds) {        long deadline = System.currentTimeMillis() + maxWaitSeconds * 1000;
        String lockKey = EXEC_LOCK_PREFIX + id;
        while (System.currentTimeMillis() < deadline) {
            Boolean locked = redisTemplate.hasKey(lockKey);
            if (!Boolean.TRUE.equals(locked)) {
                return getRequired(id);
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return null;
    }

    // ============================ 校验逻辑 ============================

    private RelocationValidationVO doValidate(RelocationBatch batch) {
        RelocationValidationVO vo = new RelocationValidationVO();
        vo.setBatchId(batch.getId());
        vo.setBatchNo(batch.getBatchNo());

        List<RelocationItem> items = itemRepository.findByBatchIdOrderBySortOrderAscIdAsc(batch.getId());
        List<Long> furnitureIds = items.stream().map(RelocationItem::getFurnitureId).toList();
        Map<Long, OfficeFurniture> furnitureMap = furnitureIds.isEmpty()
                ? Collections.emptyMap()
                : furnitureRepository.findAllById(furnitureIds).stream()
                .collect(Collectors.toMap(OfficeFurniture::getId, f -> f, (a, b) -> a));
        Map<Long, RelocationActiveFurniture> lockMap = lockRepository.findByFurnitureIdIn(
                        items.stream().map(RelocationItem::getFurnitureId).toList()).stream()
                .collect(Collectors.toMap(RelocationActiveFurniture::getFurnitureId, l -> l, (a, b) -> a));

        // 批次内目标工位重复（DB 唯一约束已保证，这里做防御性统计）
        Map<String, Long> stationCount = items.stream()
                .collect(Collectors.groupingBy(i -> i.getTargetStationCode().trim().toUpperCase(),
                        Collectors.counting()));

        int problems = 0;
        for (RelocationItem item : items) {
            String station = item.getTargetStationCode();
            String employee = item.getTargetEmployeeName();
            String department = item.getTargetDepartment();
            RelocationValidationVO.ItemCheck check;

            if (stationCount.getOrDefault(station.trim().toUpperCase(), 0L) > 1) {
                check = fail(item, RelocationFailCode.DUPLICATE_TARGET_STATION,
                        "目标工位[" + station + "]在批次内重复分配");
            } else {
                OfficeFurniture f = furnitureMap.get(item.getFurnitureId());
                if (f == null) {
                    check = fail(item, RelocationFailCode.FURNITURE_DELETED,
                            "家具[" + item.getFurnitureCode() + "]已被删除");
                } else if (!Objects.equals(f.getFloorNum(), batch.getSourceFloorNum())) {
                    check = fail(item, RelocationFailCode.SOURCE_FLOOR_MISMATCH,
                            "家具当前位于" + f.getFloorNum() + "层，与迁出楼层"
                                    + batch.getSourceFloorNum() + "层不一致");
                } else if (!StationFloorRule.matchesFloor(station, batch.getTargetFloorNum())) {
                    check = fail(item, RelocationFailCode.FLOOR_MISMATCH,
                            "目标工位[" + station + "]不属于迁入楼层" + batch.getTargetFloorNum() + "层");
                } else if (!snapshotMatches(item, f)) {
                    check = fail(item, RelocationFailCode.SNAPSHOT_CHANGED,
                            "创建后原绑定已变化：当前[" + describe(f) + "]，快照["
                                    + describeSnapshot(item) + "]");
                } else {
                    // 目标工位是否被批次外家具占用
                    List<OfficeFurniture> holders = furnitureRepository.findByStationCode(station.trim());
                    Set<Long> batchFurnitureIds = new HashSet<>(furnitureIds);
                    OfficeFurniture blocker = holders.stream()
                            .filter(h -> !h.getId().equals(f.getId())
                                    && !batchFurnitureIds.contains(h.getId()))
                            .findFirst().orElse(null);
                    if (blocker != null) {
                        check = fail(item, RelocationFailCode.STATION_OCCUPIED,
                                "目标工位[" + station + "]已被批次外家具["
                                        + blocker.getFurnitureCode() + "]占用");
                    } else {
                        RelocationActiveFurniture lock = lockMap.get(f.getId());
                        if (lock != null && !Objects.equals(lock.getBatchId(), batch.getId())) {
                            check = fail(item, RelocationFailCode.FURNITURE_LOCKED,
                                    "家具已在其他未结束批次[" + lock.getBatchId() + "]中");
                        } else {
                            check = RelocationValidationVO.ItemCheck.ok(item.getId(), f.getId(),
                                    f.getFurnitureCode(), station, employee, department);
                        }
                    }
                }
            }
            if (Boolean.FALSE.equals(check.getPassed())) {
                problems++;
            }
            vo.getItems().add(check);
        }
        vo.setTotalCount(items.size());
        vo.setProblemCount(problems);
        vo.setPassable(problems == 0 && !items.isEmpty());
        return vo;
    }

    private RelocationValidationVO.ItemCheck fail(RelocationItem item, String code, String message) {
        return RelocationValidationVO.ItemCheck.fail(item.getId(), item.getFurnitureId(),
                item.getFurnitureCode(), item.getTargetStationCode(),
                item.getTargetEmployeeName(), item.getTargetDepartment(), code, message);
    }

    private boolean snapshotMatches(RelocationItem item, OfficeFurniture f) {
        return Objects.equals(item.getSnapshotFloorNum(), f.getFloorNum())
                && Objects.equals(StrUtil.trim(item.getSnapshotStationCode()), StrUtil.trim(f.getStationCode()))
                && Objects.equals(StrUtil.trim(item.getSnapshotEmployeeName()), StrUtil.trim(f.getEmployeeName()))
                && Objects.equals(StrUtil.trim(item.getSnapshotDepartment()), StrUtil.trim(f.getDepartment()))
                && Objects.equals(item.getSnapshotBindStatus(), f.getBindStatus());
    }

    private String describe(OfficeFurniture f) {
        return f.getFloorNum() + "层/" + StrUtil.blankToDefault(f.getStationCode(), "未绑定工位")
                + "/" + StrUtil.blankToDefault(f.getEmployeeName(), "无人")
                + "/" + StrUtil.blankToDefault(f.getDepartment(), "无部门");
    }

    private String describeSnapshot(RelocationItem i) {
        return i.getSnapshotFloorNum() + "层/"
                + StrUtil.blankToDefault(i.getSnapshotStationCode(), "未绑定工位")
                + "/" + StrUtil.blankToDefault(i.getSnapshotEmployeeName(), "无人")
                + "/" + StrUtil.blankToDefault(i.getSnapshotDepartment(), "无部门");
    }

    // ============================ 辅助 ============================

    private RelocationBatch getRequired(Long id) {
        return batchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("搬迁批次不存在: " + id));
    }

    private void normalize(RelocationBatchSaveDTO dto) {
        dto.setBatchName(StrUtil.trim(dto.getBatchName()));
        dto.setDepartment(StrUtil.trimToNull(dto.getDepartment()));
        dto.setRemark(StrUtil.trimToNull(dto.getRemark()));
        dto.setOperatorName(StrUtil.blankToDefault(StrUtil.trim(dto.getOperatorName()), "行政管理员"));
        for (RelocationItemRequestDTO item : dto.getItems()) {
            item.setTargetStationCode(StrUtil.trim(item.getTargetStationCode()));
            item.setTargetEmployeeName(StrUtil.trim(item.getTargetEmployeeName()));
            item.setTargetDepartment(StrUtil.trim(item.getTargetDepartment()));
        }
    }

    private void validateStructure(RelocationBatchSaveDTO dto) {
        if (dto.getSourceFloorNum().equals(dto.getTargetFloorNum())) {
            throw new RuntimeException("迁出楼层与迁入楼层不能相同");
        }
        List<RelocationItemRequestDTO> items = dto.getItems();
        Set<Long> furnitureIds = new HashSet<>();
        Set<String> stations = new HashSet<>();
        for (RelocationItemRequestDTO it : items) {
            if (!furnitureIds.add(it.getFurnitureId())) {
                throw new RuntimeException("同一件家具在批次内重复出现");
            }
            if (!stations.add(it.getTargetStationCode().trim().toUpperCase())) {
                throw new RuntimeException("目标工位[" + it.getTargetStationCode() + "]在批次内重复");
            }
        }
    }

    private void applyDtoToBatch(RelocationBatch batch, RelocationBatchSaveDTO dto) {
        batch.setBatchName(dto.getBatchName());
        batch.setSourceFloorNum(dto.getSourceFloorNum());
        batch.setTargetFloorNum(dto.getTargetFloorNum());
        batch.setDepartment(dto.getDepartment());
        batch.setRemark(dto.getRemark());
        if (StrUtil.isNotBlank(dto.getOperatorName())) {
            batch.setOperatorName(dto.getOperatorName());
        }
    }

    private String generateBatchNo() {
        // Redis 计数保证并发下批次号唯一，DB 唯一索引兜底
        String day = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String key = "relocation:batch:seq:" + day;
        Long seq = redisTemplate.opsForValue().increment(key);
        if (seq != null && seq == 1L) {
            redisTemplate.expire(key, Duration.ofDays(2));
        }
        String no = "RL" + day + String.format("%04d", seq == null ? 1 : seq);
        if (batchRepository.existsByBatchNo(no)) {
            no = no + "-" + System.currentTimeMillis() % 1000;
        }
        return no;
    }

    private String buildLockedMessage(List<RelocationActiveFurniture> blocked) {
        String ids = blocked.stream().map(l -> String.valueOf(l.getFurnitureId()))
                .limit(10).collect(Collectors.joining(","));
        return "以下家具已存在于未结束批次中，不能重复加入: " + ids;
    }

    public static String statusText(String status) {
        return switch (status) {
            case RelocationBatch.STATUS_PENDING -> "待确认";
            case RelocationBatch.STATUS_READY -> "可执行";
            case RelocationBatch.STATUS_RUNNING -> "执行中";
            case RelocationBatch.STATUS_PARTIAL_FAILED -> "部分失败";
            case RelocationBatch.STATUS_COMPLETED -> "已完成";
            case RelocationBatch.STATUS_CANCELLED -> "已撤销";
            default -> status;
        };
    }
}
