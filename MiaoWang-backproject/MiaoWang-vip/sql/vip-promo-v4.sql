-- =============================================================
-- MiaoWang 会员（VIP）促销 v4 增量脚本：兑换码 / 优惠码
-- 适用：已执行过 vip-ddl.sql（+ 可选 vip-plan-v2.sql / vip-pay-v3.sql）的环境
-- 内容：① 促销码表（一张表覆盖「送会员时长」与「下单立减」两种玩法）
--       ② 核销记录表（唯一索引 = 每人限用一次的最终保证）
--       ③ 订单表增加 优惠金额 / 应付金额 / 促销码 三列
--       ④ 演示用促销码两枚（可重复执行）
-- 说明：CREATE TABLE 用 IF NOT EXISTS，可重复执行；ALTER 重复执行会报 Duplicate column name，忽略即可
-- =============================================================

SET NAMES utf8mb4;

-- --------------------------------------------
-- ① 促销码
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS `miaowang_vip_promo_code` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`            VARCHAR(64)  NOT NULL COMMENT '促销码（对外输入用）',
    `name`            VARCHAR(64)  NOT NULL COMMENT '活动名（后台识别用）',
    `promo_type`      VARCHAR(16)  NOT NULL COMMENT 'PLAN=送会员时长 DISCOUNT=下单立减',
    `plan_code`       VARCHAR(32)  DEFAULT NULL COMMENT '档位编码：PLAN 必填；DISCOUNT 为空表示不限档位',
    `duration_days`   INT          DEFAULT NULL COMMENT '赠送天数（PLAN 专用）',
    `discount_amount` DECIMAL(10,2) DEFAULT NULL COMMENT '立减金额（DISCOUNT 专用，元）',
    `min_amount`      DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '使用门槛（DISCOUNT 专用，0=无门槛）',
    `max_use_count`   INT          NOT NULL DEFAULT 1 COMMENT '总名额，-1=不限',
    `used_count`      INT          NOT NULL DEFAULT 0 COMMENT '已用名额',
    `start_time`      DATETIME     DEFAULT NULL COMMENT '生效时间（空=立即生效）',
    `end_time`        DATETIME     DEFAULT NULL COMMENT '失效时间（空=永久有效）',
    `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '0=停用 1=启用',
    `remark`          VARCHAR(255) DEFAULT NULL COMMENT '后台备注',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag`        TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标记 0=未删除 1=已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_promo_code` (`code`),
    KEY `idx_promo_status` (`status`, `del_flag`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '会员促销码';

-- --------------------------------------------
-- ② 核销记录
--    uk_promo_uid 是「每人限用一次」的最终保证：业务层"查一下有没有用过"在并发下会漏
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS `miaowang_vip_promo_redeem` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `promo_code_id` BIGINT      NOT NULL COMMENT '促销码ID',
    `code`          VARCHAR(64) NOT NULL COMMENT '码快照',
    `uid`           BIGINT      NOT NULL COMMENT '使用者',
    `username`      VARCHAR(64) DEFAULT NULL COMMENT '用户名（冗余）',
    `order_no`      VARCHAR(64) DEFAULT NULL COMMENT '关联订单号',
    `redeem_time`   DATETIME    NOT NULL COMMENT '核销时间',
    `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag`      TINYINT     NOT NULL DEFAULT 0 COMMENT '删除标记 0=未删除 1=已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_promo_uid` (`promo_code_id`, `uid`),
    KEY `idx_redeem_uid` (`uid`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '会员促销码核销记录';

-- --------------------------------------------
-- ③ 订单表：优惠金额 / 应付金额 / 促销码
-- --------------------------------------------
ALTER TABLE `miaowang_vip_order`
    ADD COLUMN `discount_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '优惠金额（原价-优惠=应付）' AFTER `amount`,
    ADD COLUMN `payable_amount`  DECIMAL(10,2) NULL COMMENT '应付金额（支付回调按它校验；0 元=促销单）' AFTER `discount_amount`,
    ADD COLUMN `promo_code_id`   BIGINT        NULL COMMENT '使用的促销码ID' AFTER `payable_amount`,
    ADD COLUMN `promo_code`      VARCHAR(64)   NULL COMMENT '促销码快照' AFTER `promo_code_id`;

-- 老订单没有优惠，应付即原价；回填后「回调金额校验」的口径就统一了
UPDATE `miaowang_vip_order` SET `payable_amount` = `amount` WHERE `payable_amount` IS NULL;

-- --------------------------------------------
-- ④ 演示促销码（可重复执行）
--    WELCOME30：新人送 30 天 AI_149（走 POST /vip/promo/redeem）
--    SAVE5    ：下单立减 5 元、满 20 元可用、不限档位（下单时传 promoCode）
-- --------------------------------------------
INSERT INTO `miaowang_vip_promo_code`
    (`code`, `name`, `promo_type`, `plan_code`, `duration_days`, `discount_amount`, `min_amount`,
     `max_use_count`, `used_count`, `status`, `remark`)
VALUES
    ('WELCOME30', '新人礼：送 30 天 AI_149', 'PLAN', 'AI_149', 30, NULL, 0.00, 1000, 0, 1, '演示数据，可停用'),
    ('SAVE5', '下单立减 5 元', 'DISCOUNT', NULL, NULL, 5.00, 20.00, -1, 0, 1, '演示数据，可停用')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `remark` = VALUES(`remark`);

-- --------------------------------------------
-- ⑤ 验证
-- --------------------------------------------
SHOW COLUMNS FROM `miaowang_vip_promo_code`;
SHOW COLUMNS FROM `miaowang_vip_order`;
SELECT `code`, `promo_type`, `plan_code`, `duration_days`, `discount_amount`, `min_amount`, `max_use_count`, `used_count`, `status`
FROM `miaowang_vip_promo_code`;