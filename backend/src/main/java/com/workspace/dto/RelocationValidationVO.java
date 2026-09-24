package com.workspace.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批次逐项校验结果。确认前必须展示给行政人员，存在 blocking 问题不得进入可执行状态。
 */
@Data
public class RelocationValidationVO {

    private Long batchId;

    private String batchNo;

    /** true 表示无阻断性冲突，可以确认进入可执行 */
    private Boolean passable;

    private Integer totalCount;

    private Integer problemCount;

    private List<ItemCheck> items = new ArrayList<>();

    @Data
    public static class ItemCheck {
        private Long itemId;
        private Long furnitureId;
        private String furnitureCode;
        private String targetStationCode;
        private String targetEmployeeName;
        private String targetDepartment;
        /** 是否通过 */
        private Boolean passed;
        /** 问题原因码，如 FURNITURE_DELETED */
        private String failCode;
        /** 问题描述（中文，可直接展示） */
        private String message;

        public static ItemCheck ok(Long itemId, Long furnitureId, String code,
                                   String station, String employee, String department) {
            ItemCheck c = new ItemCheck();
            c.setItemId(itemId);
            c.setFurnitureId(furnitureId);
            c.setFurnitureCode(code);
            c.setTargetStationCode(station);
            c.setTargetEmployeeName(employee);
            c.setTargetDepartment(department);
            c.setPassed(true);
            c.setMessage("校验通过");
            return c;
        }

        public static ItemCheck fail(Long itemId, Long furnitureId, String code,
                                     String station, String employee, String department,
                                     String failCode, String message) {
            ItemCheck c = new ItemCheck();
            c.setItemId(itemId);
            c.setFurnitureId(furnitureId);
            c.setFurnitureCode(code);
            c.setTargetStationCode(station);
            c.setTargetEmployeeName(employee);
            c.setTargetDepartment(department);
            c.setPassed(false);
            c.setFailCode(failCode);
            c.setMessage(message);
            return c;
        }
    }
}
