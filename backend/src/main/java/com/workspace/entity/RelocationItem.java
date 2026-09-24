package com.workspace.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "relocation_item", indexes = {
        @Index(name = "idx_batch_id", columnList = "batchId"),
        @Index(name = "idx_result", columnList = "resultStatus")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_batch_furniture", columnNames = {"batchId", "furnitureId"}),
        @UniqueConstraint(name = "uk_batch_station", columnNames = {"batchId", "targetStationCode"})
})
public class RelocationItem {

    /** 条目结果状态 */
    public static final String RESULT_PENDING = "PENDING";
    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAILED = "FAILED";
    /** 幂等成功：重试时发现已由别的操作完成到同一目标 */
    public static final String RESULT_IDEMPOTENT = "IDEMPOTENT";

    /** 逐项校验状态 */
    public static final String VALIDATE_UNCHECKED = "UNCHECKED";
    public static final String VALIDATE_PASS = "PASS";
    public static final String VALIDATE_CONFLICT = "CONFLICT";

    /** 校验冲突码 */
    public static final String CODE_FURNITURE_DELETED = "FURNITURE_DELETED";
    public static final String CODE_BINDING_CHANGED = "BINDING_CHANGED";
    public static final String CODE_STATION_OCCUPIED = "STATION_OCCUPIED";
    public static final String CODE_FLOOR_MISMATCH = "FLOOR_MISMATCH";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long batchId;

    @Column(nullable = false)
    private Long furnitureId;

    @Column(length = 50)
    private String furnitureCode;

    @Column(length = 20)
    private String furnitureType;

    @Column(nullable = false, length = 50)
    private String targetStationCode;

    @Column(length = 100)
    private String targetEmployeeName;

    @Column(length = 100)
    private String targetDepartment;

    // ===== 创建批次时的原绑定快照（确认、执行、重试均以此为基准追溯） =====

    private Integer snapFloorNum;

    @Column(length = 50)
    private String snapStationCode;

    @Column(length = 100)
    private String snapEmployeeName;

    @Column(length = 100)
    private String snapDepartment;

    private Integer snapBindStatus;

    // ===== 校验与执行结果 =====

    @Column(nullable = false, length = 20)
    private String resultStatus = RESULT_PENDING;

    @Column(nullable = false, length = 20)
    private String validateStatus = VALIDATE_UNCHECKED;

    @Column(length = 500)
    private String validateCodes;

    @Column(length = 500)
    private String validateMessage;

    private Long bindRecordId;

    private LocalDateTime executedTime;

    @Version
    private Integer version;

    @Column(nullable = false)
    @CreationTimestamp
    private LocalDateTime createTime;

    @UpdateTimestamp
    private LocalDateTime updateTime;
}
