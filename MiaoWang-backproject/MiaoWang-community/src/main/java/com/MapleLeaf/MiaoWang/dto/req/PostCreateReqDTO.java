package com.MapleLeaf.MiaoWang.dto.req;

import lombok.Data;

import java.util.List;

/**
 * 发布帖子请求
 * <p>
 * 流程：先调图片上传接口拿到 url/objectKey，再把它们塞进 images 一起提交。
 */
@Data
public class PostCreateReqDTO {

    /**
     * 版块 id（必填）
     */
    private Long sectionId;

    /**
     * 标题（可选）
     */
    private String title;

    /**
     * 正文（可选，但不能和图片同时为空）
     */
    private String content;

    /**
     * 图片列表：最多 9 张，顺序即展示顺序，第 1 张作为封面
     */
    private List<PostImageReqDTO> images;
}