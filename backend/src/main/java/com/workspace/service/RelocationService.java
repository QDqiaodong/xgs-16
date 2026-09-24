package com.workspace.service;

import com.workspace.dto.RelocationBatchSaveDTO;
import com.workspace.dto.RelocationValidationVO;
import com.workspace.entity.RelocationBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RelocationService {

    RelocationBatch createBatch(RelocationBatchSaveDTO dto);

    RelocationBatch updateBatch(Long id, RelocationBatchSaveDTO dto);

    Page<RelocationBatch> pageBatches(String keyword, String status, Integer sourceFloorNum,
                                      Integer targetFloorNum, Pageable pageable);

    RelocationBatch getDetail(Long id);

    /** 逐项校验（不落确认状态） */
    RelocationValidationVO validate(Long id);

    /** 逐项校验，无冲突时确认进入可执行；有冲突时保持待确认并返回逐项结果 */
    RelocationValidationVO confirm(Long id, String operatorName);

    /** 撤回确认，批次回到待确认可编辑，原快照仍保留在条目上可追溯 */
    RelocationBatch withdraw(Long id, String operatorName);

    /** 撤销批次（仅待确认/可执行且尚无成功条目时允许） */
    RelocationBatch cancel(Long id, String operatorName);

    /**
     * 执行搬迁（可执行）或重试失败项（部分失败）。
     * 成功条目逐件提交不回滚；全量成功才置为已完成，否则部分失败。
     */
    RelocationBatch execute(Long id, String operatorName);
}
