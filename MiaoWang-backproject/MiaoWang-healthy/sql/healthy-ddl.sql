-- =============================================================
-- MiaoWang 宠物健康档案建表脚本
-- 数据库：MySQL 8，字符集 utf8mb4，引擎 InnoDB
-- 说明：
--   1) 六张表都以 pid（宠物）+ uid（归属人）为键，所有查询都必须带上 uid，
--      归属校验由接口层负责（通过 Feign 调 pet 服务确认宠物属于当前用户）
--   2) 疫苗与驱虫合并成一张表：两者字段结构完全一致（项目名/时间/下次到期/医院/备注），
--      用 type 区分即可，省掉两套 CRUD 与两套到期提醒逻辑
--   3) uid/username 冗余存储：uid 用于归属隔离，username 便于排查
-- 注意：本脚本为初始化脚本，会 DROP 重建这六张表
-- =============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- --------------------------------------------
-- 1. 疫苗 / 驱虫记录（合并表）
-- --------------------------------------------
DROP TABLE IF EXISTS `miaowang_pet_preventive_record`;
CREATE TABLE `miaowang_pet_preventive_record` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `pid`         BIGINT       NOT NULL                COMMENT '宠物ID（对应 miaowang_pet.pid）',
    `uid`         BIGINT       NOT NULL                COMMENT '归属用户ID',
    `username`    VARCHAR(64)  NULL DEFAULT NULL       COMMENT '用户名（冗余，便于排查）',
    `type`        VARCHAR(16)  NOT NULL                COMMENT '类型：VACCINE=疫苗 DEWORM_IN=体内驱虫 DEWORM_OUT=体外驱虫',
    `item_name`   VARCHAR(128) NOT NULL                COMMENT '项目名，如：猫三联、狂犬疫苗、大宠爱',
    `happened_at` DATETIME     NOT NULL                COMMENT '接种/驱虫时间',
    `next_due_at` DATETIME     NULL DEFAULT NULL       COMMENT '下次到期时间（提醒模块据此生成提醒）',
    `hospital`    VARCHAR(128) NULL DEFAULT NULL       COMMENT '医院/机构',
    `batch_no`    VARCHAR(64)  NULL DEFAULT NULL       COMMENT '疫苗批号',
    `remark`      VARCHAR(512) NULL DEFAULT NULL       COMMENT '备注',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag`    TINYINT      NOT NULL DEFAULT 0      COMMENT '删除标识：0=未删除 1=已删除',
    PRIMARY KEY (`id`),
    KEY `idx_pid_type_time` (`pid`, `type`, `happened_at`),
    KEY `idx_next_due` (`next_due_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '宠物疫苗与驱虫记录表';

-- --------------------------------------------
-- 2. 就诊记录 / 病史
-- --------------------------------------------
DROP TABLE IF EXISTS `miaowang_pet_medical_record`;
CREATE TABLE `miaowang_pet_medical_record` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `pid`         BIGINT        NOT NULL                COMMENT '宠物ID',
    `uid`         BIGINT        NOT NULL                COMMENT '归属用户ID',
    `username`    VARCHAR(64)   NULL DEFAULT NULL       COMMENT '用户名（冗余，便于排查）',
    `visit_at`    DATETIME      NOT NULL                COMMENT '就诊时间',
    `hospital`    VARCHAR(128)  NULL DEFAULT NULL       COMMENT '医院/诊所',
    `doctor`      VARCHAR(64)   NULL DEFAULT NULL       COMMENT '接诊医生',
    `diagnosis`   VARCHAR(512)  NULL DEFAULT NULL       COMMENT '诊断结果/病史',
    `treatment`   VARCHAR(1024) NULL DEFAULT NULL       COMMENT '处置与治疗方案',
    `cost`        DECIMAL(10,2) NULL DEFAULT NULL       COMMENT '费用（元）',
    `attachments` VARCHAR(2048) NULL DEFAULT NULL       COMMENT '化验单/影像附件 URL，逗号分隔',
    `remark`      VARCHAR(512)  NULL DEFAULT NULL       COMMENT '备注',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag`    TINYINT       NOT NULL DEFAULT 0      COMMENT '删除标识：0=未删除 1=已删除',
    PRIMARY KEY (`id`),
    KEY `idx_pid_time` (`pid`, `visit_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '宠物就诊记录表';

