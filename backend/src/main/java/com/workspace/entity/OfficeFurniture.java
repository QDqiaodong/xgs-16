package com.workspace.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "office_furniture", indexes = {
        @Index(name = "idx_furniture_code", columnList = "furnitureCode", unique = true),
        @Index(name = "idx_station_code", columnList = "stationCode"),
        @Index(name = "idx_floor", columnList = "floorNum")
})
public class OfficeFurniture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, unique = true)
    private String furnitureCode;

    @Column(nullable = false, length = 20)
    private String furnitureType;

    @Column(length = 200)
    private String styleName;

    @Column(length = 100)
    private String brand;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Column(length = 50)
    private String sizeSpec;

    @Column(length = 500)
    private String remark;

    @Column(length = 500)
    private String imageUrl;

    @Column(length = 200)
    private String specTemplate;

    @Column(nullable = false)
    private Integer floorNum;

    @Column(length = 100)
    private String areaName;

    @Column(length = 50)
    private String stationCode;

    @Column(length = 100)
    private String employeeName;

    @Column(length = 50)
    private String department;

    @Column(nullable = false)
    @CreationTimestamp
    private LocalDateTime createTime;

    @UpdateTimestamp
    private LocalDateTime updateTime;

    @Column(nullable = false)
    private Integer bindStatus = 0;
}
