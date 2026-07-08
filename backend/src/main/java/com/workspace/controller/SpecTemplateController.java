package com.workspace.controller;

import com.workspace.common.Result;
import com.workspace.service.FurnitureService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/spec")
public class SpecTemplateController {

    private final FurnitureService furnitureService;

    public SpecTemplateController(FurnitureService furnitureService) {
        this.furnitureService = furnitureService;
    }

    @PostMapping("/{templateKey}")
    public Result<Void> saveSpecTemplate(@PathVariable String templateKey, @RequestBody Map<String, String> spec) {
        furnitureService.saveSpecTemplateToCache(templateKey, spec);
        return Result.success();
    }

    @GetMapping("/{templateKey}")
    public Result<Map<String, String>> getSpecTemplate(@PathVariable String templateKey) {
        return Result.success(furnitureService.getSpecTemplateFromCache(templateKey));
    }

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> getAllSpecTemplates() {
        return Result.success(furnitureService.getAllSpecTemplatesFromCache());
    }

    @DeleteMapping("/{templateKey}")
    public Result<Void> deleteSpecTemplate(@PathVariable String templateKey) {
        furnitureService.deleteSpecTemplateFromCache(templateKey);
        return Result.success();
    }
}
