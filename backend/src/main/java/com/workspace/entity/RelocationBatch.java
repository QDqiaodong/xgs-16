package com.workspace.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "relocation_batch", indexes = {
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_create_time", columnList = "createTime")
})
public class RelocationBatch {

    /** 待确认（草稿，尚未通过逐项校验） */
    public static final String STATUS_PENDING = "PENDING";
    /** 可执行（已确认，校验全部通过） */
    public static final String STATUS_READY = "READY";
    /** 执行中 */
    public static final String STATUS_RUNNING = "RUNNING";
    /** 部分失败（有成功也有失败，只能重试失败项） */
    public static final String STATUS_PARTIAL_FAILED = "PARTIAL_FAILED";
    /** 已完成（全部条目成功） */
    public static final String STATUS_COMPLETED = "COMPLETED";
    /** 已撤销（未产生成功条目前撤回） */
    public static final String STATUS_CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, unique = true)
    private String batchNo;

    @Column(nullable = false, length = 200)
    private String batchName;

    @Column(nullable = false)
    private Integer sourceFloor;

    @Column(nullable = false)
    private Integer targetFloor;

    @Column(nullable = false, length = 20)
    private String status = STATUS_PENDING;

    @Column(length = 100)
    private String targetDepartment;

    @Column(length = 100)
    private String operatorName;

    @Column(length = 500)
    private String remark;

    @Column(nullable = false)
    private Integer totalCount = 0;

    @Column(nullable = false)
    private Integer successCount = 0;

    @Column(nullable = false)
    private Integer failCount = 0;

    private LocalDateTime lastExecutedTime;

    private LocalDateTime confirmedTime;

    private LocalDateTime completedTime;

    private LocalDateTime cancelledTime;

    @Version
    private Integer version;

    @Column(nullable = false)
    @CreationTimestamp
    private LocalDateTime createTime;

    @UpdateTimestamp
    private LocalDateTime updateTime;
}
