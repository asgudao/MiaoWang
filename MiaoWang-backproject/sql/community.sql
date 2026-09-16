-- ============================================================
-- 喵汪 MiaoWang · 社区模块建表脚本
-- 数据库：MiaoWang（与 datasource url 中的库名一致）
-- 字符集：utf8mb4
-- 说明：图片本体存 MinIO，MySQL 只存访问 URL 和对象 key
-- ============================================================

-- ------------------------------------------------------------
-- 1. 社区版块表（宠物美照 / 宠物知识 / 幼宠专区 / 品种鉴定 / 病症鉴定 / 活动专区）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `miaowang_community_section`
(
    `section_id`  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '版块id',
    `name`        VARCHAR(50)  NOT NULL COMMENT '版块名称，如：宠物美照',
    `code`        VARCHAR(50)  NOT NULL COMMENT '版块编码，如：photo，供前端/网关白名单匹配',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '版块简介',
    `icon_url`    VARCHAR(500) DEFAULT NULL COMMENT '版块图标URL',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1启用',
    `del_flag`    TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除 1已删除',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`section_id`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='社区版块表';

-- ------------------------------------------------------------
-- 2. 帖子表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `miaowang_community_post`
(
    `post_id`        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '帖子id',
    `section_id`     BIGINT       NOT NULL COMMENT '所属版块id',
    `uid`            BIGINT       NOT NULL COMMENT '发帖用户id',
    `username`       VARCHAR(50)  DEFAULT NULL COMMENT '发帖用户名（冗余，列表展示免联表）',
    `title`          VARCHAR(100) DEFAULT NULL COMMENT '标题（宠物美照等版块可为空）',
    `content`        TEXT COMMENT '正文内容',
    `cover_url`      VARCHAR(500) DEFAULT NULL COMMENT '封面图URL（列表展示用）',
    `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0草稿 1待审核 2已发布 3已驳回 4已下架',
    `is_top`         TINYINT      NOT NULL DEFAULT 0 COMMENT '是否置顶：0否 1是（活动专区公告用）',
    `view_count`     INT          NOT NULL DEFAULT 0 COMMENT '浏览数',
    `like_count`     INT          NOT NULL DEFAULT 0 COMMENT '点赞数（冗余计数）',
    `favorite_count` INT          NOT NULL DEFAULT 0 COMMENT '收藏数（冗余计数）',
    `comment_count`  INT          NOT NULL DEFAULT 0 COMMENT '评论数（冗余计数）',
    `del_flag`       TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除 1已删除',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`post_id`),
    KEY `idx_section_status_time` (`section_id`, `status`, `del_flag`, `create_time`),
    KEY `idx_uid_time` (`uid`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='社区帖子表';

-- ------------------------------------------------------------
-- 3. 帖子图片表（一个帖子多张图，图片实体存 MinIO）
-- 业务约束：一个帖子最多 9 张图（服务层 + 前端校验，数据库不强制）
-- 约定：sort=0 的那张作为封面，冗余写入 post.cover_url
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `miaowang_community_post_image`
(
    `image_id`    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '图片id',
    `post_id`     BIGINT       NOT NULL COMMENT '所属帖子id',
    `url`         VARCHAR(500) NOT NULL COMMENT '图片访问URL（MinIO）',
    `object_key`  VARCHAR(255) NOT NULL COMMENT 'MinIO对象key，删除帖子时用它删对象',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序，0为封面',
    `del_flag`    TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除 1已删除',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`image_id`),
    KEY `idx_post_id` (`post_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='社区帖子图片表（每帖最多9张）';

-- ------------------------------------------------------------
-- 4. 点赞表（多态归属：帖子 / 评论共用，取消点赞=物理删除）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `miaowang_community_like`
(
    `like_id`     BIGINT   NOT NULL AUTO_INCREMENT COMMENT '点赞id',
    `uid`         BIGINT   NOT NULL COMMENT '点赞用户id',
    `target_type` TINYINT  NOT NULL COMMENT '目标类型：1帖子 2评论',
    `target_id`   BIGINT   NOT NULL COMMENT '目标id',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    PRIMARY KEY (`like_id`),
    UNIQUE KEY `uk_user_target` (`uid`, `target_type`, `target_id`),
    KEY `idx_target` (`target_type`, `target_id`),
    KEY `idx_uid_time` (`uid`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='社区点赞表';

-- ------------------------------------------------------------
-- 5. 收藏表（多态归属：帖子 / 评论共用，取消收藏=物理删除）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `miaowang_community_favorite`
(
    `favorite_id` BIGINT   NOT NULL AUTO_INCREMENT COMMENT '收藏id',
    `uid`         BIGINT   NOT NULL COMMENT '收藏用户id',
    `target_type` TINYINT  NOT NULL COMMENT '目标类型：1帖子 2评论',
    `target_id`   BIGINT   NOT NULL COMMENT '目标id',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (`favorite_id`),
    UNIQUE KEY `uk_user_target` (`uid`, `target_type`, `target_id`),
    KEY `idx_target` (`target_type`, `target_id`),
    KEY `idx_uid_time` (`uid`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='社区收藏表';

-- ------------------------------------------------------------
-- 6. 评论表（原需求有；本期不做可先不建）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `miaowang_community_comment`
(
    `comment_id`  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '评论id',
    `post_id`     BIGINT        NOT NULL COMMENT '所属帖子id',
    `uid`         BIGINT        NOT NULL COMMENT '评论用户id',
    `username`    VARCHAR(50)   DEFAULT NULL COMMENT '评论用户名（冗余）',
    `parent_id`   BIGINT        NOT NULL DEFAULT 0 COMMENT '父评论id，0为一级评论',
    `reply_uid`   BIGINT        DEFAULT NULL COMMENT '被回复用户id',
    `content`     VARCHAR(1000) NOT NULL COMMENT '评论内容',
    `like_count`  INT           NOT NULL DEFAULT 0 COMMENT '点赞数（冗余计数）',
    `status`      TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1待审核 2已发布 3已驳回 4已下架',
    `del_flag`    TINYINT       NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除 1已删除',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`comment_id`),
    KEY `idx_post_status_time` (`post_id`, `status`, `del_flag`, `create_time`),
    KEY `idx_uid_time` (`uid`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='社区评论表';

-- ------------------------------------------------------------
-- 初始化版块数据
-- 注意：病症鉴定属于用户间经验交流，不构成兽医诊断，页面需展示免责声明
-- ------------------------------------------------------------
INSERT INTO `miaowang_community_section` (`name`, `code`, `description`, `sort`, `status`)
VALUES ('宠物美照', 'photo', '晒出你家毛孩子的高光时刻', 1, 1),
       ('宠物知识', 'knowledge', '养宠经验与知识分享', 2, 1),
       ('幼宠专区', 'baby', '幼猫幼犬喂养与护理交流', 3, 1),
       ('品种鉴定', 'breed', '发照片，大家一起帮你看品种', 4, 1),
       ('病症鉴定', 'symptom', '症状交流参考，不替代兽医诊断', 5, 1),
       ('活动专区', 'activity', '官方活动与话题', 6, 1);