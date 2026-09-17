package com.MapleLeaf.MiaoWang.dto.req;

import lombok.Data;

import java.util.List;

/**
 * 编辑帖子请求
 * <p>
 * 字段语义（刻意和"发布"不同，避免"只想改标题结果把图删了"）：
 * 1. title / content：传 null = 不改；传空串 = 清空；
 * 2. images：传 null = 不动图片；传数组（哪怕空数组）= 整份替换，空数组即清空图片；
 * 3. videos：和 images 完全同一条规则（传 null = 不动；传数组 = 整份替换，空数组即清空）。
 * <p>
 * 版块不允许改：跨版块移动是运营动作，不该由作者自助完成。
 */
@Data
public class PostUpdateReqDTO {

    /**
     * 标题（null=不改；空串=清空）
     */
    private String title;

    /**
     * 正文（null=不改；空串=清空）
     */
    private String content;

    /**
     * 图片列表（null=不动图片；数组=整份替换，空数组=清空图片）
     */
    private List<PostImageReqDTO> images;

    /**
     * 视频列表（null=不动视频；数组=整份替换，空数组=清空视频；最多 9 个）
     * <p>
     * 刻意和 images 保持同一条规则：前端不用记两套"怎么算清空"的语义。
     */
    private List<PostVideoReqDTO> videos;
}