package com.workspace.service.relocation;

import com.workspace.entity.BindRecord;
import com.workspace.entity.OfficeFurniture;
import com.workspace.entity.RelocationBatch;
import com.workspace.entity.RelocationItem;
import com.workspace.repository.BindRecordRepository;
import com.workspace.repository.OfficeFurnitureRepository;
import com.workspace.repository.RelocationItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 搬迁条目执行器：每件家具在独立事务中完成"复检 -> 改绑定 -> 写台账"。
 * 单条失败只回滚该条，已成功条目不回滚。
 * 幂等三重保障：执行入口分布式锁+状态机 CAS、家具行/工位行悲观锁、台账 relocation_item_id 唯一索引。
 */
@Component
public class RelocationItemExecutor {

    private static final Logger log = LoggerFactory.getLogger(RelocationItemExecutor.class);

    private final OfficeFurnitureRepository furnitureRepository;
    private final RelocationItemRepository itemRepository;
    private final BindRecordRepository bindRecordRepository;
    private final RelocationValidator validator;
    private final RelocationItemResultRecorder recorder;

    public RelocationItemExecutor(OfficeFurnitureRepository furnitureRepository,
                                  RelocationItemRepository itemRepository,
                                  BindRecordRepository bindRecordRepository,
                                  RelocationValidator validator,
                                  RelocationItemResultRecorder recorder) {
        this.furnitureRepository = furnitureRepository;
        this.itemRepository = itemRepository;
        this.bindRecordRepository = bindRecordRepository;
        this.validator = validator;
        this.recorder = recorder;
    }

    public static class ItemResult {
        public boolean success;
        public boolean idempotent;
        public String message;
        public Long bindRecordId;

        static ItemResult ok(boolean idempotent, String message, Long bindRecordId) {
            ItemResult r = new ItemResult();
            r.success = true;
            r.idempotent = idempotent;
            r.message = message;
            r.bindRecordId = bindRecordId;
            return r;
        }

        static ItemResult fail(String message) {
            ItemResult r = new ItemResult();
            r.success = false;
            r.message = message;
            return r;
        }
    }

