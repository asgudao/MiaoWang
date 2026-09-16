package com.MapleLeaf.MiaoWang.dto.req;

import lombok.Data;

/**
 * 帖子图片项（发帖时前端把上传接口返回的 url/objectKey 一起带回来）
 */
@Data
public class PostImageReqDTO {

    /**
     * 图片访问 URL（上传接口返回）
     */
    private String url;

    /**
     * MinIO 对象 key（上传接口返回）
     */
    private String objectKey;
}