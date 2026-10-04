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
     * 促销码ID：与 {@link #promoCode} 一起写进订单
     *
     * <p>id 用于对账/统计时回连促销码表（谁发的、发了多少），code 是快照 ——
     * 码被改名或删除后，历史订单仍能显示当时用的是什么码。两个都要写。
     */
    private Long promoCodeId;

    /**
     * 促销码快照（写进订单备注与订单号溯源）
     */
    private String promoCode;

    public static VipPromoGrantReqDTO of(Long uid, String username, String planCode, String planName,
                                         Integer durationDays, Long promoCodeId, String promoCode) {
        VipPromoGrantReqDTO requestParam = new VipPromoGrantReqDTO();
        requestParam.setUid(uid);
        requestParam.setUsername(username);
        requestParam.setPlanCode(planCode);
        requestParam.setPlanName(planName);
        requestParam.setDurationDays(durationDays);
        requestParam.setPromoCodeId(promoCodeId);
        requestParam.setPromoCode(promoCode);
        return requestParam;
    }
}