package com.MapleLeaf.MiaoWang.dao.entity;


import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 后台操作日志
 *
 * <p>后台的写操作（改定制单状态、退款、发码）必须留痕：出了问题要能回答
 * "谁在什么时候把哪一单改成了什么"。这条日志是审计底线，不是可选项。
 */
@Data
@TableName("miaowang_vip_admin_op_log")
@EqualsAndHashCode(callSuper = false)
public class VipAdminOpLogDO extends BaseDO {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 操作人ID
     */
    private Long adminId;

    /**
     * 操作人登录名（冗余）
     */
    private String adminUsername;

    /**
     * 动作：3D_COMPLETE / ORDER_REFUND / PROMO_SAVE / ADMIN_CREATE ...
     */
    private String action;

    /**
     * 目标类型：3D_ORDER / VIP_ORDER / PROMO_CODE / ADMIN
     */
    private String targetType;

    /**
     * 目标标识（订单号、码等）
     */
    private String targetId;

    /**
     * 详情（变更内容摘要）
     */
    private String detail;

    /**
     * 来源IP
     */
    private String ip;
}