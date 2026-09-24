-- H2(MySQL 兼容模式) 搬迁台账幂等约束的等价实现：
-- H2 2.2 表达式索引对 CASE 表达式支持有限，这里用生成列 + 唯一索引等价保证
-- “仅 relocation_batch_no 非空时，(批次号,家具ID) 唯一”。
-- 生产 MySQL 环境使用 backend/sql/init.sql 中的 CASE 函数唯一索引。
ALTER TABLE bind_record ADD COLUMN IF NOT EXISTS relocation_uk_key VARCHAR(120)
    AS (CASE WHEN relocation_batch_no IS NOT NULL
        THEN CONCAT(relocation_batch_no, '#', CAST(furniture_id AS VARCHAR)) END);
CREATE UNIQUE INDEX IF NOT EXISTS uk_record_relocation ON bind_record (relocation_uk_key);
