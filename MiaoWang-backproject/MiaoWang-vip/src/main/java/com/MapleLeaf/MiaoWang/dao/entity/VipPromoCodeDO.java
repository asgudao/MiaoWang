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
 * 促销码（兑换码 / 优惠码）
 *
 * <p>一张表覆盖两种玩法，靠 {@code promoType} 区分：
 * <ul>
 *   <li>{@code PLAN}：核销即送 N 天指定档位</li>
 *   <li>{@code DISCOUNT}：下单立减，支付成功才消耗名额</li>
 * </ul>
 */
@Data
@TableName("miaowang_vip_promo_code")
@EqualsAndHashCode(callSuper = false)
public class VipPromoCodeDO extends BaseDO {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 码（对外展示与输入用，唯一）
     */
    private String code;

    /**
     * 活动名（后台识别用，如「双十一送会员」）
     */
    private String name;

    /**
     * 类型：PLAN=送会员时长 DISCOUNT=下单立减
     */
    private String promoType;

    /**
     * 档位编码：PLAN 必填；DISCOUNT 可填（限定适用档位），为空表示不限档位
     */
    private String planCode;

    /**
     * 赠送天数（PLAN 专用）
     */
    private Integer durationDays;

    /**
     * 立减金额（DISCOUNT 专用，元）
     */
    private BigDecimal discountAmount;

    /**
     * 使用门槛：订单金额需达到该值（DISCOUNT 专用，元；0=无门槛）
     */
    private BigDecimal minAmount;

    /**
     * 总名额，-1=不限
     */
    private Integer maxUseCount;

    /**
     * 已使用名额
     */
    private Integer usedCount;

    /**
     * 生效时间（可空=立即生效）
     */
    private Date startTime;

    /**
     * 失效时间（可空=永久有效）
     */
    private Date endTime;

    /**
     * 状态：0=停用 1=启用
     */
    private Integer status;

    /**
     * 备注（后台备注活动来源）
     */
    private String remark;
}