package com.workspace.service;

import com.workspace.dto.RelocationBatchCreateDTO;
import com.workspace.dto.RelocationBatchUpdateDTO;
import com.workspace.dto.RelocationBatchVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RelocationBatchService {

    RelocationBatchVO create(RelocationBatchCreateDTO dto);

    RelocationBatchVO update(RelocationBatchUpdateDTO dto);

    /** 按最新数据逐项重新校验（不改状态），冲突结果写入每条条目 */
    RelocationBatchVO validate(Long id);

    /** 确认：校验全部通过才能进入可执行，存在冲突保持待确认 */
    RelocationBatchVO confirm(Long id);

    /** 撤回确认：可执行 -> 待确认，快照仍保留可追溯 */
    RelocationBatchVO withdraw(Long id);

    /** 执行搬迁：READY 全量执行 / PARTIAL_FAILED 仅重试失败项；并发、超时重试均幂等 */
    RelocationBatchVO execute(Long id);

    /** 撤销：仅未执行或未产生成功条目的批次可撤销，完成后释放家具占用 */
    RelocationBatchVO cancel(Long id);

    RelocationBatchVO detail(Long id);

    Page<RelocationBatchVO> page(String keyword, String status, Pageable pageable);
}
