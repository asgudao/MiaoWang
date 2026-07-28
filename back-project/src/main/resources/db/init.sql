-- ============================================
-- 萌宠App (MiaoWang) 数据库初始化脚本
-- MySQL 8.0+
-- ============================================

CREATE DATABASE IF NOT EXISTS petapp DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE petapp;

-- 用户表
CREATE TABLE IF NOT EXISTS t_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    phone VARCHAR(20) NOT NULL COMMENT '手机号',
    nickname VARCHAR(50) COMMENT '昵称',
    avatar VARCHAR(255) COMMENT '头像URL',
    member_type TINYINT DEFAULT 0 COMMENT '0=普通 1=月卡 2=季卡 3=年卡',
    member_expire DATETIME COMMENT '会员到期时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_phone (phone)
) ENGINE=InnoDB COMMENT='用户表';

-- 宠物表
CREATE TABLE IF NOT EXISTS t_pet (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '所属用户ID',
    name VARCHAR(50) NOT NULL COMMENT '宠物名',
    breed_id BIGINT COMMENT '品种ID',
    species TINYINT NOT NULL COMMENT '1=猫 2=狗',
    gender TINYINT COMMENT '1=公 2=母',
    age INT COMMENT '月龄',
    weight DECIMAL(5,2) COMMENT '体重(kg)',
    avatar VARCHAR(255) COMMENT '照片URL',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    KEY idx_user_id (user_id)
) ENGINE=InnoDB COMMENT='宠物表';

-- 品种表
CREATE TABLE IF NOT EXISTS t_breed (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL COMMENT '品种名',
    species TINYINT NOT NULL COMMENT '1=猫 2=狗',
    care_info TEXT COMMENT '护理周期配置JSON',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='品种表';

-- 初始化品种数据
INSERT INTO t_breed (name, species, care_info) VALUES
('英短', 1, '{"vaccine":365,"deworm":90,"nail":14,"bath":30,"checkup":180}'),
('美短', 1, '{"vaccine":365,"deworm":90,"nail":14,"bath":30,"checkup":180}'),
('布偶', 1, '{"vaccine":365,"deworm":90,"nail":14,"bath":20,"checkup":180}'),
('暹罗', 1, '{"vaccine":365,"deworm":90,"nail":14,"bath":30,"checkup":180}'),
('橘猫', 1, '{"vaccine":365,"deworm":90,"nail":14,"bath":30,"checkup":180}'),
('柯基', 2, '{"vaccine":365,"deworm":90,"nail":21,"bath":14,"checkup":180}'),
('金毛', 2, '{"vaccine":365,"deworm":90,"nail":21,"bath":14,"checkup":180}'),
('泰迪', 2, '{"vaccine":365,"deworm":90,"nail":14,"bath":14,"checkup":180}'),
('哈士奇', 2, '{"vaccine":365,"deworm":90,"nail":21,"bath":14,"checkup":180}'),
('法斗', 2, '{"vaccine":365,"deworm":90,"nail":14,"bath":20,"checkup":180}');

-- 知识分类表
CREATE TABLE IF NOT EXISTS t_knowledge_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL COMMENT '分类名',
    species TINYINT DEFAULT NULL COMMENT '1=猫 2=狗',
    sort_order INT DEFAULT 0 COMMENT '排序',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='知识分类表';

INSERT INTO t_knowledge_category (name, species, sort_order) VALUES
('叫声', 1, 1), ('肢体动作', 1, 2), ('病症', 1, 3), ('其他', 1, 4),
('叫声', 2, 1), ('肢体动作', 2, 2), ('病症', 2, 3), ('其他', 2, 4);

-- 知识表
CREATE TABLE IF NOT EXISTS t_knowledge (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL COMMENT '标题',
    category_id BIGINT COMMENT '分类ID',
    cover VARCHAR(255) COMMENT '封面图',
    content TEXT COMMENT '正文(Markdown)',
    view_count INT DEFAULT 0 COMMENT '阅读量',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    KEY idx_category_id (category_id)
) ENGINE=InnoDB COMMENT='知识表';

-- 订阅档位表
CREATE TABLE IF NOT EXISTS t_subscription_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL COMMENT '档位名称',
    price INT NOT NULL COMMENT '价格(分)',
    duration_days INT NOT NULL COMMENT '有效天数',
    pet_limit INT NOT NULL COMMENT '宠物数量上限',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='订阅档位表';

INSERT INTO t_subscription_plan (name, price, duration_days, pet_limit) VALUES
('月卡', 1999, 30, 5),
('季卡', 4999, 90, 5),
('年卡', 14999, 365, 10);

-- 订单表
CREATE TABLE IF NOT EXISTS t_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(50) NOT NULL COMMENT '订单号',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    plan_id BIGINT NOT NULL COMMENT '订阅档位ID',
    amount INT NOT NULL COMMENT '金额(分)',
    status TINYINT DEFAULT 0 COMMENT '0=待支付 1=已支付 2=已取消',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    paid_at DATETIME COMMENT '支付时间',
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB COMMENT='订单表';

-- 提醒计划表
CREATE TABLE IF NOT EXISTS t_reminder_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pet_id BIGINT NOT NULL COMMENT '宠物ID',
    rule_type VARCHAR(30) NOT NULL COMMENT '提醒类型: vaccine/deworm/nail/bath/checkup',
    next_date DATE NOT NULL COMMENT '下次提醒日期',
    cycle_days INT NOT NULL COMMENT '间隔天数',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_pet_id (pet_id)
) ENGINE=InnoDB COMMENT='提醒计划表';

