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
     * 支付金额（元）
     */
    private BigDecimal amount;

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
}