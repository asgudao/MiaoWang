-- =============================================================
-- 喵汪 AI 电子宠物（ai-pet）DDL：会话 + 消息
--
-- 库：MiaoWang（与其它模块同库，不分库分表）
-- 执行：mysql -h192.168.137.128 -P3306 -uroot -p123456 -D MiaoWang < ai-pet-ddl.sql
--       或在客户端里直接整个脚本跑（全部 CREATE TABLE IF NOT EXISTS，可重复执行）
--
-- 与 ai-knowledge 的三张表（miaowang_ai_conversation / _message / _call_log）的关系：
--   ①② 是本模块独有的两张表，结构上与 ai-knowledge 那两张相似但**语义不同**：
--      - 那里是"一次提问一条会话"（话题制，有历史列表）
--      - 这里是"一只宠物一条会话"（陪伴关系，长期记忆线），所以多了 pid、pet_name 快照，
--        并且用 uk_uid_pid 唯一索引保证"一只宠物只能有一条会话"
--   ③ 调用日志**不新建表**：与 ai-knowledge 共用 miaowang_ai_call_log，
--      用 biz='chat' 区分（建表时就预留了这个业务位），这样成本与失败率能跨模块一起统计。
--
-- 两张表都继承项目约定：create_time / update_time / del_flag（0=未删除 1=已删除）
-- =============================================================

-- 1) 会话（一个用户 + 一只宠物 = 一条，长期存在） --------------------------
CREATE TABLE IF NOT EXISTS `miaowang_ai_pet_conversation` (
  `id`                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  `uid`               BIGINT      NOT NULL                COMMENT '所属用户',
  `pid`               BIGINT      NOT NULL                COMMENT '宠物ID',
  `pet_name`          VARCHAR(64) NOT NULL DEFAULT ''     COMMENT '宠物名快照（宠物改名后仍能看出当时聊的是谁）',
  `message_count`     INT         NOT NULL DEFAULT 0      COMMENT '消息条数（冗余：首页/列表不必再 count 子查询）',
  `last_message_time` DATETIME             DEFAULT NULL   COMMENT '最后一条消息时间（NULL=还没消息）',
  `create_time`       DATETIME             DEFAULT NULL,
  `update_time`       DATETIME             DEFAULT NULL,
  `del_flag`          TINYINT     NOT NULL DEFAULT 0      COMMENT '0=未删除 1=已删除',
  PRIMARY KEY (`id`),
  -- 一只宠物只能有一条会话。这个唯一索引同时是"并发首次开口"的兜底：
  -- 首页问候与进入对话页可能同时发生，两个请求都会尝试新建，
  -- 后到的那个 insert 被它拦下（DuplicateKeyException），服务层捕获后改成读取已有会话。
  UNIQUE KEY `uk_uid_pid` (`uid`, `pid`),
  KEY `idx_uid_last` (`uid`, `del_flag`, `last_message_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 电子宠物会话（一只宠物一条）';

-- 2) 消息（主人一句 / 宠物一句，各一行） ---------------------------------
CREATE TABLE IF NOT EXISTS `miaowang_ai_pet_message` (
  `id`              BIGINT   NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `conversation_id` BIGINT   NOT NULL                COMMENT '所属会话',
  `uid`             BIGINT   NOT NULL                COMMENT '所属用户（冗余：按人查、鉴权都要用）',
  `pid`             BIGINT   NOT NULL                COMMENT '宠物ID（冗余：同一用户多只宠物时按宠物过滤）',
  `role`            TINYINT  NOT NULL                COMMENT '角色：1=主人 2=宠物',
  `kind`            TINYINT  NOT NULL DEFAULT 0      COMMENT '来源：0=一问一答 1=自动问候（宠物主动开口）',
  `content`         TEXT                             COMMENT '消息正文',
  `token_in`        INT               DEFAULT NULL   COMMENT '输入 token（宠物行才有）',
  `token_out`       INT               DEFAULT NULL   COMMENT '输出 token（宠物行才有）',
  `latency_ms`      INT               DEFAULT NULL   COMMENT '本次生成耗时（毫秒）',
  `create_time`     DATETIME          DEFAULT NULL,
  `update_time`     DATETIME          DEFAULT NULL,
  `del_flag`        TINYINT  NOT NULL DEFAULT 0      COMMENT '0=未删除 1=已删除',
  PRIMARY KEY (`id`),
  -- 回放历史、取"最近 N 条拼提示词"、查"这个时段宠物说过话没有"，三条路都走它
  KEY `idx_conv` (`conversation_id`, `del_flag`, `id`),
  KEY `idx_uid_pid_time` (`uid`, `pid`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 电子宠物消息';