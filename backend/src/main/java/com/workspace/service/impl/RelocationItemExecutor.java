package com.workspace.service.impl;

import cn.hutool.core.util.StrUtil;
import com.workspace.common.RelocationFailCode;
import com.workspace.common.StationFloorRule;
import com.workspace.entity.BindRecord;
import com.workspace.entity.OfficeFurniture;
import com.workspace.entity.RelocationBatch;
import com.workspace.entity.RelocationItem;
import com.workspace.repository.BindRecordRepository;
import com.workspace.repository.OfficeFurnitureRepository;
import com.workspace.repository.RelocationActiveFurnitureRepository;
import com.workspace.repository.RelocationItemRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 搬迁条目逐件执行器。每件家具一个独立事务：
 * 单条失败只回滚该条，已成功的条目不回滚（部分失败语义）。
 *
 * 幂等防线：
 * 1. 条目行行锁 + 成功态短路；
 * 2. 家具行行锁串行化绑定写入；
 * 3. 台账 (relocation_batch_no, furniture_id) 唯一约束兜底，绝不重复生成台账。
 */
@Slf4j
@Component
public class RelocationItemExecutor {

    private final RelocationItemRepository itemRepository;
    private final OfficeFurnitureRepository furnitureRepository;
    private final BindRecordRepository bindRecordRepository;
    private final RelocationActiveFurnitureRepository activeFurnitureRepository;

    public RelocationItemExecutor(RelocationItemRepository itemRepository,
                                  OfficeFurnitureRepository furnitureRepository,
                                  BindRecordRepository bindRecordRepository,
                                  RelocationActiveFurnitureRepository activeFurnitureRepository) {
        this.itemRepository = itemRepository;
        this.furnitureRepository = furnitureRepository;
        this.bindRecordRepository = bindRecordRepository;
        this.activeFurnitureRepository = activeFurnitureRepository;
    }

    /** 单条执行结果 */
    public record ItemOutcome(Long itemId, boolean success, String failCode, String message) {
    }

