-- =============================================================
-- MiaoWang 会员（VIP）档位权益 v2 增量升级脚本
-- 适用：已经执行过 v1 版 vip-ddl.sql 的环境（表已存在）
-- 内容：① 额度列改名为周期无关的 rag_limit / chat_limit，并新增周期列
--       ② 新增健康档案与提醒相关权益列
--       ③ 用 v2 权益口径刷新六个档位的种子数据
-- 说明：本脚本可重复执行（幂等），不会动 miaowang_user_vip / 订单表的数据
-- =============================================================

SET NAMES utf8mb4;

-- --------------------------------------------
-- ① 列重命名 + 新增列
--    支持 DAY/MONTH 周期后，rag_daily_limit 这个名字会说谎，改成 rag_limit
-- --------------------------------------------
ALTER TABLE `miaowang_vip_plan`
    CHANGE COLUMN `rag_daily_limit`  `rag_limit`  INT NOT NULL DEFAULT 0 COMMENT 'RAG 次数上限，-1=不限次数',
    CHANGE COLUMN `chat_daily_limit` `chat_limit` INT NOT NULL DEFAULT 0 COMMENT '宠物聊天轮次上限，-1=不限次数',
    ADD COLUMN `rag_period`          VARCHAR(16) NOT NULL DEFAULT 'DAY' COMMENT 'RAG 额度周期：DAY=每日 MONTH=每月' AFTER `rag_limit`,
    ADD COLUMN `chat_period`         VARCHAR(16) NOT NULL DEFAULT 'DAY' COMMENT '聊天额度周期：DAY=每日 MONTH=每月' AFTER `chat_limit`,
    ADD COLUMN `health_record_limit` INT     NOT NULL DEFAULT 20  COMMENT '每只宠物每类健康记录条数上限，-1=不限' AFTER `chat_period`,
    ADD COLUMN `health_export`       TINYINT NOT NULL DEFAULT 0   COMMENT '是否可导出健康报告：0=否 1=是' AFTER `health_record_limit`,
    ADD COLUMN `health_ai_analysis`  TINYINT NOT NULL DEFAULT 0   COMMENT '是否可用 AI 解读症状：0=否 1=是' AFTER `health_export`,
    ADD COLUMN `reminder_limit`      INT     NOT NULL DEFAULT 3   COMMENT '提醒条数上限，-1=不限' AFTER `health_ai_analysis`;

-- --------------------------------------------
-- ② v2 权益口径（各维度随档位单调不降；-1 = 不限）
--    免费 RAG 改为按月 5 次（试用装），聊天保持按天 5 轮
--    宠物数量：**总数口径、不区分物种**（免费 2 只、会员 5 只）
--    —— 早期是"每种物种各算一份"，免费档 9 种物种 = 实际能养 9 只，与实际口径不符，已修正
-- --------------------------------------------
UPDATE `miaowang_vip_plan` SET
    `pet_limit_per_type` = 2,  `rag_limit` = 5,  `rag_period` = 'MONTH', `chat_limit` = 5,   `chat_period` = 'DAY',
    `health_record_limit` = 20, `health_export` = 0, `health_ai_analysis` = 0, `reminder_limit` = 3
WHERE `plan_code` = 'FREE';

UPDATE `miaowang_vip_plan` SET
    `pet_limit_per_type` = 5,  `rag_limit` = 5,  `rag_period` = 'MONTH', `chat_limit` = 5,   `chat_period` = 'DAY',
    `health_record_limit` = 20, `health_export` = 0, `health_ai_analysis` = 0, `reminder_limit` = 3
WHERE `plan_code` = 'PET_49';

UPDATE `miaowang_vip_plan` SET
    `pet_limit_per_type` = 5,  `rag_limit` = 10, `rag_period` = 'DAY',   `chat_limit` = 20,  `chat_period` = 'DAY',
    `health_record_limit` = 100, `health_export` = 0, `health_ai_analysis` = 0, `reminder_limit` = -1
WHERE `plan_code` = 'AI_149';

UPDATE `miaowang_vip_plan` SET
    `pet_limit_per_type` = 5,  `rag_limit` = 50, `rag_period` = 'DAY',   `chat_limit` = 50,  `chat_period` = 'DAY',
    `health_record_limit` = -1, `health_export` = 1, `health_ai_analysis` = 0, `reminder_limit` = -1
WHERE `plan_code` = 'AI_249';

UPDATE `miaowang_vip_plan` SET
    `pet_limit_per_type` = 5,  `rag_limit` = -1, `rag_period` = 'DAY',   `chat_limit` = 100, `chat_period` = 'DAY',
    `health_record_limit` = -1, `health_export` = 1, `health_ai_analysis` = 1, `reminder_limit` = -1
WHERE `plan_code` = 'AI_349';

UPDATE `miaowang_vip_plan` SET
    `pet_limit_per_type` = 5,  `rag_limit` = -1, `rag_period` = 'DAY',   `chat_limit` = -1,  `chat_period` = 'DAY',
    `health_record_limit` = -1, `health_export` = 1, `health_ai_analysis` = 1, `reminder_limit` = -1
WHERE `plan_code` = 'ALL_400';

-- --------------------------------------------
-- ③ 回查（应看到 6 行，且各维度随价格单调不降）
-- --------------------------------------------
SELECT `plan_code`, `price`, `pet_limit_per_type` AS pet,
       CONCAT(`rag_limit`, '/', `rag_period`) AS rag,
       CONCAT(`chat_limit`, '/', `chat_period`) AS chat,
       `health_record_limit` AS health_rec, `health_export` AS exp, `health_ai_analysis` AS ai, `reminder_limit` AS remind
FROM `miaowang_vip_plan`
ORDER BY `sort`;