package com.workspace.controller;

import com.workspace.common.Result;
import com.workspace.dto.RelocationBatchSaveDTO;
import com.workspace.dto.RelocationOperatorDTO;
import com.workspace.dto.RelocationValidationVO;
import com.workspace.entity.RelocationBatch;
import com.workspace.service.RelocationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/relocation")
public class RelocationController {

    private final RelocationService relocationService;

    public RelocationController(RelocationService relocationService) {
        this.relocationService = relocationService;
    }

    @PostMapping("/batch")
    public Result<RelocationBatch> create(@RequestBody @Valid RelocationBatchSaveDTO dto) {
        return Result.success(relocationService.createBatch(dto));
    }

    @PutMapping("/batch/{id}")
    public Result<RelocationBatch> update(@PathVariable Long id,
                                          @RequestBody @Valid RelocationBatchSaveDTO dto) {
        return Result.success(relocationService.updateBatch(id, dto));
    }

    @GetMapping("/batch/page")
    public Result<Page<RelocationBatch>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer sourceFloorNum,
            @RequestParam(required = false) Integer targetFloorNum,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createTime"));
        return Result.success(relocationService.pageBatches(keyword, status, sourceFloorNum,
                targetFloorNum, pageable));
    }

    @GetMapping("/batch/{id}")
    public Result<RelocationBatch> detail(@PathVariable Long id) {
        return Result.success(relocationService.getDetail(id));
    }

    /** 逐项校验（不改变状态，待确认页可反复查看） */
    @GetMapping("/batch/{id}/validate")
    public Result<RelocationValidationVO> validate(@PathVariable Long id) {
        return Result.success(relocationService.validate(id));
    }

    /** 确认：逐项校验，无冲突才进入可执行；有冲突返回逐项问题并保持待确认 */
    @PostMapping("/batch/{id}/confirm")
    public Result<RelocationValidationVO> confirm(@PathVariable Long id,
                                                  @RequestBody(required = false) RelocationOperatorDTO dto) {
        String operator = dto == null ? null : dto.getOperatorName();
        return Result.success(relocationService.confirm(id, operator));
    }

    /** 撤回确认：回到待确认可编辑，原快照保留可追溯 */
    @PostMapping("/batch/{id}/withdraw")
    public Result<RelocationBatch> withdraw(@PathVariable Long id,
                                            @RequestBody(required = false) RelocationOperatorDTO dto) {
        return Result.success(relocationService.withdraw(id,
                dto == null ? null : dto.getOperatorName()));
    }

    /** 撤销：仅待确认/可执行且无成功条目 */
    @PostMapping("/batch/{id}/cancel")
    public Result<RelocationBatch> cancel(@PathVariable Long id,
                                          @RequestBody(required = false) RelocationOperatorDTO dto) {
        return Result.success(relocationService.cancel(id,
                dto == null ? null : dto.getOperatorName()));
    }

    /** 执行搬迁 / 部分失败后仅重试失败项（服务端幂等，重复提交安全） */
    @PostMapping("/batch/{id}/execute")
    public Result<RelocationBatch> execute(@PathVariable Long id,
                                           @RequestBody(required = false) RelocationOperatorDTO dto) {
        return Result.success(relocationService.execute(id,
                dto == null ? null : dto.getOperatorName()));
    }
}
