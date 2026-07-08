package com.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FurnitureUpdateDTO {

    private Long id;

    private String furnitureType;

    private String styleName;

    private String brand;

    private BigDecimal price;

    private String sizeSpec;

    private String remark;

    private String imageUrl;

    private String specTemplate;

    private Integer floorNum;

    private String areaName;
}
