package com.workspace.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "bind_record", indexes = {
        @Index(name = "idx_furniture_id", columnList = "furnitureId"),
        @Index(name = "idx_record_time", columnList = "recordTime"),
        @Index(name = "idx_relocation_batch", columnList = "relocationBatchNo")
})
public class BindRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long furnitureId;

    @Column(length = 50)
    private String furnitureCode;

    @Column(length = 20)
    private String operateType;

    @Column(length = 50)
    private String oldStationCode;

    @Column(length = 50)
    private String newStationCode;

    @Column(length = 100)
    private String oldEmployeeName;

    @Column(length = 100)
    private String newEmployeeName;

    @Column(length = 100)
    private String oldDepartment;

    @Column(length = 100)
    private String newDepartment;

    @Column(length = 500)
    private String operateReason;

    @Column(length = 100)
    private String operatorName;

    @Column(length = 40)
    private String relocationBatchNo;

    @Column(nullable = false)
    @CreationTimestamp
    private LocalDateTime recordTime;
}
