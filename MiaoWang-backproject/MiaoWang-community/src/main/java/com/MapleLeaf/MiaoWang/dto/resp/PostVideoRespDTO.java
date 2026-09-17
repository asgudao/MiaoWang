package com.MapleLeaf.MiaoWang.dto.resp;

import lombok.Data;

/**
 * 帖子里的视频
 * <p>
 * 没有视频时整个字段是 null（不是空对象），前端判空即可。
 */
@Data
public class PostVideoRespDTO {

    /**
     * 视频访问 URL
     */
    private String url;

    /**
     * MinIO 对象 key
     */
    private String objectKey;
}