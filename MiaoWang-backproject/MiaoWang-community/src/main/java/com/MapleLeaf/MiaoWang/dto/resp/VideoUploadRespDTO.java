package com.MapleLeaf.MiaoWang.dto.resp;

import lombok.Data;

/**
 * 视频上传返回
 * <p>
 * 和图片上传一个套路：objectKey 要原样带回发帖/编辑接口，url 直接给前端播放。
 * 额外带上 size 与 format，前端可以在上传完成时就显示"12.3MB / mp4"，不用再探测。
 */
@Data
public class VideoUploadRespDTO {

    /**
     * 视频访问 URL
     */
    private String url;

    /**
     * MinIO 对象 key（清理对象时要用，入库必须存它而不是 URL）
     */
    private String objectKey;

    /**
     * 文件字节数
     */
    private Long size;

    /**
     * 扩展名（小写，不含点）
     */
    private String format;
}