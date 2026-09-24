package com.workspace.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "relocation_batch", indexes = {
        @Index(name = "idx_rb_status", columnList = "status"),
        @Index(name = "idx_rb_floors", columnList = "sourceFloorNum,targetFloorNum"),
        @Index(name = "idx_rb_create_time", columnList = "createTime")
})
public class RelocationBatch {

    /** 待确认 */
    public static final String STATUS_PENDING = "PENDING";
    /** 可执行 */
    public static final String STATUS_READY = "READY";
    /** 执行中 */
    public static final String STATUS_RUNNING = "RUNNING";
    /** 部分失败 */
    public static final String STATUS_PARTIAL_FAILED = "PARTIAL_FAILED";
    /** 已完成 */
    public static final String STATUS_COMPLETED = "COMPLETED";
    /** 已撤销 */
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 未结束状态：在途家具占用锁存在期间 */
    public static final List<String> ACTIVE_STATUSES =
            List.of(STATUS_PENDING, STATUS_READY, STATUS_RUNNING, STATUS_PARTIAL_FAILED);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40, unique = true)
    private String batchNo;

    @Column(nullable = false, length = 200)
    private String batchName;

    @Column(nullable = false)
    private Integer sourceFloorNum;

    @Column(nullable = false)
    private Integer targetFloorNum;

    @Column(length = 50)
    private String department;

    @Column(length = 500)
    private String remark;

    @Column(length = 100)
    private String operatorName;

    @Column(nullable = false, length = 20)
    private String status = STATUS_PENDING;

    @Column(nullable = false)
    private Integer totalCount = 0;

    @Column(nullable = false)
    private Integer successCount = 0;

    @Column(nullable = false)
    private Integer failCount = 0;

    private LocalDateTime confirmedAt;

    private LocalDateTime executedAt;

    private LocalDateTime completedAt;

    private LocalDateTime revokedAt;

    @Column(nullable = false)
    @CreationTimestamp
    private LocalDateTime createTime;

    @UpdateTimestamp
    private LocalDateTime updateTime;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("sortOrder ASC, id ASC")
    private List<RelocationItem> items = new ArrayList<>();
}
