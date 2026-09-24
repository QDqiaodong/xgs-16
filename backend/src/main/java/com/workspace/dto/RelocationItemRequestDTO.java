package com.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RelocationItemRequestDTO {

    @NotNull(message = "家具ID不能为空")
    private Long furnitureId;

    @NotBlank(message = "目标工位不能为空")
    private String targetStationCode;

    @NotBlank(message = "目标使用人不能为空")
    private String targetEmployeeName;

    @NotBlank(message = "目标部门不能为空")
    private String targetDepartment;
}
