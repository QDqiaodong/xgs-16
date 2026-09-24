package com.workspace.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class RelocationBatchSaveDTO {

    @NotBlank(message = "批次名称不能为空")
    private String batchName;

    @NotNull(message = "迁出楼层不能为空")
    private Integer sourceFloorNum;

    @NotNull(message = "迁入楼层不能为空")
    private Integer targetFloorNum;

    private String department;

    private String remark;

    private String operatorName;

    @Valid
    @NotEmpty(message = "至少选择一件家具")
    private List<RelocationItemRequestDTO> items;
}
