package com.MapleLeaf.MiaoWang.dto.resp;


import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 会员订阅订单
 */
@Data
public class VipOrderRespDTO {

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 档位编码
     */
    private String planCode;

    /**
     * 档位名称
     */
    private String planName;

    /**
     * 支付金额（元）
     */
    private BigDecimal amount;

    /**
     * 支付方式
     */
    private String payType;

    /**
     * 支付状态：0=待支付 1=已支付 2=已关闭 3=已退款
     */
    private Integer payStatus;

    /**
     * 支付完成时间
     */
    private Date payTime;

    /**
     * 本次权益开始时间
     */
    private Date startTime;

    /**
     * 本次权益到期时间
     */
    private Date expireTime;

    /**
     * 下单时间
     */
    private Date createTime;
}