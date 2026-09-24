package com.workspace.service.relocation;

import com.workspace.entity.RelocationBatch;
import com.workspace.entity.RelocationItem;
import com.workspace.repository.RelocationBatchRepository;
import com.workspace.repository.RelocationItemRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 搬迁批次状态机：所有批次级状态流转都以 @Version 乐观锁 + 期望前置状态做 CAS，
 * 两名管理员并发重复操作时只有一方能推进，另一方拿到最新状态后幂等返回。
 */
@Component
public class RelocationStateService {

    private final RelocationBatchRepository batchRepository;
    private final RelocationItemRepository itemRepository;

    public RelocationStateService(RelocationBatchRepository batchRepository,
                                  RelocationItemRepository itemRepository) {
        this.batchRepository = batchRepository;
        this.itemRepository = itemRepository;
    }

    public static class ConcurrentTransitionException extends RuntimeException {
        public ConcurrentTransitionException(String message) {
            super(message);
        }
    }

    /** 确认：PENDING -> READY（仅当逐项校验全部通过） */
    @Transactional
    public RelocationBatch confirm(Long batchId) {
        RelocationBatch batch = require(batchId);
        if (RelocationBatch.STATUS_READY.equals(batch.getStatus())) {
            return batch;
        }
        if (!RelocationBatch.STATUS_PENDING.equals(batch.getStatus())) {
            throw new IllegalStateException("当前状态[" + batch.getStatus() + "]不允许确认，仅待确认批次可确认");
        }
        List<RelocationItem> items = itemRepository.findByBatchIdOrderByIdAsc(batchId);
        long conflict = items.stream()
                .filter(i -> RelocationItem.VALIDATE_CONFLICT.equals(i.getValidateStatus()))
                .count();
        if (conflict > 0) {
            throw new IllegalStateException("存在 " + conflict + " 项校验冲突，不得进入可执行状态，请处理后重新校验");
        }
        batch.setStatus(RelocationBatch.STATUS_READY);
        batch.setConfirmedTime(LocalDateTime.now());
        return saveCas(batch);
    }

    /** 撤回确认：READY -> PENDING，原快照继续保留 */
    @Transactional
    public RelocationBatch withdraw(Long batchId) {
        RelocationBatch batch = require(batchId);
        if (RelocationBatch.STATUS_PENDING.equals(batch.getStatus())) {
            return batch;
        }
        if (!RelocationBatch.STATUS_READY.equals(batch.getStatus())) {
            throw new IllegalStateException("当前状态[" + batch.getStatus() + "]不允许撤回，仅可执行（未执行）批次可撤回");
        }
        batch.setStatus(RelocationBatch.STATUS_PENDING);
        batch.setConfirmedTime(null);
        return saveCas(batch);
    }

    /** 进入执行中：READY -> RUNNING；PARTIAL_FAILED/RUNNING(崩溃恢复) 允许继续 */
    @Transactional
    public RelocationBatch beginExecution(Long batchId) {
        RelocationBatch batch = require(batchId);
        String status = batch.getStatus();
        if (RelocationBatch.STATUS_READY.equals(status)
                || RelocationBatch.STATUS_PARTIAL_FAILED.equals(status)
                || RelocationBatch.STATUS_RUNNING.equals(status)) {
            batch.setStatus(RelocationBatch.STATUS_RUNNING);
            return saveCas(batch);
        }
        if (RelocationBatch.STATUS_COMPLETED.equals(status)) {
            return batch;
        }
        throw new IllegalStateException("当前状态[" + status + "]不允许执行，请先确认批次");
    }

    /**
     * 执行结束重算计数并落最终状态：
     * 全部成功 -> COMPLETED；有成功有失败 -> PARTIAL_FAILED。
     * 成功条目提交后计数只增不减，失败重试不会让已成功项回滚。
     */
    @Transactional
    public RelocationBatch finishExecution(Long batchId) {
        RelocationBatch batch = require(batchId);
        List<RelocationItem> items = itemRepository.findByBatchIdOrderByIdAsc(batchId);
        int success = 0;
        int failed = 0;
        for (RelocationItem item : items) {
            if (RelocationItem.RESULT_SUCCESS.equals(item.getResultStatus())
                    || RelocationItem.RESULT_IDEMPOTENT.equals(item.getResultStatus())) {
                success++;
            } else if (RelocationItem.RESULT_FAILED.equals(item.getResultStatus())) {
                failed++;
            }
        }
        batch.setSuccessCount(success);
        batch.setFailCount(failed);
        batch.setLastExecutedTime(LocalDateTime.now());
        if (failed == 0) {
            batch.setStatus(RelocationBatch.STATUS_COMPLETED);
            batch.setCompletedTime(LocalDateTime.now());
        } else {
            batch.setStatus(RelocationBatch.STATUS_PARTIAL_FAILED);
        }
        return saveCas(batch);
    }

    /**
     * 撤销：仅 PENDING / READY（未产生任何成功条目）可撤销。
     * COMPLETED/PARTIAL_FAILED/RUNNING 一律拒绝。
     */
    @Transactional
    public RelocationBatch cancel(Long batchId) {
        RelocationBatch batch = require(batchId);
        String status = batch.getStatus();
        if (RelocationBatch.STATUS_CANCELLED.equals(status)) {
            return batch;
        }
        if (!RelocationBatch.STATUS_PENDING.equals(status) && !RelocationBatch.STATUS_READY.equals(status)) {
            throw new IllegalStateException("当前状态[" + status + "]不允许撤销：已产生成功条目或已完成的批次不得撤销");
        }
        if (batch.getSuccessCount() != null && batch.getSuccessCount() > 0) {
            throw new IllegalStateException("批次已产生成功条目，不得撤销");
        }
        batch.setStatus(RelocationBatch.STATUS_CANCELLED);
        batch.setCancelledTime(LocalDateTime.now());
        return saveCas(batch);
    }

    private RelocationBatch require(Long batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("搬迁批次不存在: " + batchId));
    }

    private RelocationBatch saveCas(RelocationBatch batch) {
        try {
            return batchRepository.save(batch);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConcurrentTransitionException("批次状态刚被其他管理员更新，请刷新后重试");
        }
    }
}
