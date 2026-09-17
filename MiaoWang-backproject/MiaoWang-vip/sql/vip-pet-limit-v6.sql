-- =============================================================
-- MiaoWang 会员档位：宠物数量口径修正（v6）
-- 背景：原口径是「每种物种各自计数」，免费档 9 种物种各 1 只 = 实际能养 9 只，与产品口径不符。
--       现产品定案：**按宠物总数计数、不区分物种** —— 免费档一共 2 只、会员档一共 5 只。
-- 内容：① 免费档 1 → 2（会员档保持 5，语义由"每种"变为"总共"）
--       ② 修正列注释（旧注释写的是"每种物种可养数量上限"，会误导后来的人）
-- 说明：可重复执行
-- =============================================================

SET NAMES utf8mb4;

-- ① 免费档：总上限 2 只（会员档 5 不变，只是语义从"每种 5 只"变成"总共 5 只"）
UPDATE `miaowang_vip_plan`
SET `pet_limit_per_type` = 2
WHERE `plan_code` = 'FREE';

-- ② 列注释跟着改正（值不变，只是别让注释继续骗人）
ALTER TABLE `miaowang_vip_plan`
    MODIFY COLUMN `pet_limit_per_type` INT NOT NULL DEFAULT 2
    COMMENT '可养宠物总数量上限（不区分物种；-1=不限）';

-- ③ 验证
SELECT `plan_code`, `plan_name`, `pet_limit_per_type` AS pet_total_limit, `purchasable`
FROM `miaowang_vip_plan`
ORDER BY `id`;