package com.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RelocationOperatorDTO {

    @NotBlank(message = "操作人不能为空")
    private String operatorName;
}
