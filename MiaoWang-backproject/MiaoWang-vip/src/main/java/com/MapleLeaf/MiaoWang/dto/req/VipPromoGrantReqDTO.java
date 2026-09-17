package com.MapleLeaf.MiaoWang.dto.req;


import lombok.Data;

/**
 * 促销发权益指令：促销服务把「哪个用户、哪个档位、多少天、哪个码」交给订单服务，
 * 由订单服务生成 0 元促销订单并复用同一套发会员逻辑（订单服务不需要认识促销表）
 */
@Data
public class VipPromoGrantReqDTO {

    /**
     * 用户ID
     */
    private Long uid;

    /**
     * 用户名（冗余进订单）
     */
    private String username;

    /**
     * 档位编码
     */
    private String planCode;

    /**
     * 档位名快照
     */
    private String planName;

    /**
     * 赠送天数
     */
    private Integer durationDays;

    /**
     * 促销码快照（写进订单备注与订单号溯源）
     */
    private String promoCode;

    public static VipPromoGrantReqDTO of(Long uid, String username, String planCode, String planName,
                                         Integer durationDays, String promoCode) {
        VipPromoGrantReqDTO requestParam = new VipPromoGrantReqDTO();
        requestParam.setUid(uid);
        requestParam.setUsername(username);
        requestParam.setPlanCode(planCode);
        requestParam.setPlanName(planName);
        requestParam.setDurationDays(durationDays);
        requestParam.setPromoCode(promoCode);
        return requestParam;
    }
}