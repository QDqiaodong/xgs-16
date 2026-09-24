package com.workspace.controller;

import com.workspace.common.Result;
import com.workspace.dto.RelocationBatchCreateDTO;
import com.workspace.dto.RelocationBatchUpdateDTO;
import com.workspace.dto.RelocationBatchVO;
import com.workspace.service.RelocationBatchService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

/**
 * 楼层搬迁交接台
 */
@RestController
@RequestMapping("/relocation")
public class RelocationBatchController {

    private final RelocationBatchService relocationBatchService;

    public RelocationBatchController(RelocationBatchService relocationBatchService) {
        this.relocationBatchService = relocationBatchService;
    }

    /** 批次列表（支持状态筛选、批次号/名称/操作人关键字） */
    @GetMapping("/page")
    public Result<Page<RelocationBatchVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return Result.success(relocationBatchService.page(keyword, status, pageable));
    }

    /** 批次详情：批次信息 + 逐项快照/校验/执行结果 + 本次搬迁台账记录 */
    @GetMapping("/{id}")
    public Result<RelocationBatchVO> detail(@PathVariable Long id) {
        return Result.success(relocationBatchService.detail(id));
    }

    /** 创建批次（保存为待确认，同时返回逐项校验结果） */
    @PostMapping
    public Result<RelocationBatchVO> create(@RequestBody @Valid RelocationBatchCreateDTO dto) {
        return Result.success(relocationBatchService.create(dto));
    }

    /** 撤回确认后调整批次内容（仅待确认状态可改） */
    @PutMapping
    public Result<RelocationBatchVO> update(@RequestBody @Valid RelocationBatchUpdateDTO dto) {
        return Result.success(relocationBatchService.update(dto));
    }

    /** 按最新数据逐项重新校验（不改批次状态） */
    @PostMapping("/{id}/validate")
    public Result<RelocationBatchVO> validate(@PathVariable Long id) {
        return Result.success(relocationBatchService.validate(id));
    }

    /** 确认批次：无冲突才进入可执行，有冲突保持待确认 */
    @PostMapping("/{id}/confirm")
    public Result<RelocationBatchVO> confirm(@PathVariable Long id) {
        return Result.success(relocationBatchService.confirm(id));
    }

    /** 撤回确认：可执行 -> 待确认 */
    @PostMapping("/{id}/withdraw")
    public Result<RelocationBatchVO> withdraw(@PathVariable Long id) {
        return Result.success(relocationBatchService.withdraw(id));
    }

    /** 执行搬迁 / 部分失败后重试失败项（接口幂等，可安全重试） */
    @PostMapping("/{id}/execute")
    public Result<RelocationBatchVO> execute(@PathVariable Long id) {
        return Result.success(relocationBatchService.execute(id));
    }

    /** 撤销批次（仅未执行或未产生成功条目的批次） */
    @PostMapping("/{id}/cancel")
    public Result<RelocationBatchVO> cancel(@PathVariable Long id) {
        return Result.success(relocationBatchService.cancel(id));
    }
}
