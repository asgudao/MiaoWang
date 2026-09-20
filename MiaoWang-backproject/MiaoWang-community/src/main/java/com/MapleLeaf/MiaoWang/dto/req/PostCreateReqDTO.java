package com.MapleLeaf.MiaoWang.dto.req;

import lombok.Data;

import java.util.List;

/**
 * 发布帖子请求
 * <p>
 * 流程：先调图片上传接口拿到 url/objectKey，再把它们塞进 images 一起提交；
 * 视频同理，先调视频上传接口（一次一个），再把 url/objectKey 塞进 videos。
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
     * 正文（可选，但不能和图片/视频同时为空）
     */
    private String content;

    /**
     * 图片列表：最多 9 张，顺序即展示顺序，第 1 张作为封面
     * <p>
     * ⚠️ 与 videos **共享同一个 9 条预算**：图片数 + 视频数 ≤ 9
     * （{@code CommunityConstant.MAX_POST_MEDIA_COUNT}）。
     * 单类型超 9 → {@code B000303}（图片）/ {@code B000332}（视频）；
     * 单看都没超、加起来超 9（如 5 图 + 5 视频）→ {@code B000333}。
     */
    private List<PostImageReqDTO> images;

    /**
     * 视频列表：最多 9 个，顺序即展示顺序，可以和图片同时存在
     * <p>
     * 每个元素都要有 url + objectKey（objectKey 是将来清理 MinIO 对象的凭据），
     * 缺了就是 {@code B000331}；超过 9 个是 {@code B000332}；
     * 与图片合计超过 9 条是 {@code B000333}（见 {@link #images}）。
     */
    private List<PostVideoReqDTO> videos;
}