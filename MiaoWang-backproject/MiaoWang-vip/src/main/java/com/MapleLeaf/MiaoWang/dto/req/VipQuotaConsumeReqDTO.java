package com.MapleLeaf.MiaoWang.dto.req;


import lombok.Data;

/**
 * 扣减当前用户每日额度请求
 */
@Data
public class VipQuotaConsumeReqDTO {

    /**
     * 业务类型：rag / chat
     */
    private String biz;
}