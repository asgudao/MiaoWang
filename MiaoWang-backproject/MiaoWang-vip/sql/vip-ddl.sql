-- =============================================================
-- MiaoWang 会员（VIP）建表脚本
-- 数据库：MySQL 8，字符集 utf8mb4，引擎 InnoDB
-- 说明：VIP 模块只负责「权益判定 + 额度计数」，
--       宠物体量、RAG 次数、聊天轮次、健康档案的实际执行在各自模块，
--       由它们调用本模块的接口来校验/扣额度（见 MiaoWang-Vip/README.md）。
-- 版本：v2（额度支持 DAY/MONTH 周期；新增健康档案与提醒权益）
-- 注意：本脚本会 DROP 并重建 VIP 相关表（初始化用）；
--       已经建过表的环境请执行增量脚本 vip-plan-v2.sql，不要重跑本脚本。
-- =============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- --------------------------------------------
-- 1. 会员档位配置表（权益数据驱动，改价格/次数只改数据不改代码）
-- --------------------------------------------
DROP TABLE IF EXISTS `miaowang_vip_plan`;
CREATE TABLE `miaowang_vip_plan` (
    `id`                   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `plan_code`            VARCHAR(32)   NOT NULL                COMMENT '档位编码：FREE/PET_49/AI_149/AI_249/AI_349/ALL_400',
    `plan_name`            VARCHAR(64)   NOT NULL                COMMENT '档位名称',
    `price`                DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '月费（元）',
    `duration_days`        INT           NOT NULL DEFAULT 30     COMMENT '单次开通/续费的有效天数',
    `pet_limit_per_type`   INT           NOT NULL DEFAULT 2      COMMENT '可养宠物总数量上限（不区分物种；-1=不限。列名是历史遗留，语义已是总数）',
    `rag_limit`            INT           NOT NULL DEFAULT 0      COMMENT 'RAG 次数上限，-1=不限次数',
    `rag_period`           VARCHAR(16)   NOT NULL DEFAULT 'DAY'  COMMENT 'RAG 额度周期：DAY=每日 MONTH=每月',
    `chat_limit`           INT           NOT NULL DEFAULT 0      COMMENT '宠物聊天轮次上限，-1=不限次数',
    `chat_period`          VARCHAR(16)   NOT NULL DEFAULT 'DAY'  COMMENT '聊天额度周期：DAY=每日 MONTH=每月',
    `health_record_limit`  INT           NOT NULL DEFAULT 20     COMMENT '每只宠物每类健康记录条数上限，-1=不限',
    `health_export`        TINYINT       NOT NULL DEFAULT 0      COMMENT '是否可导出健康报告：0=否 1=是',
    `health_ai_analysis`   TINYINT       NOT NULL DEFAULT 0      COMMENT '是否可用 AI 解读症状：0=否 1=是',
    `reminder_limit`       INT           NOT NULL DEFAULT 3      COMMENT '提醒条数上限，-1=不限',
    `purchasable`          TINYINT       NOT NULL DEFAULT 1      COMMENT '是否可购买：0=否（免费档/内部档）1=是',
    `status`               TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：0=停用 1=启用',
    `sort`                 INT           NOT NULL DEFAULT 0      COMMENT '排序值，越小越靠前',
    `create_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag`             TINYINT       NOT NULL DEFAULT 0      COMMENT '删除标识：0=未删除 1=已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plan_code` (`plan_code`),
    KEY `idx_status_sort` (`status`, `sort`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '会员档位配置表';

-- --------------------------------------------
-- 2. 用户会员表（一个用户可以同时持有多个档位，权益按维度取最大值合并）
--    uk_uid_plan 保证同档位只会有一行：续费 = 延长 expire_time，不会产生多行
-- --------------------------------------------
DROP TABLE IF EXISTS `miaowang_user_vip`;
CREATE TABLE `miaowang_user_vip` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `uid`         BIGINT      NOT NULL                COMMENT '用户ID',
    `username`    VARCHAR(64) NULL DEFAULT NULL       COMMENT '用户名（冗余，便于排查）',
    `plan_code`   VARCHAR(32) NOT NULL                COMMENT '档位编码，对应 miaowang_vip_plan.plan_code',
    `start_time`  DATETIME    NOT NULL                COMMENT '首次开通时间（续费只延长 expire_time，不改这里；每次开通明细见订阅订单表）',
    `expire_time` DATETIME    NOT NULL                COMMENT '到期时间（判断是否生效只看这个字段）',
    `status`      TINYINT     NOT NULL DEFAULT 1      COMMENT '状态：1=生效 0=失效（退款/封禁时置 0）',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag`    TINYINT     NOT NULL DEFAULT 0      COMMENT '删除标识：0=未删除 1=已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_uid_plan` (`uid`, `plan_code`),
    KEY `idx_uid_status_expire` (`uid`, `status`, `expire_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户会员表';

-- --------------------------------------------
-- 3. 订阅订单表（支付为 mock，真实支付接入后 pay_type/pay_status 语义不变）
-- --------------------------------------------
DROP TABLE IF EXISTS `miaowang_vip_order`;
CREATE TABLE `miaowang_vip_order` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`    VARCHAR(64)   NOT NULL                COMMENT '订单号（对外唯一标识）',
    `uid`         BIGINT        NOT NULL                COMMENT '用户ID',
    `username`    VARCHAR(64)   NULL DEFAULT NULL       COMMENT '用户名（冗余，便于排查）',
    `plan_code`   VARCHAR(32)   NOT NULL                COMMENT '档位编码',
    `plan_name`   VARCHAR(64)   NOT NULL                COMMENT '档位名称快照（档位改名后历史订单不受影响）',
    `amount`      DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '支付金额（元），下单时快照',
    `pay_type`    VARCHAR(32)   NOT NULL DEFAULT 'MOCK' COMMENT '支付方式：MOCK=模拟支付 WECHAT=微信 ALIPAY=支付宝',
    `pay_status`  TINYINT       NOT NULL DEFAULT 0      COMMENT '支付状态：0=待支付 1=已支付 2=已关闭 3=已退款',
    `pay_time`    DATETIME      NULL DEFAULT NULL       COMMENT '支付完成时间',
    `start_time`  DATETIME      NOT NULL                COMMENT '本次权益开始时间',
    `expire_time` DATETIME      NOT NULL                COMMENT '本次权益到期时间',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag`    TINYINT       NOT NULL DEFAULT 0      COMMENT '删除标识：0=未删除 1=已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_uid_create` (`uid`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '会员订阅订单表';

-- --------------------------------------------
-- 4. 3D 宠物模型定制订单表（人工定制服务：提交需求 → 制作中 → 完成）
--    完成后 model_url 仅下单用户本人可访问（接口层校验 uid 归属）
-- --------------------------------------------
DROP TABLE IF EXISTS `miaowang_vip_3d_order`;
CREATE TABLE `miaowang_vip_3d_order` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`          VARCHAR(64)  NOT NULL                COMMENT '定制单号（对外唯一标识）',
    `uid`               BIGINT       NOT NULL                COMMENT '下单用户ID（模型归属人）',
    `username`          VARCHAR(64)  NULL DEFAULT NULL       COMMENT '用户名（冗余，便于排查）',
    `pid`               BIGINT       NULL DEFAULT NULL       COMMENT '宠物ID（对应 miaowang_pet.pid，可为空=用户暂未建宠物档案）',
    `pet_name`          VARCHAR(64)  NOT NULL                COMMENT '宠物名（定制对象，冗余存储）',
    `requirement`       VARCHAR(1024) NULL DEFAULT NULL      COMMENT '定制需求描述（毛色、姿态、特征等）',
    `reference_images`  VARCHAR(2048) NULL DEFAULT NULL      COMMENT '参考图 URL，逗号分隔',
    `status`            TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0=待处理 1=制作中 2=已完成 3=已取消',
    `model_url`         VARCHAR(512) NULL DEFAULT NULL       COMMENT '定制模型地址（.glb），仅本人可访问',
    `admin_remark`      VARCHAR(512) NULL DEFAULT NULL       COMMENT '处理备注（制作方填写，用户可见）',
    `finish_time`       DATETIME     NULL DEFAULT NULL       COMMENT '完成时间',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag`          TINYINT      NOT NULL DEFAULT 0      COMMENT '删除标识：0=未删除 1=已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_uid_status` (`uid`, `status`),
    KEY `idx_status_create` (`status`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '3D 宠物模型定制订单表';

-- --------------------------------------------
-- 5. 档位种子数据（v2 权益口径）
--    设计原则：
--      1) 高档位「叠加包含」低档位，各维度单调不降
--      2) 免费额度按月给试用（防止白嫖），付费额度按天给（体验爽）
--      3) 聊天按天（留存钩子），RAG 免费按月（调用成本高）
--      4) 健康档案能建几只宠物 = 宠物数量上限，不另设概念
--    -1 表示不限
-- --------------------------------------------
INSERT INTO `miaowang_vip_plan`
(`plan_code`, `plan_name`, `price`, `duration_days`, `pet_limit_per_type`,
 `rag_limit`, `rag_period`, `chat_limit`, `chat_period`,
 `health_record_limit`, `health_export`, `health_ai_analysis`, `reminder_limit`,
 `purchasable`, `status`, `sort`)
VALUES
('FREE',    '免费用户',       0.00,  0,  2, 5,  'MONTH', 5,   'DAY', 20,  0, 0, 3,  0, 1, 0),
('PET_49',  '宠物位扩容月卡', 4.90,  30, 5, 5,  'MONTH', 5,   'DAY', 20,  0, 0, 3,  1, 1, 1),
('AI_149',  'AI 基础月卡',   14.90, 30, 5, 10, 'DAY',   20,  'DAY', 100, 0, 0, -1, 1, 1, 2),
('AI_249',  'AI 进阶月卡',   24.90, 30, 5, 50, 'DAY',   50,  'DAY', -1,  1, 0, -1, 1, 1, 3),
('AI_349',  'AI 畅享月卡',   34.90, 30, 5, -1, 'DAY',   100, 'DAY', -1,  1, 1, -1, 1, 1, 4),
('ALL_400', '全能月卡',      40.00, 30, 5, -1, 'DAY',   -1,  'DAY', -1,  1, 1, -1, 1, 1, 5);

SET FOREIGN_KEY_CHECKS = 1;