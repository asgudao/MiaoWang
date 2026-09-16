package com.MapleLeaf.MiaoWang.dto.req;


import lombok.Data;

/**
 * 服务间扣减额度请求（供 ai-pet / ai-knowledge 调用）
 */
@Data
public class VipInternalQuotaConsumeReqDTO {

    /**
     * 用户ID
     */
    private Long uid;

    /**
     * 业务类型：rag / chat
     */
    private String biz;
}