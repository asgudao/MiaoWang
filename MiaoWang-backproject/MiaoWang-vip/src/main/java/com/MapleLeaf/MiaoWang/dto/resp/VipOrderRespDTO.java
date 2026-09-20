package com.MapleLeaf.MiaoWang.dto.resp;


import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

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
     * 支付金额（元）：档位原价
     */
    private BigDecimal amount;

    /**
     * 优惠金额（元）
     */
    private BigDecimal discountAmount;

    /**
     * 应付金额（元）：前端展示"实付"用；0 元订单是促销兑换单
     */
    private BigDecimal payableAmount;

    /**
     * 使用的促销码（下单时用了立减码才有值）
     */
    private String promoCode;

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

    /**
     * 支付状态中文（前端直接展示，不用自己维护 0/1/2/3 的映射）
     */
    private String payStatusDesc;

    /**
     * 渠道交易号（支付宝的 {@code trade_no}、mock 的 {@code MOCK...}）：待支付时为 null
     *
     * <p>排查支付问题（"渠道说付了、我们这边什么状态"）必须靠它去渠道后台对单，
     * 所以对外也要给出去，不能只留在库里。
     */
    private String channelTradeNo;

    /**
     * 退款单号（支付宝的 {@code out_request_no}，由我们生成并对渠道幂等）：未退款时为 null
     */
    private String refundNo;

    /**
     * 订阅时长（天）
     */
    private Integer durationDays;

    /**
     * 支付参数：仅「下单 / 模拟支付」接口返回，订单列表里为 null
     * （真实渠道是二维码链接、预支付ID、调起参数等；mock 只有操作提示）
     */
    private Map<String, Object> payParams;

    /**
     * 关单时间（待支付超时后自动关闭）
     */
    private Date closeTime;

    /**
     * 退款完成时间
     */
    private Date refundTime;

    /**
     * 退款原因
     */
    private String refundReason;
}