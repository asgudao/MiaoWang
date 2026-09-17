-- =============================================================
-- ⚠️ 仅本地/测试造数用：把指定账号直接调成最高档 ALL_400（全能月卡）
--    不产生订单、不发任何消息 —— 就是往会员表里塞一行。
-- 生效判定（VipBenefitResolver 第 111-115 行）：
--    status = 1 且 del_flag = 0 且 expire_time > NOW()
--    start_time **不校验**（可以等于 NOW()），所以都写成 NOW() 就行。
-- 用法：改 @uid / @days，整份执行即可；可重复执行。
-- =============================================================

SET @uid  = 1;      -- 1 = MapleLeaf（要换人改这里）
SET @days = 365;    -- 有效天数；3650 ≈ 永久，30 = 想先试试月卡

-- ① 清掉该用户已有档位：避免多档位"按月等效取最优"合并出来的结果跟预期不一样
DELETE FROM `miaowang_user_vip` WHERE `uid` = @uid;

-- ② 开最高档（ALL_400 权益：宠物 5 只、RAG/聊天/健康记录/提醒全部 -1 不限、可导出报告、可用 AI 解读）
INSERT INTO `miaowang_user_vip` (`uid`, `username`, `plan_code`, `start_time`, `expire_time`, `status`, `del_flag`)
SELECT `uid`, `username`, 'ALL_400', NOW(), DATE_ADD(NOW(), INTERVAL @days DAY), 1, 0
FROM `miaowang_user`
WHERE `uid` = @uid;

-- ③ 验证：应看到 plan_code=ALL_400、status=1、expire_time 在未来
SELECT v.`uid`, v.`username`, v.`plan_code`, p.`plan_name`, v.`start_time`, v.`expire_time`, v.`status`
FROM `miaowang_user_vip` v
LEFT JOIN `miaowang_vip_plan` p ON p.`plan_code` = v.`plan_code`
WHERE v.`uid` = @uid;

-- ④ 想退回免费档：删掉这行即可（权益解析器找不到有效会员就回落 FREE 档）
-- DELETE FROM `miaowang_user_vip` WHERE `uid` = @uid;