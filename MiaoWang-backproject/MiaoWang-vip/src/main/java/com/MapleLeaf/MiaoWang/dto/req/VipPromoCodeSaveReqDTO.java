package com.MapleLeaf.MiaoWang.dto.req;


import lombok.Data;

import java.math.BigDecimal;

/**
 * 促销码新增/编辑请求（后台）
 */
@Data
public class VipPromoCodeSaveReqDTO {

    /**
     * 主键：为空=新增，不为空=编辑
     */
    private Long id;

    /**
     * 促销码
     */
    private String code;

    /**
     * 活动名
     */
    private String name;

    /**
     * 类型：PLAN=送会员时长 DISCOUNT=下单立减
     */
    private String promoType;

    /**
     * 档位编码：PLAN 必填；DISCOUNT 为空=不限档位
     */
    private String planCode;

    /**
     * 赠送天数（PLAN）
     */
    private Integer durationDays;

    /**
     * 立减金额（DISCOUNT）
     */
    private BigDecimal discountAmount;

    /**
     * 使用门槛（DISCOUNT）
     */
    private BigDecimal minAmount;

    /**
     * 总名额，-1=不限
     */
    private Integer maxUseCount;

    /**
     * 生效时间，格式 yyyy-MM-dd HH:mm:ss（空=立即生效）
     */
    private String startTime;

    /**
     * 失效时间（空=永久有效）
     */
    private String endTime;

    /**
     * 状态：0=停用 1=启用
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;
}