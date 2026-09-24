package com.workspace.service.relocation;

import cn.hutool.core.util.StrUtil;
import com.workspace.dto.RelocationBatchCreateDTO;
import com.workspace.dto.RelocationBatchUpdateDTO;
import com.workspace.dto.RelocationBatchVO;
import com.workspace.entity.BindRecord;
import com.workspace.entity.OfficeFurniture;
import com.workspace.entity.RelocationBatch;
import com.workspace.entity.RelocationFurnitureLock;
import com.workspace.entity.RelocationItem;
import com.workspace.repository.BindRecordRepository;
import com.workspace.repository.OfficeFurnitureRepository;
import com.workspace.repository.RelocationBatchRepository;
import com.workspace.repository.RelocationFurnitureLockRepository;
import com.workspace.repository.RelocationItemRepository;
import com.workspace.service.RelocationBatchService;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RelocationBatchServiceImpl implements RelocationBatchService {

    private static final Logger log = LoggerFactory.getLogger(RelocationBatchServiceImpl.class);

    private static final String EXEC_LOCK_PREFIX = "relocation:execute:lock:";
    private static final String BATCH_NO_SEQ_PREFIX = "relocation:batchno:seq:";
    private static final long EXEC_LOCK_SECONDS = 300;

    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT;

    static {
        RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
                "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
                Long.class);
    }

    private final RelocationBatchRepository batchRepository;
    private final RelocationItemRepository itemRepository;
    private final RelocationFurnitureLockRepository lockRepository;
    private final OfficeFurnitureRepository furnitureRepository;
    private final BindRecordRepository bindRecordRepository;
    private final RelocationValidator validator;
    private final RelocationStateService stateService;
    private final RelocationItemExecutor itemExecutor;
    private final RedisTemplate<String, Object> redisTemplate;

    public RelocationBatchServiceImpl(RelocationBatchRepository batchRepository,
                                      RelocationItemRepository itemRepository,
                                      RelocationFurnitureLockRepository lockRepository,
                                      OfficeFurnitureRepository furnitureRepository,
                                      BindRecordRepository bindRecordRepository,
                                      RelocationValidator validator,
                                      RelocationStateService stateService,
                                      RelocationItemExecutor itemExecutor,
                                      RedisTemplate<String, Object> redisTemplate) {
        this.batchRepository = batchRepository;
        this.itemRepository = itemRepository;
        this.lockRepository = lockRepository;
        this.furnitureRepository = furnitureRepository;
        this.bindRecordRepository = bindRecordRepository;
        this.validator = validator;
        this.stateService = stateService;
        this.itemExecutor = itemExecutor;
        this.redisTemplate = redisTemplate;
    }

    // ============================ 创建 / 调整 ============================

    @Override
    @Transactional
    public RelocationBatchVO create(RelocationBatchCreateDTO dto) {
        validatePayload(dto.getSourceFloor(), dto.getTargetFloor(), dto.getItems());

        RelocationBatch batch = new RelocationBatch();
        batch.setBatchNo(generateBatchNo());
        batch.setBatchName(dto.getBatchName().trim());
        batch.setSourceFloor(dto.getSourceFloor());
        batch.setTargetFloor(dto.getTargetFloor());
        batch.setTargetDepartment(trimToNull(dto.getTargetDepartment()));
        batch.setOperatorName(StrUtil.isNotBlank(dto.getOperatorName()) ? dto.getOperatorName().trim() : "行政管理员");
        batch.setRemark(trimToNull(dto.getRemark()));
        batch.setStatus(RelocationBatch.STATUS_PENDING);
        batch.setTotalCount(dto.getItems().size());
        batch.setSuccessCount(0);
        batch.setFailCount(0);
        batch = batchRepository.save(batch);

        List<RelocationItem> items = buildItems(batch, dto.getItems());
        itemRepository.saveAll(items);
        acquireFurnitureLocks(batch.getId(), items);

        // 创建时即给出逐项校验结果，批次以待确认状态保存
        validator.validateAndPersist(batch, items);
        return detail(batch.getId());
    }

    @Override
    @Transactional
    public RelocationBatchVO update(RelocationBatchUpdateDTO dto) {
        RelocationBatch batch = getRequired(dto.getId());
        if (RelocationBatch.STATUS_CANCELLED.equals(batch.getStatus())) {
            throw new IllegalStateException("已撤销批次不可调整，请新建批次");
        }
        if (!RelocationBatch.STATUS_PENDING.equals(batch.getStatus())) {
            throw new IllegalStateException("已确认批次内容不可直接改动，请先撤回确认再调整");
        }
        validatePayload(batch.getSourceFloor(), batch.getTargetFloor(), dto.getItems());

        batch.setBatchName(dto.getBatchName().trim());
        batch.setTargetDepartment(trimToNull(dto.getTargetDepartment()));
        batch.setRemark(trimToNull(dto.getRemark()));
        batch.setTotalCount(dto.getItems().size());
        batchRepository.save(batch);

        // 释放旧占用与条目，再按新内容重建（快照随新内容重新生成）
        // 先批量落库删除并 flush，避免 Hibernate 动作重排导致 insert 先于 delete 触发唯一键冲突
        lockRepository.deleteByBatchId(batch.getId());
        lockRepository.flush();
        List<RelocationItem> oldItems = itemRepository.findByBatchIdOrderByIdAsc(batch.getId());
        if (!oldItems.isEmpty()) {
            itemRepository.deleteAllInBatch(oldItems);
        }

        List<RelocationItem> items = buildItems(batch, dto.getItems());
        itemRepository.saveAll(items);
        acquireFurnitureLocks(batch.getId(), items);
        validator.validateAndPersist(batch, items);
        return detail(batch.getId());
    }

    private void validatePayload(Integer sourceFloor, Integer targetFloor,
                                 List<RelocationBatchCreateDTO.ItemDTO> items) {
        if (sourceFloor.equals(targetFloor)) {
            throw new IllegalStateException("迁出楼层与迁入楼层不能相同");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalStateException("请至少添加一件家具");
        }
        Set<Long> furnitureIds = new HashSet<>();
        Set<String> stations = new HashSet<>();
        for (RelocationBatchCreateDTO.ItemDTO item : items) {
            if (!furnitureIds.add(item.getFurnitureId())) {
                throw new IllegalStateException("批次内同一家具不能重复添加: furnitureId=" + item.getFurnitureId());
            }
            String station = item.getTargetStationCode().trim();
            if (!stations.add(station)) {
                throw new IllegalStateException("目标工位在批次内不能重复: " + station);
            }
            if (!RelocationValidator.floorMatches(targetFloor, station)) {
                throw new IllegalStateException("目标工位[" + station + "]与迁入楼层[" + targetFloor + "层]不匹配");
            }
        }
    }

    private List<RelocationItem> buildItems(RelocationBatch batch, List<RelocationBatchCreateDTO.ItemDTO> dtoItems) {
        Map<Long, OfficeFurniture> furnitureMap = furnitureRepository.findAllById(
                        dtoItems.stream().map(RelocationBatchCreateDTO.ItemDTO::getFurnitureId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(OfficeFurniture::getId, Function.identity()));

        List<RelocationItem> items = new ArrayList<>();
        for (RelocationBatchCreateDTO.ItemDTO dto : dtoItems) {
            OfficeFurniture f = furnitureMap.get(dto.getFurnitureId());
            if (f == null) {
                throw new IllegalStateException("家具不存在或已被删除: id=" + dto.getFurnitureId());
            }
            RelocationItem item = new RelocationItem();
            item.setBatchId(batch.getId());
            item.setFurnitureId(f.getId());
            item.setFurnitureCode(f.getFurnitureCode());
            item.setFurnitureType(f.getFurnitureType());
            item.setTargetStationCode(dto.getTargetStationCode().trim());
            item.setTargetEmployeeName(trimToNull(dto.getTargetEmployeeName()));
            item.setTargetDepartment(StrUtil.isNotBlank(dto.getTargetDepartment())
                    ? dto.getTargetDepartment().trim() : batch.getTargetDepartment());
            // 保留创建时的原绑定快照，后续确认与执行全部以此对照
            item.setSnapFloorNum(f.getFloorNum());
            item.setSnapStationCode(f.getStationCode());
            item.setSnapEmployeeName(f.getEmployeeName());
            item.setSnapDepartment(f.getDepartment());
            item.setSnapBindStatus(f.getBindStatus());
            item.setResultStatus(RelocationItem.RESULT_PENDING);
            item.setValidateStatus(RelocationItem.VALIDATE_UNCHECKED);
            items.add(item);
        }
        return items;
    }

    /**
     * 借助 relocation_furniture_lock 唯一键落占用：
     * 同一家具不能同时出现在两个未结束批次中（完成/撤销时释放，部分失败期间继续占用）。
     */
    private void acquireFurnitureLocks(Long batchId, List<RelocationItem> items) {
        for (RelocationItem item : items) {
            RelocationFurnitureLock lock = new RelocationFurnitureLock();
            lock.setBatchId(batchId);
            lock.setFurnitureId(item.getFurnitureId());
            try {
                lockRepository.saveAndFlush(lock);
            } catch (DataIntegrityViolationException e) {
                throw new IllegalStateException("家具[" + item.getFurnitureCode()
                        + "]已存在于另一个未结束的搬迁批次中，不能重复加入");
            }
        }
    }

    // ============================ 校验 / 确认 / 撤回 / 撤销 ============================

    @Override
    @Transactional
    public RelocationBatchVO validate(Long id) {
        RelocationBatch batch = getRequired(id);
        if (RelocationBatch.STATUS_CANCELLED.equals(batch.getStatus())) {
            throw new IllegalStateException("已撤销批次不能校验");
        }
        if (RelocationBatch.STATUS_COMPLETED.equals(batch.getStatus())) {
            throw new IllegalStateException("已完成批次无需校验");
        }
        List<RelocationItem> items = itemRepository.findByBatchIdOrderByIdAsc(id);
        validator.validateAndPersist(batch, items);
        return detail(id);
    }

    @Override
    @Transactional
    public RelocationBatchVO confirm(Long id) {
        // 确认前强制按最新数据再校验一次，杜绝旧校验结果放行
        RelocationBatch batch = getRequired(id);
        List<RelocationItem> items = itemRepository.findByBatchIdOrderByIdAsc(id);
        int conflicts = validator.validateAndPersist(batch, items);
        if (conflicts > 0) {
            // 存在冲突不得进入可执行状态，保持待确认并返回逐项冲突
            return detail(id);
        }
        stateService.confirm(id);
        return detail(id);
    }

    @Override
    @Transactional
    public RelocationBatchVO withdraw(Long id) {
        stateService.withdraw(id);
        return detail(id);
    }

    @Override
    @Transactional
    public RelocationBatchVO cancel(Long id) {
        stateService.cancel(id);
        // 撤销即批次结束，释放家具占用，允许家具进入其他批次
        lockRepository.deleteByBatchId(id);
        return detail(id);
    }

    // ============================ 执行（并发/异常/重试的核心） ============================

    @Override
    public RelocationBatchVO execute(Long id) {
        String lockKey = EXEC_LOCK_PREFIX + id;
        String token = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, token, EXEC_LOCK_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            // 两名管理员重复点击 / 上一请求仍在执行：直接返回当前服务端状态，不重复执行
            log.info("搬迁批次[{}]正在执行中，重复请求被拦截并返回最新状态", id);
            return detail(id);
        }
        try {
            RelocationBatch before = getRequired(id);
            if (RelocationBatch.STATUS_COMPLETED.equals(before.getStatus())
                    || RelocationBatch.STATUS_CANCELLED.equals(before.getStatus())) {
                // 已完成/已撤销的重复执行请求（超时重试、页面刷新后再点）幂等返回
                return detail(id);
            }

            // CAS 进入执行中；并发下乐观锁失败的一方会拿到 ConcurrentTransitionException
            RelocationBatch running = stateService.beginExecution(id);

            List<RelocationItem> items = itemRepository.findByBatchIdOrderByIdAsc(id);
            boolean retryFailedOnly = RelocationBatch.STATUS_PARTIAL_FAILED.equals(before.getStatus());

            List<Long> batchFurnitureIds = items.stream()
                    .map(RelocationItem::getFurnitureId).collect(Collectors.toList());

            for (RelocationItem item : items) {
                boolean shouldRun;
                if (retryFailedOnly) {
                    // 部分失败后只能重试失败项
                    shouldRun = RelocationItem.RESULT_FAILED.equals(item.getResultStatus());
                } else {
                    shouldRun = RelocationItem.RESULT_PENDING.equals(item.getResultStatus());
                }
                if (!shouldRun) {
                    continue;
                }
                // 每件家具独立事务：失败只回滚该件，已成功件保持提交
                itemExecutor.executeItem(item.getId(), running, batchFurnitureIds);
            }

            // 重算成功/失败数并落 已完成 / 部分失败
            stateService.finishExecution(id);
            return detail(id);
        } finally {
            redisTemplate.execute(RELEASE_LOCK_SCRIPT, Collections.singletonList(lockKey), token);
        }
    }

    // ============================ 列表 / 详情 ============================

    @Override
    public Page<RelocationBatchVO> page(String keyword, String status, Pageable pageable) {
        return batchRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StrUtil.isNotBlank(status)) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (StrUtil.isNotBlank(keyword)) {
                String pattern = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("batchNo"), pattern),
                        cb.like(root.get("batchName"), pattern),
                        cb.like(root.get("operatorName"), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable).map(RelocationBatchVO::from);
    }

    @Override
    public RelocationBatchVO detail(Long id) {
        RelocationBatch batch = getRequired(id);
        RelocationBatchVO vo = RelocationBatchVO.from(batch);

        List<RelocationItem> items = itemRepository.findByBatchIdOrderByIdAsc(id);
        Map<Long, OfficeFurniture> currentMap = furnitureRepository.findAllById(
                        items.stream().map(RelocationItem::getFurnitureId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(OfficeFurniture::getId, Function.identity()));

        int conflict = 0;
        List<RelocationBatchVO.ItemVO> itemVOs = new ArrayList<>();
        for (RelocationItem item : items) {
            RelocationBatchVO.ItemVO itemVO = RelocationBatchVO.toItemVO(item);
            OfficeFurniture current = currentMap.get(item.getFurnitureId());
            itemVO.setFurnitureExists(current != null);
            if (current != null) {
                itemVO.setCurrentFloorNum(current.getFloorNum());
                itemVO.setCurrentStationCode(current.getStationCode());
                itemVO.setCurrentEmployeeName(current.getEmployeeName());
                itemVO.setCurrentDepartment(current.getDepartment());
                itemVO.setCurrentBindStatus(current.getBindStatus());
            }
            if (RelocationItem.VALIDATE_CONFLICT.equals(item.getValidateStatus())) {
                conflict++;
            }
            itemVOs.add(itemVO);
        }
        vo.setItems(itemVOs);
        vo.setConflictCount(conflict);
        vo.setValidationPassed(conflict == 0 && !items.isEmpty());

        List<BindRecord> records = bindRecordRepository.findByRelocationBatchIdOrderByRecordTimeDesc(id);
        vo.setBindRecords(records);
        return vo;
    }

    // ============================ 工具 ============================

    private RelocationBatch getRequired(Long id) {
        return batchRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("搬迁批次不存在: " + id));
    }

    private String generateBatchNo() {
        String day = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long seq;
        try {
            seq = redisTemplate.opsForValue().increment(BATCH_NO_SEQ_PREFIX + day);
            if (seq != null && seq == 1L) {
                redisTemplate.expire(BATCH_NO_SEQ_PREFIX + day, 2, TimeUnit.DAYS);
            }
        } catch (Exception e) {
            // Redis 不可用时退化为毫秒尾号，保证批次号仍可用（极小概率场景）
            log.warn("批次号序列自增失败，使用时间戳兜底: {}", e.getMessage());
            seq = System.currentTimeMillis() % 10000;
        }
        return "RL" + day + "-" + String.format("%04d", Objects.requireNonNullElse(seq, 1L) % 10000);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
