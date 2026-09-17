package com.MapleLeaf.MiaoWang.service;


import com.MapleLeaf.MiaoWang.dto.resp.VipQuotaRespDTO;

/**
 * 额度服务
 * 额度按「上限 + 周期」计数（DAY=每自然日 MONTH=每自然月），计数放 Redis，不落库、不跑定时任务
 */
public interface VipQuotaService {

    /**
     * 查询某用户某业务本周期的额度余量（不消耗次数）
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

    /**
     * 回退一次额度：调用方「扣了额度但业务最终失败」时调用，保证生成失败不扣用户次数
     * 不限次数的档位本来就不计数，调用即空操作；计数 key 已过期（跨周期）时同样空操作
     *
     * @param uid 用户ID
     * @param biz 业务类型：rag / chat
     */
    void refundQuota(Long uid, String biz);
}