    /**
     * 处理一个条目，独立事务提交。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ItemOutcome process(Long itemId) {
        RelocationItem item = itemRepository.findByIdForUpdate(itemId)
                .orElse(null);
        if (item == null) {
            return new ItemOutcome(itemId, false, RelocationFailCode.FURNITURE_DELETED, "条目不存在");
        }
        // 已成功条目直接短路：重复点击/超时重试不会重复搬迁、重复写台账
        if (RelocationItem.STATUS_SUCCESS.equals(item.getStatus())) {
            return new ItemOutcome(itemId, true, null,
                    StrUtil.blankToDefault(item.getResultNote(), "已成功，跳过"));
        }

        RelocationBatch batch = item.getBatch();

        OfficeFurniture furniture = furnitureRepository.findByIdForUpdate(item.getFurnitureId())
                .orElse(null);
        if (furniture == null) {
            markFailed(item, RelocationFailCode.FURNITURE_DELETED,
                    "家具[" + item.getFurnitureCode() + "]已被删除");
            return new ItemOutcome(itemId, false, RelocationFailCode.FURNITURE_DELETED, "家具已被删除");
        }

        String targetStation = item.getTargetStationCode().trim();
        String targetEmployee = item.getTargetEmployeeName().trim();
        String targetDepartment = item.getTargetDepartment().trim();

        // 1) 幂等收口：家具当前绑定与目标完全一致（可能由别的操作/上一次超时请求完成）
        if (Objects.equals(furniture.getFloorNum(), batch.getTargetFloorNum())
                && Objects.equals(targetStation, StrUtil.trim(furniture.getStationCode()))
                && Objects.equals(targetEmployee, StrUtil.trim(furniture.getEmployeeName()))
                && Objects.equals(targetDepartment, StrUtil.trim(furniture.getDepartment()))
                && Objects.equals(furniture.getBindStatus(), 1)) {
            Optional<BindRecord> existed = bindRecordRepository
                    .findByRelocationBatchNoAndFurnitureId(batch.getBatchNo(), furniture.getId());
            String note = existed.map(r -> "家具已在目标工位，按幂等成功收口（台账#" + r.getId() + "）")
                    .orElse("家具已由其他操作搬迁至同一目标，按幂等成功收口，未重复生成台账");
            item.setStatus(RelocationItem.STATUS_SUCCESS);
            item.setFailCode(null);
            item.setFailReason(null);
            item.setResultNote(note);
            existed.ifPresent(r -> item.setRecordId(r.getId()));
            item.setExecutedAt(java.time.LocalDateTime.now());
            itemRepository.save(item);
            return new ItemOutcome(itemId, true, null, note);
        }

        // 2) 原绑定快照已变化（且并未恰好到达目标）
        if (!snapshotMatches(item, furniture)) {
            String msg = "原绑定已变化：当前[" + describe(furniture) + "]与创建快照[" + describeSnapshot(item) + "]不一致，请撤回确认后重新校验";
            markFailed(item, RelocationFailCode.SNAPSHOT_CHANGED, msg);
            return new ItemOutcome(itemId, false, RelocationFailCode.SNAPSHOT_CHANGED, msg);
        }

        // 3) 目标工位与迁入楼层必须匹配
        if (!StationFloorRule.matchesFloor(targetStation, batch.getTargetFloorNum())) {
            String msg = "目标工位[" + targetStation + "]不属于迁入楼层" + batch.getTargetFloorNum() + "层";
            markFailed(item, RelocationFailCode.FLOOR_MISMATCH, msg);
            return new ItemOutcome(itemId, false, RelocationFailCode.FLOOR_MISMATCH, msg);
        }

        // 4) 目标工位被本批次之外的家具占用（行锁查询，与并发绑定/搬迁串行化）
        List<OfficeFurniture> holders = furnitureRepository.findByStationCodeForUpdate(targetStation);
        java.util.Set<Long> batchFurnitureIds = new java.util.HashSet<>(
                itemRepository.findFurnitureIdsByBatchId(batch.getId()));
        for (OfficeFurniture holder : holders) {
            if (holder.getId().equals(furniture.getId()) || batchFurnitureIds.contains(holder.getId())) {
                // 本批次内其他家具（如同一工位的桌+椅套装）不算外部占用
                continue;
            }
            String msg = "目标工位[" + targetStation + "]已被家具[" + holder.getFurnitureCode()
                    + "](" + StrUtil.blankToDefault(holder.getEmployeeName(), "未分配使用人")
                    + ")占用";
            markFailed(item, RelocationFailCode.STATION_OCCUPIED, msg);
            return new ItemOutcome(itemId, false, RelocationFailCode.STATION_OCCUPIED, msg);
        }

        // 通过全部校验：写绑定 + 写台账（同事务，失败一起回滚，保证绑定与台账一致）
        BindRecord record = new BindRecord();
        record.setFurnitureId(furniture.getId());
        record.setFurnitureCode(furniture.getFurnitureCode());
        record.setOperateType("RELOCATE");
        record.setOldStationCode(furniture.getStationCode());
        record.setOldEmployeeName(furniture.getEmployeeName());
        record.setOldDepartment(furniture.getDepartment());
        record.setNewStationCode(targetStation);
        record.setNewEmployeeName(targetEmployee);
        record.setNewDepartment(targetDepartment);
        record.setOperateReason("楼层搬迁批次[" + batch.getBatchNo() + "]"
                + batch.getSourceFloorNum() + "层→" + batch.getTargetFloorNum() + "层"
                + (StrUtil.isNotBlank(batch.getDepartment()) ? " " + batch.getDepartment() : ""));
        record.setOperatorName(StrUtil.blankToDefault(batch.getOperatorName(), "系统管理员"));
        record.setRelocationBatchNo(batch.getBatchNo());
        try {
            bindRecordRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException dup) {
            // 唯一约束兜底：并发下台账已由另一请求生成，转幂等成功
            BindRecord existed = bindRecordRepository
                    .findByRelocationBatchNoAndFurnitureId(batch.getBatchNo(), furniture.getId())
                    .orElse(null);
            String note = "检测到台账已生成，按幂等成功收口";
            item.setStatus(RelocationItem.STATUS_SUCCESS);
            item.setFailCode(null);
            item.setFailReason(null);
            item.setResultNote(note);
            if (existed != null) {
                item.setRecordId(existed.getId());
            }
            item.setExecutedAt(java.time.LocalDateTime.now());
            itemRepository.save(item);
            log.warn("relocation ledger duplicate suppressed: batch={}, furniture={}",
                    batch.getBatchNo(), furniture.getId());
            return new ItemOutcome(itemId, true, null, note);
        }

        Integer oldFloor = furniture.getFloorNum();
        furniture.setFloorNum(batch.getTargetFloorNum());
        furniture.setStationCode(targetStation);
        furniture.setEmployeeName(targetEmployee);
        furniture.setDepartment(targetDepartment);
        furniture.setBindStatus(1);
        furnitureRepository.save(furniture);

        item.setStatus(RelocationItem.STATUS_SUCCESS);
        item.setFailCode(null);
        item.setFailReason(null);
        item.setRecordId(record.getId());
        item.setResultNote("搬迁成功" + (oldFloor != null && !oldFloor.equals(batch.getTargetFloorNum())
                ? "（" + oldFloor + "层→" + batch.getTargetFloorNum() + "层）" : ""));
        item.setExecutedAt(java.time.LocalDateTime.now());
        itemRepository.save(item);
        return new ItemOutcome(itemId, true, null, item.getResultNote());
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

    /**
     * 全部条目搬迁成功后释放该批次的在途家具占用（独立事务）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseBatchLocks(Long batchId) {
        activeFurnitureRepository.deleteByBatchId(batchId);
    }

    /**
     * 未预期异常兜底：独立事务把条目标记为失败，避免它一直停在待执行状态
     * （成功条目绝不能被覆盖为失败）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markUnexpectedFailure(Long itemId, String message) {
        RelocationItem item = itemRepository.findById(itemId).orElse(null);
        if (item == null || RelocationItem.STATUS_SUCCESS.equals(item.getStatus())) {
            return;
        }
        item.setStatus(RelocationItem.STATUS_FAILED);
        item.setFailCode("EXEC_ERROR");
        String reason = cn.hutool.core.util.StrUtil.blankToDefault(message, "执行异常");
        item.setFailReason(reason.length() > 480 ? reason.substring(0, 480) : reason);
        item.setExecutedAt(java.time.LocalDateTime.now());
        itemRepository.save(item);
    }

    private void markFailed(RelocationItem item, String code, String message) {
        item.setStatus(RelocationItem.STATUS_FAILED);
        item.setFailCode(code);
        item.setFailReason(message);
        item.setExecutedAt(java.time.LocalDateTime.now());
        itemRepository.save(item);
    }
}
