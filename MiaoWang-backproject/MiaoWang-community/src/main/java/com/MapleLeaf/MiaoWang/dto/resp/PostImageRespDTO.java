package com.MapleLeaf.MiaoWang.dto.resp;

import lombok.Data;

/**
 * 帖子图片返回
 */
@Data
public class PostImageRespDTO {

    /**
     * 图片访问 URL
     */
    private String url;

    /**
     * MinIO 对象 key
     */
    private String objectKey;

    /**
     * 排序，0 为封面
     */
    private Integer sort;
}