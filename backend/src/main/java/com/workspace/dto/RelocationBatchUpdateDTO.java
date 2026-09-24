package com.workspace.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 撤回确认后调整批次内容（仅 PENDING 状态可改）。
 */
@Data
public class RelocationBatchUpdateDTO {

    @NotNull(message = "批次ID不能为空")
    private Long id;

    @NotBlank(message = "批次名称不能为空")
    private String batchName;

    private String targetDepartment;

    private String remark;

    @NotNull(message = "搬迁条目不能为空")
    @Valid
    private List<RelocationBatchCreateDTO.ItemDTO> items;
}
