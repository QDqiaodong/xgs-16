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
    operate_type VARCHAR(20) COMMENT 'BIND绑定 UNBIND解绑 REBIND重绑定 RELOCATION搬迁',
    old_station_code VARCHAR(50) COMMENT '原工位',
    new_station_code VARCHAR(50) COMMENT '新工位',
    old_employee_name VARCHAR(100) COMMENT '原使用人',
    new_employee_name VARCHAR(100) COMMENT '新使用人',
    old_department VARCHAR(100) COMMENT '原部门',
    new_department VARCHAR(100) COMMENT '新部门',
    operate_reason VARCHAR(500) COMMENT '操作原因',
    operator_name VARCHAR(100) COMMENT '操作人',
    relocation_batch_id BIGINT COMMENT '搬迁批次ID',
    relocation_batch_no VARCHAR(50) COMMENT '搬迁批次号',
    relocation_item_id BIGINT COMMENT '搬迁批次条目ID',
    record_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_relocation_item (relocation_item_id),
    INDEX idx_furniture_id (furniture_id),
    INDEX idx_relocation_batch (relocation_batch_id),
    INDEX idx_record_time (record_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工位绑定变更台账';

-- 楼层搬迁批次
CREATE TABLE IF NOT EXISTS relocation_batch (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    batch_no VARCHAR(50) NOT NULL UNIQUE COMMENT '批次号',
    batch_name VARCHAR(200) NOT NULL COMMENT '批次名称',
    source_floor INT NOT NULL COMMENT '迁出楼层',
    target_floor INT NOT NULL COMMENT '迁入楼层',
    status VARCHAR(20) NOT NULL COMMENT 'PENDING待确认 READY可执行 RUNNING执行中 PARTIAL_FAILED部分失败 COMPLETED已完成 CANCELLED已撤销',
    target_department VARCHAR(100) COMMENT '默认目标部门',
    operator_name VARCHAR(100) COMMENT '创建人/操作人',
    remark VARCHAR(500) COMMENT '备注',
    total_count INT NOT NULL DEFAULT 0 COMMENT '条目总数',
    success_count INT NOT NULL DEFAULT 0 COMMENT '成功数',
    fail_count INT NOT NULL DEFAULT 0 COMMENT '失败数',
    last_executed_time DATETIME COMMENT '最近执行时间',
    confirmed_time DATETIME COMMENT '确认时间',
    completed_time DATETIME COMMENT '完成时间',
    cancelled_time DATETIME COMMENT '撤销时间',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='楼层搬迁交接批次';

-- 搬迁批次条目（含创建时原绑定快照）
CREATE TABLE IF NOT EXISTS relocation_item (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL COMMENT '批次ID',
    furniture_id BIGINT NOT NULL COMMENT '家具ID',
    furniture_code VARCHAR(50) COMMENT '家具编号(快照)',
    furniture_type VARCHAR(20) COMMENT '家具类型(快照)',
    target_station_code VARCHAR(50) NOT NULL COMMENT '目标工位',
    target_employee_name VARCHAR(100) COMMENT '目标使用人',
    target_department VARCHAR(100) COMMENT '目标部门',
    snap_floor_num INT COMMENT '创建时楼层快照',
    snap_station_code VARCHAR(50) COMMENT '创建时原工位快照',
    snap_employee_name VARCHAR(100) COMMENT '创建时原使用人快照',
    snap_department VARCHAR(100) COMMENT '创建时原部门快照',
    snap_bind_status INT COMMENT '创建时绑定状态快照',
    result_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待处理 SUCCESS成功 FAILED失败 IDEMPOTENT幂等成功',
    validate_status VARCHAR(20) NOT NULL DEFAULT 'UNCHECKED' COMMENT 'UNCHECKED未校验 PASS通过 CONFLICT冲突',
    validate_codes VARCHAR(500) COMMENT '最近校验冲突码,逗号分隔',
    validate_message VARCHAR(500) COMMENT '最近校验/执行结果说明',
    bind_record_id BIGINT COMMENT '对应台账记录ID',
    executed_time DATETIME COMMENT '实际执行时间',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_batch_furniture (batch_id, furniture_id),
    UNIQUE KEY uk_batch_station (batch_id, target_station_code),
    INDEX idx_batch_id (batch_id),
    INDEX idx_result (result_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='楼层搬迁批次条目';

-- 未结束批次的家具占用锁：同一家具不能同时出现在两个未结束批次中
CREATE TABLE IF NOT EXISTS relocation_furniture_lock (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL COMMENT '批次ID',
    furniture_id BIGINT NOT NULL COMMENT '家具ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_furniture (furniture_id),
    INDEX idx_batch_id (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搬迁批次家具占用锁';

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
