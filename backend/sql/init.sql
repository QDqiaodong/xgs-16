CREATE DATABASE IF NOT EXISTS workspace_manage DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE workspace_manage;

CREATE TABLE IF NOT EXISTS office_furniture (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    furniture_code VARCHAR(50) NOT NULL UNIQUE COMMENT '桌椅编号',
    furniture_type VARCHAR(20) NOT NULL COMMENT '类型:桌/椅/套装',
    style_name VARCHAR(200) COMMENT '款式名称',
    brand VARCHAR(100) COMMENT '品牌',
    price DECIMAL(10,2) COMMENT '价格',
    size_spec VARCHAR(50) COMMENT '尺寸规格',
    remark VARCHAR(500) COMMENT '备注',
    image_url VARCHAR(500) COMMENT '图片URL',
    spec_template VARCHAR(200) COMMENT '规格模板名称',
    floor_num INT NOT NULL COMMENT '适配楼层',
    area_name VARCHAR(100) COMMENT '区域名称',
    station_code VARCHAR(50) COMMENT '工位编号',
    employee_name VARCHAR(100) COMMENT '使用人',
    department VARCHAR(50) COMMENT '部门',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    bind_status INT NOT NULL DEFAULT 0 COMMENT '0未绑定 1已绑定',
    INDEX idx_furniture_code (furniture_code),
    INDEX idx_station_code (station_code),
    INDEX idx_floor (floor_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='办公桌椅档案';

CREATE TABLE IF NOT EXISTS bind_record (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    furniture_id BIGINT NOT NULL COMMENT '家具ID',
    furniture_code VARCHAR(50) COMMENT '家具编号',
    operate_type VARCHAR(20) COMMENT 'BIND绑定 UNBIND解绑 REBIND重绑定 RELOCATE楼层搬迁',
    old_station_code VARCHAR(50) COMMENT '原工位',
    new_station_code VARCHAR(50) COMMENT '新工位',
    old_employee_name VARCHAR(100) COMMENT '原使用人',
    new_employee_name VARCHAR(100) COMMENT '新使用人',
    old_department VARCHAR(100) COMMENT '原部门',
    new_department VARCHAR(100) COMMENT '新部门',
    operate_reason VARCHAR(500) COMMENT '操作原因',
    operator_name VARCHAR(100) COMMENT '操作人',
    relocation_batch_no VARCHAR(40) COMMENT '楼层搬迁批次号(RELOCATE台账的幂等标识)',
    relocation_uk_key VARCHAR(120) GENERATED ALWAYS AS
        (CASE WHEN relocation_batch_no IS NOT NULL
              THEN CONCAT(relocation_batch_no, '#', furniture_id) END) VIRTUAL
        COMMENT '搬迁台账幂等键(仅批次号非空时生成)',
    record_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_furniture_id (furniture_id),
    INDEX idx_record_time (record_time),
    INDEX idx_relocation_batch (relocation_batch_no),
    UNIQUE KEY uk_record_relocation (relocation_uk_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工位绑定变更台账';

-- 楼层搬迁批次：一次部门搬迁整理为独立批次
CREATE TABLE IF NOT EXISTS relocation_batch (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    batch_no VARCHAR(40) NOT NULL UNIQUE COMMENT '批次编号 RL+日期+序号',
    batch_name VARCHAR(200) NOT NULL COMMENT '批次名称',
    source_floor_num INT NOT NULL COMMENT '迁出楼层',
    target_floor_num INT NOT NULL COMMENT '迁入楼层',
    department VARCHAR(50) COMMENT '搬迁部门',
    remark VARCHAR(500) COMMENT '备注',
    operator_name VARCHAR(100) COMMENT '创建/操作人',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待确认 READY可执行 RUNNING执行中 PARTIAL_FAILED部分失败 COMPLETED已完成 CANCELLED已撤销',
    total_count INT NOT NULL DEFAULT 0 COMMENT '条目总数',
    success_count INT NOT NULL DEFAULT 0 COMMENT '成功条目数',
    fail_count INT NOT NULL DEFAULT 0 COMMENT '失败条目数',
    confirmed_at DATETIME NULL COMMENT '最近一次确认时间',
    executed_at DATETIME NULL COMMENT '首次开始执行时间',
    completed_at DATETIME NULL COMMENT '全部完成时间',
    revoked_at DATETIME NULL COMMENT '撤销时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_rb_status (status),
    INDEX idx_rb_floors (source_floor_num, target_floor_num),
    INDEX idx_rb_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='楼层搬迁批次';

-- 搬迁批次条目：保存创建时的原绑定快照，执行结果逐项落库（刷新/重试后服务端状态一致）
CREATE TABLE IF NOT EXISTS relocation_item (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL COMMENT '所属批次',
    furniture_id BIGINT NOT NULL COMMENT '家具ID',
    furniture_code VARCHAR(50) COMMENT '家具编号(冗余,家具被删后仍可展示)',
    snapshot_floor_num INT COMMENT '快照:原楼层',
    snapshot_station_code VARCHAR(50) COMMENT '快照:原工位',
    snapshot_employee_name VARCHAR(100) COMMENT '快照:原使用人',
    snapshot_department VARCHAR(50) COMMENT '快照:原部门',
    snapshot_bind_status INT COMMENT '快照:绑定状态',
    target_station_code VARCHAR(50) NOT NULL COMMENT '目标工位',
    target_employee_name VARCHAR(100) NOT NULL COMMENT '目标使用人',
    target_department VARCHAR(50) NOT NULL COMMENT '目标部门',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待执行 SUCCESS成功 FAILED失败',
    fail_code VARCHAR(50) COMMENT '最近失败原因码',
    fail_reason VARCHAR(500) COMMENT '最近失败原因描述',
    result_note VARCHAR(500) COMMENT '结果备注(如幂等收口说明)',
    record_id BIGINT COMMENT '对应台账记录ID',
    sort_order INT NOT NULL DEFAULT 0,
    executed_at DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ri_batch_station (batch_id, target_station_code),
    UNIQUE KEY uk_ri_batch_furniture (batch_id, furniture_id),
    INDEX idx_ri_batch (batch_id),
    INDEX idx_ri_furniture (furniture_id),
    INDEX idx_ri_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搬迁批次条目';

-- 在途家具占用锁：同一家具不能同时出现在两个未结束批次中(数据库强约束)
CREATE TABLE IF NOT EXISTS relocation_active_furniture (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    furniture_id BIGINT NOT NULL UNIQUE COMMENT '家具ID(唯一,被未结束批次占用)',
    batch_id BIGINT NOT NULL COMMENT '占用该家具的批次',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_raf_batch (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搬迁在途家具占用锁';

INSERT INTO office_furniture (furniture_code, furniture_type, style_name, brand, price, size_spec, remark, floor_num, area_name, station_code, employee_name, department, bind_status) VALUES
('DESK-01F-A001', '办公桌', 'L型主管桌', '震旦', 2800.00, '1600x800x750', '行政部主管工位', 1, 'A区开放办公', 'G1-A001', '张敏', '行政部', 1),
('CHAIR-01F-A001', '办公椅', '人体工学椅', '联友', 1580.00, '660x680x1150', '高背网布', 1, 'A区开放办公', 'G1-A001', '张敏', '行政部', 1),
('DESK-01F-A002', '办公桌', '标准职员桌', '震旦', 1680.00, '1400x700x750', '职员工位', 1, 'A区开放办公', 'G1-A002', '李晓娟', '行政部', 1),
('CHAIR-01F-A002', '办公椅', '职员网椅', '联友', 680.00, '600x600x1000', '中背网布', 1, 'A区开放办公', 'G1-A002', '李晓娟', '行政部', 1),
('DESK-01F-A003', '办公桌', '标准职员桌', '震旦', 1680.00, '1400x700x750', NULL, 1, 'A区开放办公', NULL, NULL, NULL, 0),
('CHAIR-01F-A003', '办公椅', '职员网椅', '联友', 680.00, '600x600x1000', NULL, 1, 'A区开放办公', NULL, NULL, NULL, 0),
('DESK-01F-B001', '办公桌', '会议桌', '震旦', 8600.00, '3600x1600x750', '6人位', 1, 'B区会议室', 'G1-MT01', NULL, '行政部', 1),
('DESK-02F-A001', '办公桌', 'L型主管桌', '震旦', 2800.00, '1600x800x750', '技术总监工位', 2, 'A区开放办公', 'G2-A001', '王强', '技术部', 1),
('CHAIR-02F-A001', '办公椅', '人体工学椅', '赫曼米勒', 5600.00, '680x700x1200', '进口高背', 2, 'A区开放办公', 'G2-A001', '王强', '技术部', 1),
('DESK-02F-A002', '办公桌', '标准职员桌', '震旦', 1680.00, '1400x700x750', '后端开发', 2, 'A区开放办公', 'G2-A002', '赵磊', '技术部', 1),
('CHAIR-02F-A002', '办公椅', '职员网椅', '联友', 680.00, '600x600x1000', NULL, 2, 'A区开放办公', 'G2-A002', '赵磊', '技术部', 1),
('DESK-02F-A003', '办公桌', '标准职员桌', '震旦', 1680.00, '1400x700x750', '前端开发', 2, 'A区开放办公', 'G2-A003', '陈雨', '技术部', 1),
('CHAIR-02F-A003', '办公椅', '职员网椅', '联友', 680.00, '600x600x1000', NULL, 2, 'A区开放办公', 'G2-A003', '陈雨', '技术部', 1),
('DESK-02F-A004', '办公桌', '标准职员桌', '震旦', 1680.00, '1400x700x750', '运维岗', 2, 'A区开放办公', NULL, NULL, NULL, 0),
('CHAIR-02F-A004', '办公椅', '职员网椅', '联友', 680.00, '600x600x1000', NULL, 2, 'A区开放办公', NULL, NULL, NULL, 0),
('DESK-02F-B001', '办公桌', '独立经理桌', '震旦', 3600.00, '1800x900x750', '独立办公室', 2, 'B区经理室', 'G2-M001', '刘涛', '产品部', 1),
('CHAIR-02F-B001', '办公椅', '真皮大班椅', '震旦', 3200.00, '700x750x1300', '真皮高背', 2, 'B区经理室', 'G2-M001', '刘涛', '产品部', 1),
('DESK-03F-A001', '办公桌', 'L型主管桌', '震旦', 2800.00, '1600x800x750', '财务总监', 3, 'A区财务室', 'G3-A001', '周芳', '财务部', 1),
('CHAIR-03F-A001', '办公椅', '人体工学椅', '联友', 1580.00, '660x680x1150', NULL, 3, 'A区财务室', 'G3-A001', '周芳', '财务部', 1),
('DESK-03F-A002', '办公桌', '标准职员桌', '震旦', 1680.00, '1400x700x750', '会计岗1', 3, 'A区财务室', 'G3-A002', '吴静', '财务部', 1),
('CHAIR-03F-A002', '办公椅', '职员网椅', '联友', 680.00, '600x600x1000', NULL, 3, 'A区财务室', 'G3-A002', '吴静', '财务部', 1),
('DESK-03F-A003', '办公桌', '标准职员桌', '震旦', 1680.00, '1400x700x750', '出纳岗', 3, 'A区财务室', 'G3-A003', '孙莉', '财务部', 1),
('CHAIR-03F-A003', '办公椅', '职员网椅', '联友', 680.00, '600x600x1000', NULL, 3, 'A区财务室', 'G3-A003', '孙莉', '财务部', 1),
('DESK-03F-B001', '办公桌', '培训桌', '震旦', 12000.00, '6000x1800x750', '20人培训', 3, 'B区培训室', 'G3-TR01', NULL, '行政部', 1),
('DESK-05F-A001', '办公桌', '老板桌', '震旦', 18800.00, '2800x1100x760', '总裁办公室', 5, 'A区总裁室', 'G5-EX01', '郑总', '总裁办', 1),
('CHAIR-05F-A001', '办公椅', '真皮总裁椅', '震旦', 9800.00, '780x800x1400', '进口真皮', 5, 'A区总裁室', 'G5-EX01', '郑总', '总裁办', 1),
('DESK-05F-A002', '办公桌', '秘书桌', '震旦', 2400.00, '1500x800x750', '总裁秘书', 5, 'A区总裁室', 'G5-EX02', '林秘书', '总裁办', 1),
('CHAIR-05F-A002', '办公椅', '职员网椅', '联友', 980.00, '620x620x1050', NULL, 5, 'A区总裁室', 'G5-EX02', '林秘书', '总裁办', 1);
