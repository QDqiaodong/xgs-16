package com.workspace.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 未结束搬迁批次对家具的占用锁。
 * 唯一索引 uk_furniture 保证：同一家具不能同时出现在两个未结束批次中。
 * 批次完成或撤销时释放；部分失败时保留，失败项仍占用。
 */
@Data
@Entity
@Table(name = "relocation_furniture_lock", indexes = {
        @Index(name = "idx_batch_id", columnList = "batchId")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_furniture", columnNames = "furnitureId")
})
public class RelocationFurnitureLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long batchId;

    @Column(nullable = false)
    private Long furnitureId;

    @Column(nullable = false)
    @CreationTimestamp
    private LocalDateTime createTime;
}