    /**
     * 执行单条（重试时按最新数据复检）。每件家具独立事务提交；
     * 失败/幂等结果通过独立事务记录器落库，本事务即便回滚，失败原因仍可追溯。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ItemResult executeItem(Long itemId, RelocationBatch batch, List<Long> batchFurnitureIds) {
        RelocationItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalStateException("搬迁条目不存在: " + itemId));

        // 已成功（含上一次执行已提交、调用方超时重试）：幂等收口，不重复搬迁、不重复写台账
        if (RelocationItem.RESULT_SUCCESS.equals(item.getResultStatus())
                || RelocationItem.RESULT_IDEMPOTENT.equals(item.getResultStatus())) {
            return ItemResult.ok(RelocationItem.RESULT_IDEMPOTENT.equals(item.getResultStatus()),
                    "已执行成功，幂等跳过", item.getBindRecordId());
        }

        Set<Long> batchIdSet = batchFurnitureIds.stream().collect(Collectors.toSet());

        OfficeFurniture furniture;
        try {
            // 串行化对同一家具的并发操作
            furniture = furnitureRepository.findByIdForUpdate(item.getFurnitureId()).orElse(null);
            // 串行化对同一目标工位的并发抢占
            furnitureRepository.findBoundByStationCodeForUpdate(item.getTargetStationCode());
        } catch (Exception e) {
            log.warn("搬迁条目[{}]加锁失败: {}", itemId, e.getMessage());
            return fail(itemId, "执行加锁失败（并发冲突），请重试: " + safeMessage(e), null);
        }

        // 1. 家具已被删除
        if (furniture == null) {
            return fail(itemId, "家具已被删除", RelocationItem.CODE_FURNITURE_DELETED);
        }

        // 2. 幂等收口：家具当前绑定已与目标完全一致，且本条目台账已存在
        //    （可能是上轮请求超时但实际已提交，或别的操作完成到同一目标）-> 不重复搬迁、不重复写台账
        if (alreadyAtTarget(item, furniture)
                && bindRecordRepository.existsByRelocationItemId(item.getId())) {
            Long recordId = recorder.markIdempotent(item.getId(),
                    "已由之前的操作完成到同一目标，按幂等成功收口");
            return ItemResult.ok(true, "已由之前的操作完成到同一目标，按幂等成功收口", recordId);
        }

        // 3. 按最新数据复检：楼层不匹配 / 原绑定快照漂移 / 工位被批次外占用
        List<String> codes = new ArrayList<>();
        if (!RelocationValidator.floorMatches(batch.getTargetFloor(), item.getTargetStationCode())) {
            codes.add(RelocationItem.CODE_FLOOR_MISMATCH);
        }
        if (RelocationValidator.bindingChanged(item, furniture)) {
            codes.add(RelocationItem.CODE_BINDING_CHANGED);
        }
        if (validator.occupiedByOtherBoundFurniture(item.getTargetStationCode(), batchIdSet)) {
            codes.add(RelocationItem.CODE_STATION_OCCUPIED);
        }
        if (!codes.isEmpty()) {
            return fail(itemId, RelocationValidator.describeCodes(codes, batch.getTargetFloor()),
                    String.join(",", codes));
        }

        // 4. 写入绑定关系：新楼层 + 新工位 + 新使用人 + 新部门
        String oldStation = furniture.getStationCode();

        BindRecord record = new BindRecord();
        record.setFurnitureId(furniture.getId());
        record.setFurnitureCode(furniture.getFurnitureCode());
        record.setOperateType("RELOCATION");
        record.setOldStationCode(furniture.getStationCode());
        record.setNewStationCode(item.getTargetStationCode());
        record.setOldEmployeeName(furniture.getEmployeeName());
        record.setNewEmployeeName(item.getTargetEmployeeName());
        record.setOldDepartment(furniture.getDepartment());
        record.setNewDepartment(item.getTargetDepartment());
        record.setOperateReason("楼层搬迁批次[" + batch.getBatchNo() + "] "
                + batch.getSourceFloor() + "层 -> " + batch.getTargetFloor() + "层"
                + (batch.getRemark() != null ? "；" + batch.getRemark() : ""));
        record.setOperatorName(batch.getOperatorName() != null ? batch.getOperatorName() : "行政管理员");
        record.setRelocationBatchId(batch.getId());
        record.setRelocationBatchNo(batch.getBatchNo());
        record.setRelocationItemId(item.getId());

        furniture.setFloorNum(batch.getTargetFloor());
        furniture.setStationCode(item.getTargetStationCode());
        furniture.setEmployeeName(item.getTargetEmployeeName());
        furniture.setDepartment(item.getTargetDepartment());
        furniture.setBindStatus(1);

        try {
            // 台账 relocation_item_id 唯一索引是"不重复生成台账/不重复搬迁"的最终防线：
            // 两名管理员重复点击、请求超时后重试，即便前面判断都穿透，也只会有一条台账、一次绑定变更
            BindRecord saved = bindRecordRepository.saveAndFlush(record);
            furnitureRepository.saveAndFlush(furniture);

            item.setResultStatus(RelocationItem.RESULT_SUCCESS);
            item.setValidateStatus(RelocationItem.VALIDATE_PASS);
            item.setValidateCodes(null);
            item.setValidateMessage("搬迁成功：" + nullToDash(oldStation) + " -> " + item.getTargetStationCode());
            item.setBindRecordId(saved.getId());
            item.setExecutedTime(LocalDateTime.now());
            itemRepository.save(item);
            return ItemResult.ok(false, item.getValidateMessage(), saved.getId());
        } catch (DataIntegrityViolationException dup) {
            // 台账已存在 => 本次搬迁此前已落库，按幂等成功收口，不重复改绑定（本事务回滚）
            log.info("搬迁条目[{}]台账唯一键冲突，按幂等成功处理", itemId);
            BindRecord existing = bindRecordRepository.findByRelocationItemId(item.getId()).orElse(null);
            if (existing != null) {
                recorder.attachBindRecord(itemId, existing.getId());
                recorder.markIdempotent(itemId, "搬迁此前已完成（台账已存在），重复请求按幂等成功收口");
                return ItemResult.ok(true, "搬迁此前已完成（台账已存在），重复请求按幂等成功收口",
                        existing.getId());
            }
            return fail(itemId, "执行冲突，请重试: " + safeMessage(dup), null);
        } catch (Exception e) {
            log.error("搬迁条目[{}]执行异常", itemId, e);
            return fail(itemId, "执行异常: " + safeMessage(e), null);
        }
    }

    /**
     * 失败收口：结果通过 REQUIRES_NEW 独立事务落库，随后抛出异常让本事务回滚，
     * 保证"绑定没改、台账没写"，同时失败原因在刷新后仍可从服务端查到。
     */
    private ItemResult fail(Long itemId, String message, String codes) {
        recorder.markFailed(itemId, message, codes);
        throw new ItemFailedException(message);
    }

    /** 标记性异常：单条失败的正常控制流，由执行编排层捕获，不影响后续条目 */
    public static class ItemFailedException extends RuntimeException {
        public ItemFailedException(String message) {
            super(message);
        }
    }

    private boolean alreadyAtTarget(RelocationItem item, OfficeFurniture f) {
        return Objects.equals(item.getTargetStationCode(), f.getStationCode())
                && Objects.equals(emptyToNull(item.getTargetEmployeeName()), emptyToNull(f.getEmployeeName()))
                && Objects.equals(emptyToNull(item.getTargetDepartment()), emptyToNull(f.getDepartment()))
                && f.getBindStatus() != null && f.getBindStatus() == 1;
    }

    private static String emptyToNull(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s;
    }

    private static String nullToDash(String s) {
        return s == null ? "未绑定工位" : s;
    }

    private static String safeMessage(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
