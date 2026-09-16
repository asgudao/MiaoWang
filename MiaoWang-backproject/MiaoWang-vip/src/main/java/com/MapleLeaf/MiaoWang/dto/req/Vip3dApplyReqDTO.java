package com.MapleLeaf.MiaoWang.dto.req;


import lombok.Data;

/**
 * 提交 3D 模型定制申请
 */
@Data
public class Vip3dApplyReqDTO {

    /**
     * 宠物ID（可为空：用户还没建宠物档案时也能先提需求）
     */
    private Long pid;

    /**
     * 宠物名
     */
    private String petName;

    /**
     * 定制需求描述（毛色、姿态、特征等）
     */
    private String requirement;

    /**
     * 参考图 URL，逗号分隔
     */
    private String referenceImages;
}