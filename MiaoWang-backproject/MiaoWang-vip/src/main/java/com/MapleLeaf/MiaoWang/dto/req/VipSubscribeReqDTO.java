package com.MapleLeaf.MiaoWang.dto.req;


import lombok.Data;

/**
 * 开通/续费会员请求
 */
@Data
public class VipSubscribeReqDTO {

    /**
     * 档位编码，如 AI_249
     */
    private String planCode;
}