package com.MapleLeaf.MiaoWang.constant;


/**
 * VIP 模块 Redis 常量类
 */
public class VipRedisConstant {

    /**
     * 每日额度计数 key 前缀
     * 完整 key 形如：MiaoWang:vip:quota:rag:{uid}:{yyyyMMdd}
     * 采用「按天分 key + 当天过期」的方式，不做定时任务清理
     */
    public static final String VIP_QUOTA_KEY_PREFIX = "MiaoWang:vip:quota:";

    /**
     * 拼接每日额度计数 key
     *
     * @param biz 业务类型（rag / chat）
     * @param uid 用户ID
     * @param day 日期，格式 yyyyMMdd
     */
    public static String buildQuotaKey(String biz, Long uid, String day) {
        return VIP_QUOTA_KEY_PREFIX + biz + ":" + uid + ":" + day;
    }
}