-- --------------------------------------------
-- 3. 用药记录
-- --------------------------------------------
DROP TABLE IF EXISTS `miaowang_pet_medication_record`;
CREATE TABLE `miaowang_pet_medication_record` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `pid`           BIGINT       NOT NULL                COMMENT '宠物ID',
    `uid`           BIGINT       NOT NULL                COMMENT '归属用户ID',
    `username`      VARCHAR(64)  NULL DEFAULT NULL       COMMENT '用户名（冗余，便于排查）',
    `medical_id`    BIGINT       NULL DEFAULT NULL       COMMENT '关联就诊记录ID（可空：日常用药没有就诊单）',
    `medicine_name` VARCHAR(128) NOT NULL                COMMENT '药品名',
    `dosage`        VARCHAR(64)  NULL DEFAULT NULL       COMMENT '单次用量，如：半片、5ml',
    `frequency`     VARCHAR(64)  NULL DEFAULT NULL       COMMENT '频次，如：每日2次',
    `start_at`      DATETIME     NOT NULL                COMMENT '开始用药时间',
    `end_at`        DATETIME     NULL DEFAULT NULL       COMMENT '结束时间（空=仍在用药）',
    `status`        TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0=用药中 1=已结束 2=已停用',
    `remark`        VARCHAR(512) NULL DEFAULT NULL       COMMENT '备注',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag`      TINYINT      NOT NULL DEFAULT 0      COMMENT '删除标识：0=未删除 1=已删除',
    PRIMARY KEY (`id`),
    KEY `idx_pid_start` (`pid`, `start_at`),
    KEY `idx_medical` (`medical_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '宠物的用药记录表';

-- --------------------------------------------
-- 4. 过敏史
-- --------------------------------------------
DROP TABLE IF EXISTS `miaowang_pet_allergy_record`;
CREATE TABLE `miaowang_pet_allergy_record` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `pid`         BIGINT       NOT NULL                COMMENT '宠物ID',
    `uid`         BIGINT       NOT NULL                COMMENT '归属用户ID',
    `username`    VARCHAR(64)  NULL DEFAULT NULL       COMMENT '用户名（冗余，便于排查）',
    `allergen`    VARCHAR(128) NOT NULL                COMMENT '过敏原，如：鸡肉、某品牌猫粮、青霉素',
    `severity`    TINYINT      NOT NULL DEFAULT 1      COMMENT '严重程度：1=轻度 2=中度 3=重度',
    `reaction`    VARCHAR(512) NULL DEFAULT NULL       COMMENT '过敏反应表现，如：呕吐、皮肤红疹',
    `note`        VARCHAR(512) NULL DEFAULT NULL       COMMENT '应对方式/备注',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag`    TINYINT      NOT NULL DEFAULT 0      COMMENT '删除标识：0=未删除 1=已删除',
    PRIMARY KEY (`id`),
    KEY `idx_pid` (`pid`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '宠物过敏史表';

-- --------------------------------------------
-- 5. 症状日记
--    symptom_id/symptom_name 对接知识库「健康自查」条目：
--    只存条目ID + 名称快照，知识库改文案后历史记录不会失真
-- --------------------------------------------
DROP TABLE IF EXISTS `miaowang_pet_symptom_log`;
CREATE TABLE `miaowang_pet_symptom_log` (
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

-- --------------------------------------------
-- 6. 体重记录（曲线由接口按天聚合，不额外存聚合表）
-- --------------------------------------------
DROP TABLE IF EXISTS `miaowang_pet_weight_record`;
CREATE TABLE `miaowang_pet_weight_record` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `pid`         BIGINT       NOT NULL                COMMENT '宠物ID',
    `uid`         BIGINT       NOT NULL                COMMENT '归属用户ID',
    `username`    VARCHAR(64)  NULL DEFAULT NULL       COMMENT '用户名（冗余，便于排查）',
    `weight_kg`   DECIMAL(6,2) NOT NULL                COMMENT '体重（kg）',
    `measured_at` DATETIME     NOT NULL                COMMENT '测量时间',
    `note`        VARCHAR(255) NULL DEFAULT NULL       COMMENT '备注',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag`    TINYINT      NOT NULL DEFAULT 0      COMMENT '删除标识：0=未删除 1=已删除',
    PRIMARY KEY (`id`),
    KEY `idx_pid_time` (`pid`, `measured_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '宠物体重记录表';

SET FOREIGN_KEY_CHECKS = 1;