package com.workspace.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class RelocationBatchCreateDTO {

    @NotBlank(message = "批次名称不能为空")
    private String batchName;

    @NotNull(message = "迁出楼层不能为空")
    private Integer sourceFloor;

    @NotNull(message = "迁入楼层不能为空")
    private Integer targetFloor;

    /** 默认目标部门，条目未单独指定时使用 */
    private String targetDepartment;

    private String operatorName;

    private String remark;

    @NotNull(message = "搬迁条目不能为空")
    @Valid
    private List<ItemDTO> items;

    @Data
    public static class ItemDTO {

        @NotNull(message = "家具ID不能为空")
        private Long furnitureId;

        @NotBlank(message = "目标工位不能为空")
        private String targetStationCode;

        private String targetEmployeeName;

        /** 为空时取批次默认目标部门 */
        private String targetDepartment;
    }
}
