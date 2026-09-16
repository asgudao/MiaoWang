package com.MapleLeaf.MiaoWang.dto.resp;


import lombok.Data;

import java.util.Date;

/**
 * 3D 模型定制订单
 */
@Data
public class Vip3dOrderRespDTO {

    /**
     * 定制单号
     */
    private String orderNo;

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
     * 状态描述
     */
    private String statusDesc;

    /**
     * 定制模型地址，仅本人可见（非本人返回 404/无权限）
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

    /**
     * 下单时间
     */
    private Date createTime;
}