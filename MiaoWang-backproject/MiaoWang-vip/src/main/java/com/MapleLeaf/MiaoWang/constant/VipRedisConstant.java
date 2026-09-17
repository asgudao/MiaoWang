package com.MapleLeaf.MiaoWang.constant;


/**
 * VIP 模块 Redis 常量类
 */
public class VipRedisConstant {

    /**
     * 额度计数 key 前缀
     * 完整 key 形如：MiaoWang:vip:quota:{biz}:{period}:{uid}:{bucket}
     * 例：MiaoWang:vip:quota:rag:MONTH:1001:202609 （2026 年 9 月的 RAG 用量）
     *     MiaoWang:vip:quota:chat:DAY:1001:20260916 （2026-09-16 当天的聊天用量）
     * key 自带归档粒度，用完即过期，不需要定时任务清理
     */
    public static final String VIP_QUOTA_KEY_PREFIX = "MiaoWang:vip:quota:";

    /**
     * 拼接额度计数 key
     *
     * @param biz    业务类型（rag / chat）
     * @param period 额度周期（DAY / MONTH）
     * @param uid    用户ID
     * @param bucket 归档刻度：DAY 传 yyyyMMdd，MONTH 传 yyyyMM
     */
    public static String buildQuotaKey(String biz, String period, Long uid, String bucket) {
        return VIP_QUOTA_KEY_PREFIX + biz + ":" + period + ":" + uid + ":" + bucket;
    }
}