package com.MapleLeaf.MiaoWang.dao.entity;


import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 3D 宠物模型定制订单
 */
@Data
@TableName("miaowang_vip_3d_order")
@EqualsAndHashCode(callSuper = false)
public class Vip3dOrderDO extends BaseDO {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 定制单号
     */
    private String orderNo;

    /**
     * 下单用户ID（模型归属人）
     */
    private Long uid;

    /**
     * 用户名（冗余，便于排查）
     */
    private String username;

    /**
     * 宠物ID
     */
    private Long pid;

    /**
     * 宠物名
     */
    private String petName;

    /**
     * 定制需求描述
     */
    private String requirement;

    /**
     * 参考图 URL，逗号分隔
     */
    private String referenceImages;

    /**
     * 状态：0=待处理 1=制作中 2=已完成 3=已取消
     */
    private Integer status;

    /**
     * 定制模型地址（.glb），仅本人可访问
     */
    private String modelUrl;

    /**
     * 处理备注
     */
    private String adminRemark;

    /**
     * 完成时间
     */
    private Date finishTime;
}