package com.MapleLeaf.MiaoWang.dto.req;


import lombok.Data;

/**
 * 3D 定制订单处理请求（制作方/运营侧回填模型地址）
 */
@Data
public class Vip3dCompleteReqDTO {

    /**
     * 定制单号
     */
    private String orderNo;

    /**
     * 定制模型地址（.glb）
     */
    private String modelUrl;

    /**
     * 处理备注
     */
    private String adminRemark;
}