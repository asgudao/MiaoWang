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
}