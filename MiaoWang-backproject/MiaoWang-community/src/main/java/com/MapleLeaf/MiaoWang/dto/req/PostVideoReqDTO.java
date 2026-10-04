package com.MapleLeaf.MiaoWang.dto.req;

import lombok.Data;

/**
 * 帖子视频项
 * <p>
 * 和图片一样是"两段式"：先调视频上传接口拿到 url/objectKey，再随发帖/编辑提交。
 * 一个帖子最多一个视频，所以这里是个对象而不是数组。
 */
@Data
public class PostVideoReqDTO {

    /**
     * 视频访问 URL（必填）
     */
    private String url;

    /**
     * MinIO 对象 key（必填，入库靠它做后续清理）
     */
    private String objectKey;

    /**
     * 视频封面 URL（**可选**）
     * <p>
     * 来源就是视频上传接口 {@code POST /community/file/video} 返回的同名字段 ——
     * 服务端在上传时已经抽好首帧并存进对象存储了，客户端**只需原样带回**，不用理解它是什么。
     * <p>
     * ⚠️ 这条是可选的、而且是"软"的：没带 / 只带一半 ⇒ 服务端存 null（前端画中性占位），
     * <b>不会</b>因此拒绝发帖（封面是装饰，不该挡住主流程）。
     * 漏带的代价只是"这一帖暂时没有封面"，跑一次
     * {@code dev-tools/video-cover-backfill} 就能按 objectKey 把它补上。
     */
    private String coverUrl;

    /**
     * 封面 MinIO 对象 key（可选，与 {@link #coverUrl} 成对；单独给一个会被忽略）
     */
    private String coverObjectKey;
}