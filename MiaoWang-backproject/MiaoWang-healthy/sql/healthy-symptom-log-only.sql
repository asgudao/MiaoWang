-- =============================================================
-- MiaoWang healthy：补齐缺失的「宠物症状日记」表（只建这一张，不动其他五张）
-- 背景：线上库已存在 preventive / medical / medication / allergy / weight 五张表，
--       唯独缺 miaowang_pet_symptom_log —— 它是 AI 症状解读（POST /health/{pid}/ai-analysis）
--       与症状记录接口的依赖表，缺了会直接报错。
-- ⚠️ 不要为了补这张表去重跑整份 healthy-ddl.sql：那份脚本里六张表都是 DROP TABLE IF EXISTS，
--    会把已有的健康记录全清掉。
-- 本脚本用 CREATE TABLE IF NOT EXISTS，可重复执行、不删数据。
-- =============================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `miaowang_pet_symptom_log` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `pid`          BIGINT        NOT NULL                COMMENT '宠物ID',
    `uid`          BIGINT        NOT NULL                COMMENT '归属用户ID',
    `username`     VARCHAR(64)   NULL DEFAULT NULL       COMMENT '用户名（冗余，便于排查）',
    `symptom_id`   BIGINT        NULL DEFAULT NULL       COMMENT '知识库健康自查条目ID（knowledge_fragment.id）',
    `symptom_name` VARCHAR(255)  NULL DEFAULT NULL       COMMENT '症状名快照，便于列表展示与历史留存',
    `description`  VARCHAR(1024) NULL DEFAULT NULL       COMMENT '补充描述：什么时候开始、频率、精神状态等',
    `severity`     TINYINT       NOT NULL DEFAULT 1      COMMENT '严重程度：1=轻度 2=中度 3=重度',
    `occurred_at`  DATETIME      NOT NULL                COMMENT '症状出现时间',
    `images`       VARCHAR(2048) NULL DEFAULT NULL       COMMENT '症状照片 URL，逗号分隔',
    `remark`       VARCHAR(512)  NULL DEFAULT NULL       COMMENT '备注',
    `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag`     TINYINT       NOT NULL DEFAULT 0      COMMENT '删除标识：0=未删除 1=已删除',
    PRIMARY KEY (`id`),
    KEY `idx_pid_time` (`pid`, `occurred_at`),
    KEY `idx_symptom` (`symptom_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '宠物症状日记表';

-- 验证：应能看到六张 miaowang_pet_*_record / *_log 表
SHOW TABLES LIKE 'miaowang_pet_%';