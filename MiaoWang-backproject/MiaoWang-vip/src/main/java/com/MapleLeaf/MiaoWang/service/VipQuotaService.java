package com.MapleLeaf.MiaoWang.service;


import com.MapleLeaf.MiaoWang.dto.resp.VipQuotaRespDTO;

/**
 * 每日额度服务
 * 额度计数放在 Redis（按天分 key），不落库、不跑定时任务
 */
public interface VipQuotaService {

    /**
     * 查询某用户某业务的今日额度（不消耗次数）
     *
     * @param uid 用户ID
     * @param biz 业务类型：rag / chat
     */
    VipQuotaRespDTO queryQuota(Long uid, String biz);

    /**
     * 消耗一次额度（会真实扣减），返回是否允许本次使用
     * 注意：调用方必须在「真正执行业务动作之前」调用，避免先回答后扣次数
     *
     * @param uid 用户ID
     * @param biz 业务类型：rag / chat
     */
    VipQuotaRespDTO consumeQuota(Long uid, String biz);
}