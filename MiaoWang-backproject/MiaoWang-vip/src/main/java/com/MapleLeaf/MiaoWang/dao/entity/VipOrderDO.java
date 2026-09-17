package com.MapleLeaf.MiaoWang.dao.entity;


import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 会员订阅订单
 */
@Data
@TableName("miaowang_vip_order")
@EqualsAndHashCode(callSuper = false)
public class VipOrderDO extends BaseDO {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 用户ID
     */
    private Long uid;

    /**
     * 用户名（冗余，便于排查）
     */
    private String username;

    /**
     * 档位编码
     */
    private String planCode;

    /**
     * 档位名称快照
     */
    private String planName;

    /**
     * 支付金额（元）：档位原价快照
     */
    private BigDecimal amount;

    /**
     * 优惠金额（元）：原价 - 应付
     */
    private BigDecimal discountAmount;

    /**
     * 应付金额（元）：支付回调按它校验金额；0 表示促销单（没有钱要收）
     */
    private BigDecimal payableAmount;

    /**
     * 使用的促销码ID（立减码）
     */
    private Long promoCodeId;

    /**
     * 促销码快照
     */
    private String promoCode;

    /**
     * 支付方式：MOCK/WECHAT/ALIPAY
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
     * 订阅时长快照（天）：档位以后改时长不影响已售订单，退款回收时长也按这个数算
     */
    private Integer durationDays;

    /**
     * 下单幂等键（前端生成）：同一用户同一 requestId 只会出一单
     */
    private String requestId;

    /**
     * 支付渠道交易号（对账用）
     */
    private String channelTradeNo;

    /**
     * 退款单号
     */
    private String refundNo;

    /**
     * 退款原因
     */
    private String refundReason;

    /**
     * 退款完成时间
     */
    private Date refundTime;

    /**
     * 关单时间（超时未支付）
     */
    private Date closeTime;

    /**
     * 最近一次收到回调的时间
     */
    private Date notifyTime;

    /**
     * 累计收到回调次数（审计：渠道重试了几次、有没有被刷）
     */
    private Integer notifyCount;
}