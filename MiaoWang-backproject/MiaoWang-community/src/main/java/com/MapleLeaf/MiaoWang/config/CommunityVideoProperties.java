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
}