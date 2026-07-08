package com.workspace.controller;

import com.workspace.common.Result;
import com.workspace.entity.BindRecord;
import com.workspace.service.FurnitureService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
}
