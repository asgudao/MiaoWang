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

    /**
     * 视频封面直链（服务端抽首帧生成）
     * <p>
     * <b>可能为 null</b>，而且这是常态而非异常：老数据没回填过、或抽帧失败（编码不支持）、
     * 或客户端发帖时没把上传接口返回的封面字段带回来 ⇒ 这里就是 {@code null}。
     * 前端必须按"{@code null} = 画中性占位（既有令牌色的中性底 + 自绘播放三角）"处理，
     * <b>不要</b>当错误、也不要留空白。
     * <p>
     * ⚠️ 不要拿帖子级的 {@code PostRespDTO.coverUrl} 来顶替它：那是"帖子的第 1 张<b>图片</b>"，
     * 与视频画面无关（一个"纯视频帖"的帖子封面本来就是 null）。
     */
    private String coverUrl;

    /**
     * 封面对象 key（清理封面对象时用；与 {@link #coverUrl} 同生同灭，要么都有要么都没有）
     */
    private String coverObjectKey;
}