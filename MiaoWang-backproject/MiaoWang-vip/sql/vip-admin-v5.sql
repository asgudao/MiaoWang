-- =============================================================
-- MiaoWang 会员（VIP）后台管理 v5 增量脚本：管理员体系 + 操作审计
-- 内容：① 后台管理员表（独立于 miaowang_user，密码 BCrypt 哈希）
--       ② 后台操作日志表（后台写操作必须留痕）
--       ③ 种子超管账号 admin / admin123456（请务必改密码）
-- 说明：CREATE TABLE 用 IF NOT EXISTS，可重复执行；种子账号重复执行不会覆盖已改的密码
-- =============================================================

SET NAMES utf8mb4;

-- --------------------------------------------
-- ① 后台管理员
--    为什么独立一张表：后台账号与 C 端账号生命周期/权限/审计要求都不同，
--    混在一张表意味着"用户表的任何漏洞都可能变成后台入口"
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS `miaowang_vip_admin` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`        VARCHAR(64)  NOT NULL COMMENT '登录名',
    `password_hash`   VARCHAR(100) NOT NULL COMMENT '密码哈希（BCrypt，绝不存明文）',
    `real_name`       VARCHAR(64)  DEFAULT NULL COMMENT '姓名',
    `role`            VARCHAR(16)  NOT NULL DEFAULT 'OPERATOR' COMMENT '角色：SUPER=超管 OPERATOR=运营',
    `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用',
    `last_login_time` DATETIME     DEFAULT NULL COMMENT '最近登录时间',
    `last_login_ip`   VARCHAR(64)  DEFAULT NULL COMMENT '最近登录IP',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag`        TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标记 0=未删除 1=已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_admin_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '会员后台管理员';

-- --------------------------------------------
-- ② 操作日志（谁在什么时候把哪一单改成了什么）
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS `miaowang_vip_admin_op_log` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `admin_id`       BIGINT       DEFAULT NULL COMMENT '操作人ID',
    `admin_username` VARCHAR(64)  DEFAULT NULL COMMENT '操作人登录名（冗余）',
    `action`         VARCHAR(32)  NOT NULL COMMENT '动作：ADMIN_LOGIN / 3D_COMPLETE / ORDER_REFUND / PROMO_SAVE ...',
    `target_type`    VARCHAR(32)  DEFAULT NULL COMMENT '目标类型：3D_ORDER / VIP_ORDER / PROMO_CODE / ADMIN',
    `target_id`      VARCHAR(64)  DEFAULT NULL COMMENT '目标标识（订单号、码等）',
    `detail`         VARCHAR(500) DEFAULT NULL COMMENT '详情摘要',
    `ip`             VARCHAR(64)  DEFAULT NULL COMMENT '来源IP',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag`       TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_op_log_admin` (`admin_id`, `create_time`),
    KEY `idx_op_log_target` (`target_type`, `target_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '会员后台操作日志';

-- --------------------------------------------
-- ③ 种子超管：admin / admin123456
--    ⚠️ 这是公开在仓库里的初始密码，登录后请立刻在后台改密码（PUT /vip/admin/password）
--    ON DUPLICATE KEY UPDATE 只刷姓名，不覆盖你改过的密码
-- --------------------------------------------
INSERT INTO `miaowang_vip_admin` (`username`, `password_hash`, `real_name`, `role`, `status`, `del_flag`)
VALUES ('admin', '$2a$10$OZlAvGNRzaG3lsi4Ef12.eyJomB5qAO5RZucuK4vXotve./.ZsALq', '超级管理员', 'SUPER', 1, 0)
ON DUPLICATE KEY UPDATE `real_name` = VALUES(`real_name`);

-- --------------------------------------------
-- ④ 验证
-- --------------------------------------------
SHOW COLUMNS FROM `miaowang_vip_admin`;
SELECT `id`, `username`, `real_name`, `role`, `status`, `last_login_time` FROM `miaowang_vip_admin`;