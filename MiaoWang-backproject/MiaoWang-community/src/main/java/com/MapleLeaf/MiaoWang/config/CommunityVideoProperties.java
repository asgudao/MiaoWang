package com.MapleLeaf.MiaoWang.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 社区视频上传配置（对应 Nacos 里的 community.video.*）
 * <p>
 * 为什么单独一套配置，而不是复用 common 的 minio.*：
 * {@code minio.allowed-extensions} / {@code minio.max-size-mb} 是给<b>图片</b>定的口径
 * （jpg/png/webp、50MB），视频要的是 mp4/mov、几百 MB，两者混在一个开关里必然互相妥协。
 * 桶仍然复用社区桶（{@code minio.buckets.post}），只是换个对象目录，不再多开一个桶 ——
 * 视频和帖子图片同属社区内容，生命周期、清理策略一致。
 * <p>
 * 代码里全部给了默认值，Nacos 上不配也能跑。
 */
@Data
@Component
@ConfigurationProperties(prefix = "community.video")
public class CommunityVideoProperties {

    /**
     * 允许的视频扩展名，逗号分隔
     */
    private String allowedExtensions = "mp4,mov";

    /**
     * 单个视频大小上限（MB）
     * <p>
     * 要和 {@code spring.servlet.multipart.max-file-size} 对齐：容器层先拦一次，
     * 这里再拦一次，容器层给的是笼统的"上传超限"，这里能给出精确的业务错误码。
     */
    private Integer maxSizeMb = 100;

    /**
     * 对象 key 目录前缀（桶内路径，不是桶名）
     */
    private String objectDir = "community/video/";

    /**
     * 用哪个桶：对应 {@code minio.buckets.<bucketKey>}，默认复用社区桶
     */
    private String bucketKey = "post";

    /**
     * 上传视频时是否抽首帧当封面（Nacos key：{@code community.video.cover-enabled}）
     * <p>
     * 抽帧是**尽力而为**的附加步骤：关掉它、或它失败，上传都照常成功，只是本次响应里
     * {@code coverUrl} / {@code coverObjectKey} 为空。留这个开关是为了"线上抽帧拖慢上传 / 吃 CPU 时
     * 能一行配置先关掉，而不是回滚代码"。
     */
    private Boolean coverEnabled = true;

    /**
     * 抽帧定位到第几秒（Nacos key：{@code community.video.cover-seek-second}），默认 0.1
     * <p>
     * 不直接取第 0 帧的原因：不少素材的第 0 帧是黑场 / 片头黑帧，抽出来等于一张黑图。
     * 往后挪 0.1s 基本不影响"看到第一眼画面"的语义，又能避开黑帧。
     * 实测本仓库 4 个素材的 0.0s 与 0.1s 都是正常画面（无黑帧），所以这个值只是保险。
     * 配 0 表示直接取第 0 帧。
     */
    private Double coverSeekSecond = 0.1;
}