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

    /**
     * 视频封面直链（服务端抽首帧生成）
     * <p>
     * <b>可空，且这是设计内的降级状态</b>：抽帧失败（编码不支持 / 视频损坏）时就是 {@code null}，
     * 此时上传<b>照样成功</b>（url / objectKey 都正常），前端画中性占位即可 ——
     * 绝不能因为"抽不出封面"让一次正常的视频上传失败。
     * <p>
     * 发帖/编辑时要把它和 {@code objectKey} 一起原样带回
     * （{@code PostVideoReqDTO.coverUrl} / {@code coverObjectKey}），否则这一帖没有封面。
     */
    private String coverUrl;

    /**
     * 封面 MinIO 对象 key（与 {@link #coverUrl} 成对；清理封面对象时用）
     */
    private String coverObjectKey;
}