package com.workspace.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 在途家具占用锁：同一家具不能同时出现在两个未结束批次中。
 * 家具ID唯一约束由数据库强保证，防止两个管理员并发建批次时重复占用。
 */
@Data
@Entity
@Table(name = "relocation_active_furniture", indexes = {
        @Index(name = "idx_raf_batch", columnList = "batchId")
})
public class RelocationActiveFurniture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long furnitureId;

    @Column(nullable = false)
    private Long batchId;

    @Column(nullable = false)
    @CreationTimestamp
    private LocalDateTime createTime;
}
