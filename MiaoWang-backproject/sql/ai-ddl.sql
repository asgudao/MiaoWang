-- =============================================================
-- 喵汪 AI 问答记录层 DDL（会话 / 消息 / 调用日志）
--
-- 库：miaowang（与其它模块同库，不分库分表）
-- 执行：客户端里直接跑，或 mysql -u<user> -p miaowang < ai-ddl.sql
-- 说明：三张表一次性建好，避免二次迁移
--   ① miaowang_ai_conversation  会话（多轮对话 + 历史列表的骨架）
--   ② miaowang_ai_message       消息（一问一答各一行，含引用来源、token、反馈）
--   ③ miaowang_ai_call_log      调用日志（成本统计 + 排障）
--
-- 为什么 ③ 不并进 ②：失败的调用根本没有消息行（大模型报错/超时/检索失败），
-- 但成本与稳定性统计必须把它们算进去，所以日志必须是独立的一张"只写不改"的表。
--
-- 三张表都继承项目约定：create_time / update_time / del_flag（del_flag 0=未删除 1=已删除）
-- =============================================================

-- 1) 会话 ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS `miaowang_ai_conversation` (
  `id`                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  `uid`               BIGINT      NOT NULL                COMMENT '所属用户',
  `title`             VARCHAR(64) NOT NULL DEFAULT ''     COMMENT '标题（取首条提问前 20 字，避免列表页拉正文）',
  `message_count`     INT         NOT NULL DEFAULT 0      COMMENT '消息条数（冗余字段：列表页不用再 count 子查询）',
  `last_message_time` DATETIME             DEFAULT NULL   COMMENT '最后一条消息时间（列表按它倒序；NULL=还没消息）',
  `create_time`       DATETIME             DEFAULT NULL,
  `update_time`       DATETIME             DEFAULT NULL,
  `del_flag`          TINYINT     NOT NULL DEFAULT 0      COMMENT '0=未删除 1=已删除',
  PRIMARY KEY (`id`),
  KEY `idx_uid_last` (`uid`, `del_flag`, `last_message_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 问答会话';

-- 2) 消息 ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS `miaowang_ai_message` (
  `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `conversation_id` BIGINT      NOT NULL                COMMENT '所属会话',
  `uid`             BIGINT      NOT NULL                COMMENT '所属用户（冗余：反馈鉴权、按人查都要用）',
  `role`            TINYINT     NOT NULL                COMMENT '1=用户提问 2=AI回答',
  `content`         TEXT                                COMMENT '消息正文',
  `sources_json`    TEXT                                COMMENT 'AI 回答引用的知识碎片 JSON（回放历史时原样返回，省一次检索）',
  `has_knowledge`   TINYINT              DEFAULT NULL   COMMENT 'AI 行：是否命中知识库 0=否 1=是',
  `token_in`        INT                  DEFAULT NULL   COMMENT '输入 token（AI 行才有）',
  `token_out`       INT                  DEFAULT NULL   COMMENT '输出 token',
  `latency_ms`      INT                  DEFAULT NULL   COMMENT '本次生成耗时（毫秒）',
  `feedback`        TINYINT              DEFAULT NULL   COMMENT '答案反馈：NULL=未评价 1=👍 2=👎',
  `feedback_time`   DATETIME             DEFAULT NULL   COMMENT '反馈时间',
  `create_time`     DATETIME             DEFAULT NULL,
  `update_time`     DATETIME             DEFAULT NULL,
  `del_flag`        TINYINT     NOT NULL DEFAULT 0      COMMENT '0=未删除 1=已删除',
  PRIMARY KEY (`id`),
  KEY `idx_conv` (`conversation_id`, `del_flag`, `id`),
  KEY `idx_uid_feedback` (`uid`, `feedback`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 问答消息';

-- 3) 调用日志（append-only：只插不改，del_flag 恒为 0） -----------------
CREATE TABLE IF NOT EXISTS `miaowang_ai_call_log` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `uid`             BIGINT                 DEFAULT NULL   COMMENT '用户ID（未登录/额度拒绝时可能为空）',
  `biz`             VARCHAR(16)   NOT NULL DEFAULT 'rag'  COMMENT '业务：rag / chat / index',
  `scene`           VARCHAR(24)   NOT NULL                COMMENT '场景：ask / ask_stream / index_build',
  `conversation_id` BIGINT                 DEFAULT NULL   COMMENT '关联会话（可空）',
  `message_id`      BIGINT                 DEFAULT NULL   COMMENT '关联 AI 消息（可空）',
  `model`           VARCHAR(64)            DEFAULT NULL   COMMENT '模型名，如 qwen3.7-flash',
  `success`         TINYINT       NOT NULL                COMMENT '1=成功 0=失败',
  `error_code`      VARCHAR(32)            DEFAULT NULL   COMMENT '失败时的业务错误码',
  `error_message`   VARCHAR(255)           DEFAULT NULL   COMMENT '失败原因（截断保存）',
  `token_in`        INT                    DEFAULT NULL   COMMENT '输入 token',
  `token_out`       INT                    DEFAULT NULL   COMMENT '输出 token',
  `total_token`     INT                    DEFAULT NULL   COMMENT '总 token（冗余，聚合统计少算一次加法）',
  `latency_ms`      INT                    DEFAULT NULL   COMMENT '耗时（毫秒）',
  `estimated_cost`  DECIMAL(12,6)          DEFAULT NULL   COMMENT '估算成本（元）；未配置单价时为 NULL，不猜数',
  `create_time`     DATETIME               DEFAULT NULL,
  `update_time`     DATETIME               DEFAULT NULL,
  `del_flag`        TINYINT       NOT NULL DEFAULT 0      COMMENT '0=未删除（日志表不软删，留着是为复用 BaseDO 自动填充）',
  PRIMARY KEY (`id`),
  KEY `idx_uid_time` (`uid`, `create_time`),
  KEY `idx_time` (`create_time`),
  KEY `idx_scene_time` (`scene`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 调用日志（成本统计与排障，含失败调用）';