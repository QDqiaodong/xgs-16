package com.workspace.service.impl;

import cn.hutool.core.date.DateUtil;
import com.workspace.entity.OfficeFurniture;
import com.workspace.service.ExcelExportService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ExcelExportServiceImpl implements ExcelExportService {

    private static final String[] HEADERS = {
            "序号", "桌椅编号", "类型", "款式", "品牌", "尺寸规格",
            "价格", "工位编号", "使用人", "部门", "区域", "绑定状态", "备注"
    };

    @Override
    public ByteArrayOutputStream exportFloorDetail(Integer floorNum, List<OfficeFurniture> furnitures) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(floorNum + "层办公资产明细");

            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle summaryStyle = createSummaryStyle(workbook);

            int rowIndex = 0;

            Row titleRow = sheet.createRow(rowIndex++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(floorNum + "层 办公桌椅资产明细清单");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, HEADERS.length - 1));

            Row infoRow = sheet.createRow(rowIndex++);
            infoRow.createCell(0).setCellValue("生成时间：" + DateUtil.now());
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(1, 1, 0, 2));
            infoRow.createCell(3).setCellValue("导出楼层：" + floorNum + "层");
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(1, 1, 3, 5));

            rowIndex++;

            Row headerRow = sheet.createRow(rowIndex++);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            long boundCount = furnitures.stream().filter(f -> f.getBindStatus() == 1).count();
            long unboundCount = furnitures.size() - boundCount;

            int seq = 1;
            for (OfficeFurniture f : furnitures) {
                Row dataRow = sheet.createRow(rowIndex++);
                setCellValue(dataRow, 0, seq++, dataStyle);
                setCellValue(dataRow, 1, f.getFurnitureCode(), dataStyle);
                setCellValue(dataRow, 2, f.getFurnitureType(), dataStyle);
                setCellValue(dataRow, 3, f.getStyleName(), dataStyle);
                setCellValue(dataRow, 4, f.getBrand(), dataStyle);
                setCellValue(dataRow, 5, f.getSizeSpec(), dataStyle);
                setCellValue(dataRow, 6, f.getPrice() != null ? "¥" + f.getPrice() : "", dataStyle);
                setCellValue(dataRow, 7, f.getStationCode(), dataStyle);
                setCellValue(dataRow, 8, f.getEmployeeName(), dataStyle);
                setCellValue(dataRow, 9, f.getDepartment(), dataStyle);
                setCellValue(dataRow, 10, f.getAreaName(), dataStyle);
                setCellValue(dataRow, 11, f.getBindStatus() == 1 ? "已绑定" : "未绑定", dataStyle);
                setCellValue(dataRow, 12, f.getRemark(), dataStyle);
            }

            rowIndex++;
            Row summaryRow1 = sheet.createRow(rowIndex++);
            summaryRow1.createCell(0).setCellValue("统计汇总");
            summaryRow1.getCell(0).setCellStyle(summaryStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowIndex - 1, rowIndex - 1, 0, 2));

            Row summaryRow2 = sheet.createRow(rowIndex++);
            summaryRow2.createCell(0).setCellValue("资产总数：" + furnitures.size() + " 套");
            summaryRow2.getCell(0).setCellStyle(summaryStyle);
            summaryRow2.createCell(3).setCellValue("已绑定：" + boundCount + " 套");
            summaryRow2.getCell(3).setCellStyle(summaryStyle);
            summaryRow2.createCell(6).setCellValue("未绑定：" + unboundCount + " 套");
            summaryRow2.getCell(6).setCellStyle(summaryStyle);
            if (furnitures.size() > 0) {
                double rate = (double) boundCount / furnitures.size() * 100;
                summaryRow2.createCell(9).setCellValue("绑定率：" + String.format("%.2f", rate) + "%");
                summaryRow2.getCell(9).setCellStyle(summaryStyle);
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 1000);
            }
            sheet.setColumnWidth(0, 256 * 8);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out;
        } catch (IOException e) {
            throw new RuntimeException("Excel 导出失败: " + e.getMessage());
        }
    }

    @Override
    public ByteArrayOutputStream exportAllFloorDetail(List<OfficeFurniture> furnitures) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Map<Integer, List<OfficeFurniture>> groupByFloor = furnitures.stream()
                    .collect(Collectors.groupingBy(OfficeFurniture::getFloorNum, java.util.TreeMap::new, Collectors.toList()));

            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);

            for (Map.Entry<Integer, List<OfficeFurniture>> entry : groupByFloor.entrySet()) {
                Integer floorNum = entry.getKey();
                List<OfficeFurniture> floorList = entry.getValue();

                Sheet sheet = workbook.createSheet(floorNum + "层");
                int rowIndex = 0;

                Row titleRow = sheet.createRow(rowIndex++);
                Cell titleCell = titleRow.createCell(0);
                titleCell.setCellValue(floorNum + "层 办公桌椅资产明细");
                titleCell.setCellStyle(titleStyle);
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, HEADERS.length - 1));

                Row infoRow = sheet.createRow(rowIndex++);
                infoRow.createCell(0).setCellValue("生成时间：" + DateUtil.now());
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(1, 1, 0, 2));

                rowIndex++;

                Row headerRow = sheet.createRow(rowIndex++);
                for (int i = 0; i < HEADERS.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(HEADERS[i]);
                    cell.setCellStyle(headerStyle);
                }

                int seq = 1;
                for (OfficeFurniture f : floorList) {
                    Row dataRow = sheet.createRow(rowIndex++);
                    setCellValue(dataRow, 0, seq++, dataStyle);
                    setCellValue(dataRow, 1, f.getFurnitureCode(), dataStyle);
                    setCellValue(dataRow, 2, f.getFurnitureType(), dataStyle);
                    setCellValue(dataRow, 3, f.getStyleName(), dataStyle);
                    setCellValue(dataRow, 4, f.getBrand(), dataStyle);
                    setCellValue(dataRow, 5, f.getSizeSpec(), dataStyle);
                    setCellValue(dataRow, 6, f.getPrice() != null ? "¥" + f.getPrice() : "", dataStyle);
                    setCellValue(dataRow, 7, f.getStationCode(), dataStyle);
                    setCellValue(dataRow, 8, f.getEmployeeName(), dataStyle);
                    setCellValue(dataRow, 9, f.getDepartment(), dataStyle);
                    setCellValue(dataRow, 10, f.getAreaName(), dataStyle);
                    setCellValue(dataRow, 11, f.getBindStatus() == 1 ? "已绑定" : "未绑定", dataStyle);
                    setCellValue(dataRow, 12, f.getRemark(), dataStyle);
                }

                long boundCount = floorList.stream().filter(f -> f.getBindStatus() == 1).count();
                rowIndex++;
                Row sumRow = sheet.createRow(rowIndex);
                sumRow.createCell(0).setCellValue("小计：共 " + floorList.size() + " 套，已绑定 " + boundCount + " 套，未绑定 " + (floorList.size() - boundCount) + " 套");
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowIndex, rowIndex, 0, HEADERS.length - 1));

                for (int i = 0; i < HEADERS.length; i++) {
                    sheet.autoSizeColumn(i);
                    sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 800);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out;
        } catch (IOException e) {
            throw new RuntimeException("Excel 导出失败: " + e.getMessage());
        }
    }

    private void setCellValue(Row row, int col, Object value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value != null) {
            if (value instanceof Number) {
                cell.setCellValue(((Number) value).doubleValue());
            } else {
                cell.setCellValue(String.valueOf(value));
            }
        }
        cell.setCellStyle(style);
    }

    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 18);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        return style;
    }

    private CellStyle createSummaryStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
}
