package com.workspace.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UnbindStationDTO {

    @NotNull(message = "家具ID不能为空")
    private Long furnitureId;

    private String operateReason;

    private String operatorName;
}
