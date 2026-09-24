package com.workspace.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "relocation_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ri_batch_station", columnNames = {"batchId", "targetStationCode"}),
        @UniqueConstraint(name = "uk_ri_batch_furniture", columnNames = {"batchId", "furnitureId"})
}, indexes = {
        @Index(name = "idx_ri_batch", columnList = "batchId"),
        @Index(name = "idx_ri_furniture", columnList = "furnitureId"),
        @Index(name = "idx_ri_status", columnList = "status")
})
public class RelocationItem {

    /** 待执行 */
    public static final String STATUS_PENDING = "PENDING";
    /** 成功 */
    public static final String STATUS_SUCCESS = "SUCCESS";
    /** 失败 */
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", insertable = false, updatable = false)
    private RelocationBatch batch;

    @Column(nullable = false)
    private Long furnitureId;

    @Column(length = 50)
    private String furnitureCode;

    private Integer snapshotFloorNum;

    @Column(length = 50)
    private String snapshotStationCode;

    @Column(length = 100)
    private String snapshotEmployeeName;

    @Column(length = 50)
    private String snapshotDepartment;

    private Integer snapshotBindStatus;

    @Column(nullable = false, length = 50)
    private String targetStationCode;

    @Column(nullable = false, length = 100)
    private String targetEmployeeName;

    @Column(nullable = false, length = 50)
    private String targetDepartment;

    @Column(nullable = false, length = 20)
    private String status = STATUS_PENDING;

    @Column(length = 50)
    private String failCode;

    @Column(length = 500)
    private String failReason;

    @Column(length = 500)
    private String resultNote;

    private Long recordId;

    @Column(nullable = false)
    private Integer sortOrder = 0;

    private LocalDateTime executedAt;

    @Column(nullable = false)
    @CreationTimestamp
    private LocalDateTime createTime;

    @UpdateTimestamp
    private LocalDateTime updateTime;
}
