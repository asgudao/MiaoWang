package com.MapleLeaf.MiaoWang.dto.req;


import lombok.Data;

/**
 * 兑换码核销请求
 */
@Data
public class VipPromoRedeemReqDTO {

    /**
     * 兑换码（不区分大小写，前后空格会自动去掉）
     */
    private String code;
}