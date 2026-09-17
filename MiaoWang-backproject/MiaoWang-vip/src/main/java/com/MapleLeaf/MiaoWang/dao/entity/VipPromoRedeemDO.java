package com.MapleLeaf.MiaoWang.dao.entity;


import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 促销码核销记录
 *
 * <p>唯一索引 {@code uk_promo_uid (promo_code_id, uid)} 是「每人限用一次」的最终保证 ——
 * 业务层的"查一下有没有用过"在并发下会漏，唯一索引不会。
 */
@Data
@TableName("miaowang_vip_promo_redeem")
@EqualsAndHashCode(callSuper = false)
public class VipPromoRedeemDO extends BaseDO {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 促销码ID
     */
    private Long promoCodeId;

    /**
     * 码快照（码被删也能追溯）
     */
    private String code;

    /**
     * 使用者
     */
    private Long uid;

    /**
     * 用户名（冗余，便于排查）
     */
    private String username;

    /**
     * 关联订单号（PLAN 是 0 元促销单；DISCOUNT 是那笔立减订单）
     */
    private String orderNo;

    /**
     * 核销时间
     */
    private Date redeemTime;
}