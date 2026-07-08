package com.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BindStationDTO {

    @NotNull(message = "家具ID不能为空")
    private Long furnitureId;

    @NotBlank(message = "工位编号不能为空")
    private String stationCode;

    private String employeeName;

    private String department;

    private String operateReason;

    private String operatorName;
}
