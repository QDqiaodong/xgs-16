package com.workspace.controller;

import com.workspace.common.Result;
import com.workspace.entity.BindRecord;
import com.workspace.service.FurnitureService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/record")
public class BindRecordController {

    private final FurnitureService furnitureService;

    public BindRecordController(FurnitureService furnitureService) {
        this.furnitureService = furnitureService;
    }

    @GetMapping("/furniture/{furnitureId}")
    public Result<List<BindRecord>> getByFurnitureId(@PathVariable Long furnitureId) {
        return Result.success(furnitureService.getRecordsByFurnitureId(furnitureId));
    }

    @GetMapping("/furniture-code/{furnitureCode}")
    public Result<List<BindRecord>> getByFurnitureCode(@PathVariable String furnitureCode) {
        return Result.success(furnitureService.getRecordsByFurnitureCode(furnitureCode));
    }

    @GetMapping("/page")
    public Result<Page<BindRecord>> page(
            @RequestParam(required = false) String furnitureCode,
            @RequestParam(required = false) String operateType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String relocationBatchNo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200),
                Sort.by(Sort.Direction.DESC, "recordTime"));
        return Result.success(furnitureService.pageRecords(furnitureCode, operateType, keyword,
                relocationBatchNo, pageable));
    }
}
