-- =============================================================
-- MiaoWang 会员（VIP）支付 v3 增量脚本
-- 适用：已经执行过 vip-ddl.sql 的环境（miaowang_vip_order 表已存在）
-- 内容：① 订单表增加「幂等键 / 渠道交易号 / 退款 / 关单 / 回调审计」列
--       ② 建三个索引（两个唯一索引是幂等的最后一道防线）
-- 背景：原实现是「下单即生效」（模拟支付）。改成标准支付流程后：
--       下单只落「待支付」→ 渠道回调把订单 CAS 改成「已支付」→ 同一事务里发会员；
--       退款则把订单改成「已退款」并按快照时长回收会员时长。
-- 说明：ALTER 重复执行会报 Duplicate column name（MySQL 8 不支持 ADD COLUMN IF NOT EXISTS），
--       索引重复执行会报 Duplicate key name，都属正常，忽略即可。
-- =============================================================

SET NAMES utf8mb4;

-- --------------------------------------------
-- ① 订单表加列
-- --------------------------------------------
ALTER TABLE `miaowang_vip_order`
    ADD COLUMN `duration_days`    INT          NULL COMMENT '订阅时长快照（天），退款回收时长按它算' AFTER `amount`,
    ADD COLUMN `request_id`       VARCHAR(64)  NULL COMMENT '下单幂等键（前端生成，同一用户同一键只出一单）' AFTER `duration_days`,
    ADD COLUMN `channel_trade_no` VARCHAR(64)  NULL COMMENT '支付渠道交易号（对账用）' AFTER `pay_time`,
    ADD COLUMN `refund_no`        VARCHAR(64)  NULL COMMENT '退款单号' AFTER `channel_trade_no`,
    ADD COLUMN `refund_reason`    VARCHAR(255) NULL COMMENT '退款原因' AFTER `refund_no`,
    ADD COLUMN `refund_time`      DATETIME     NULL COMMENT '退款完成时间' AFTER `refund_reason`,
    ADD COLUMN `close_time`       DATETIME     NULL COMMENT '关单时间（待支付超时）' AFTER `refund_time`,
    ADD COLUMN `notify_time`      DATETIME     NULL COMMENT '最近一次收到回调的时间' AFTER `close_time`,
    ADD COLUMN `notify_count`     INT NOT NULL DEFAULT 0 COMMENT '累计回调次数（审计渠道重试了几次）' AFTER `notify_time`;

-- start_time / expire_time 原来是 NOT NULL —— 因为 v1 的流程是「下单即生效」，一定有值。
-- 改成标准支付流程后，下单时还没付钱、也就还没有权益周期，必须允许为空，
-- 由支付回调成功后在发会员的同一个事务里回写。（不改成 NULL 的话，下单 INSERT 会直接失败）
ALTER TABLE `miaowang_vip_order`
    MODIFY COLUMN `start_time`  DATETIME NULL COMMENT '本次权益开始时间（支付成功后回写）',
    MODIFY COLUMN `expire_time` DATETIME NULL COMMENT '本次权益到期时间（支付成功后回写）';

-- --------------------------------------------
-- ② 索引
--    业务层的 CAS 更新负责「同一条回调只有一次生效」，
--    这两个唯一索引负责「同一笔支付不可能挂到两个订单上」——双保险。
--    MySQL 唯一索引允许多个 NULL，所以历史数据（request_id / channel_trade_no 为 NULL）不受影响。
-- --------------------------------------------
CREATE UNIQUE INDEX `uk_vip_order_uid_request`   ON `miaowang_vip_order` (`uid`, `request_id`);
CREATE UNIQUE INDEX `uk_vip_order_channel_trade` ON `miaowang_vip_order` (`pay_type`, `channel_trade_no`);
-- 支撑「查某人某档位是否还有待支付单」与超时关单扫描
CREATE INDEX        `idx_vip_order_uid_status`   ON `miaowang_vip_order` (`uid`, `pay_status`, `create_time`);

-- --------------------------------------------
-- ③ 验证：列与索引是否落上
-- --------------------------------------------
SHOW COLUMNS FROM `miaowang_vip_order`;
SHOW INDEX FROM `miaowang_vip_order`;