package com.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FurnitureCreateDTO {

    @NotBlank(message = "桌椅编号不能为空")
    private String furnitureCode;

    @NotBlank(message = "家具类型不能为空")
    private String furnitureType;

    private String styleName;

    private String brand;

    private BigDecimal price;

    private String sizeSpec;

    private String remark;

    private String imageUrl;

    private String specTemplate;

    @NotNull(message = "适配楼层不能为空")
    private Integer floorNum;

    private String areaName;
}
