package com.workspace.controller;

import com.workspace.common.Result;
import com.workspace.dto.BindStationDTO;
import com.workspace.dto.FurnitureCreateDTO;
import com.workspace.dto.FurnitureUpdateDTO;
import com.workspace.dto.UnbindStationDTO;
import com.workspace.entity.OfficeFurniture;
import com.workspace.service.ExcelExportService;
import com.workspace.service.FurnitureService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/furniture")
public class FurnitureController {

    private final FurnitureService furnitureService;
    private final ExcelExportService excelExportService;

    public FurnitureController(FurnitureService furnitureService, ExcelExportService excelExportService) {
        this.furnitureService = furnitureService;
        this.excelExportService = excelExportService;
    }

    @PostMapping
    public Result<OfficeFurniture> create(@RequestBody @Valid FurnitureCreateDTO dto) {
        return Result.success(furnitureService.create(dto));
    }

    @PutMapping
    public Result<OfficeFurniture> update(@RequestBody @Valid FurnitureUpdateDTO dto) {
        return Result.success(furnitureService.update(dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        furnitureService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<OfficeFurniture> getById(@PathVariable Long id) {
        return Result.success(furnitureService.getById(id));
    }

    @GetMapping("/code/{code}")
    public Result<OfficeFurniture> getByCode(@PathVariable String code) {
        return Result.success(furnitureService.getByCode(code));
    }

    @GetMapping("/page")
    public Result<Page<OfficeFurniture>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer floorNum,
            @RequestParam(required = false) Integer bindStatus,
            @RequestParam(required = false) String furnitureType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return Result.success(furnitureService.list(keyword, floorNum, bindStatus, furnitureType, pageable));
    }

    @GetMapping("/list")
    public Result<List<OfficeFurniture>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer floorNum,
            @RequestParam(required = false) Integer bindStatus,
            @RequestParam(required = false) String furnitureType) {
        Pageable pageable = PageRequest.of(0, 10000, Sort.by(Sort.Direction.DESC, "createTime"));
        return Result.success(furnitureService.list(keyword, floorNum, bindStatus, furnitureType, pageable).getContent());
    }

    @PostMapping("/bind")
    public Result<OfficeFurniture> bindStation(@RequestBody @Valid BindStationDTO dto) {
        return Result.success(furnitureService.bindStation(dto));
    }

    @PostMapping("/unbind")
    public Result<OfficeFurniture> unbindStation(@RequestBody @Valid UnbindStationDTO dto) {
        return Result.success(furnitureService.unbindStation(dto));
    }

    @GetMapping("/floor/{floorNum}")
    public Result<List<OfficeFurniture>> getByFloor(@PathVariable Integer floorNum) {
        return Result.success(furnitureService.getByFloor(floorNum));
    }

    @GetMapping("/station/{stationCode}")
    public Result<List<OfficeFurniture>> getByStationCode(@PathVariable String stationCode) {
        return Result.success(furnitureService.getByStationCode(stationCode));
    }

    @GetMapping("/group-by-floor")
    public Result<Map<Integer, List<OfficeFurniture>>> getGroupByFloor() {
        return Result.success(furnitureService.getGroupByFloor());
    }

    @GetMapping("/floors")
    public Result<List<Integer>> getAllFloors() {
        return Result.success(furnitureService.getAllFloors());
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics() {
        return Result.success(furnitureService.getStatistics());
    }

    @GetMapping("/export/floor/{floorNum}")
    public void exportFloorDetail(@PathVariable Integer floorNum, HttpServletResponse response) throws IOException {
        List<OfficeFurniture> furnitures = furnitureService.getByFloor(floorNum);
        ByteArrayOutputStream out = excelExportService.exportFloorDetail(floorNum, furnitures);

        String filename = URLEncoder.encode(floorNum + "层_办公资产明细_" + System.currentTimeMillis() + ".xlsx", StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);
        response.getOutputStream().write(out.toByteArray());
        response.getOutputStream().flush();
    }

    @GetMapping("/export/all")
    public void exportAllFloorDetail(HttpServletResponse response) throws IOException {
        Pageable pageable = PageRequest.of(0, 100000, Sort.by(Sort.Direction.ASC, "floorNum", "furnitureCode"));
        List<OfficeFurniture> furnitures = furnitureService.list(null, null, null, null, pageable).getContent();
        ByteArrayOutputStream out = excelExportService.exportAllFloorDetail(furnitures);

        String filename = URLEncoder.encode("全楼层_办公资产明细_" + System.currentTimeMillis() + ".xlsx", StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);
        response.getOutputStream().write(out.toByteArray());
        response.getOutputStream().flush();
    }
}
