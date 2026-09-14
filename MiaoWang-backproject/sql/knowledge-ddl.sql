-- =============================================================
-- MiaoWang 知识库建表脚本
-- 数据库：MySQL 8，字符集 utf8mb4，引擎 InnoDB
-- 说明：知识库内容为「碎片化」形态（非长文章），一条记录 = 一个知识碎片
--       碎片天然可作为 RAG 的 chunk 使用（后续导入 Milvus）
-- =============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- --------------------------------------------
-- 1. 知识分类表
-- --------------------------------------------
DROP TABLE IF EXISTS `knowledge_category`;
CREATE TABLE `knowledge_category` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`        VARCHAR(32)  NOT NULL                COMMENT '分类编码：diet=饮食安全 behavior=行为解读 care=日常养护 health=健康自查',
    `name`        VARCHAR(64)  NOT NULL                COMMENT '分类名称',
    `species`     TINYINT      NOT NULL DEFAULT 0      COMMENT '适用物种：0=通用 1=猫 2=狗',
    `sort`        INT          NOT NULL DEFAULT 0      COMMENT '排序值，越小越靠前',
    `status`      TINYINT      NOT NULL DEFAULT 2      COMMENT '状态：0=草稿 1=待审 2=已发布 3=下架',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag`    TINYINT      NOT NULL DEFAULT 0      COMMENT '删除标识：0=未删除 1=已删除',
    PRIMARY KEY (`id`),
    KEY `idx_species_sort` (`species`, `sort`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '知识分类表';

-- --------------------------------------------
-- 2. 知识碎片表（内容主体）
-- --------------------------------------------
DROP TABLE IF EXISTS `knowledge_fragment`;
CREATE TABLE `knowledge_fragment` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `category_code` VARCHAR(32)   NOT NULL               COMMENT '所属分类编码，对应 knowledge_category.code',
    `species`       TINYINT       NOT NULL               COMMENT '物种：1=猫 2=狗',
    `breed_id`      BIGINT        NULL DEFAULT NULL      COMMENT '品种ID，NULL=全品种通用（预留，当前无品种数据）',
    `title`         VARCHAR(255)  NOT NULL               COMMENT '条目名，如：疫苗接种、摇尾巴、巧克力',
    `content`       TEXT          NOT NULL               COMMENT '主内容，如：幼犬6-8周龄起接种联苗…',
    `remark`        VARCHAR(1024) NULL DEFAULT NULL      COMMENT '备注/补充说明',
    `stage`         VARCHAR(32)   NULL DEFAULT NULL      COMMENT '适用阶段：幼年/成年/老年，NULL=通用',
    `content_type`  VARCHAR(32)   NOT NULL DEFAULT 'guide' COMMENT '内容类型：care_schedule=护理日程(提醒模块消费) guide=科普 warning=安全警示 medical=医疗自查',
    `tags`          VARCHAR(255)  NULL DEFAULT NULL      COMMENT '标签，逗号分隔',
    `view_count`    BIGINT        NOT NULL DEFAULT 0     COMMENT '阅读量（缓存热点演示用）',
    `source`        VARCHAR(32)   NOT NULL DEFAULT 'MANUAL' COMMENT '来源：EXCEL=手册导入 LLM=生成 MANUAL=人工',
    `batch_no`      VARCHAR(64)   NULL DEFAULT NULL      COMMENT '导入批次号',
    `status`        TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：0=草稿 1=待审 2=已发布 3=下架',
    `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag`      TINYINT       NOT NULL DEFAULT 0     COMMENT '删除标识：0=未删除 1=已删除',
    PRIMARY KEY (`id`),
    KEY `idx_species_category` (`species`, `category_code`),
    KEY `idx_category_status`  (`category_code`, `status`),
    KEY `idx_content_type`     (`content_type`),
    KEY `idx_breed`            (`breed_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '知识碎片表';

-- --------------------------------------------
-- 3. 分类种子数据（4 类 × 通用/猫/狗）
-- --------------------------------------------
INSERT INTO `knowledge_category` (`code`, `name`, `species`, `sort`) VALUES
('diet',     '饮食安全', 0, 1),
('behavior', '行为解读', 0, 2),
('care',     '日常养护', 0, 3),
('health',   '健康自查', 0, 4),
('diet',     '饮食安全', 1, 1),
('behavior', '行为解读', 1, 2),
('care',     '日常养护', 1, 3),
('health',   '健康自查', 1, 4),
('diet',     '饮食安全', 2, 1),
('behavior', '行为解读', 2, 2),
('care',     '日常养护', 2, 3),
('health',   '健康自查', 2, 4);

SET FOREIGN_KEY_CHECKS = 1;