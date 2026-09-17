package com.MapleLeaf.MiaoWang.common.config;

import com.MapleLeaf.MiaoWang.common.enums.FileBizTypeEnum;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MinIO 对象存储配置（对应 Nacos 里的 minio.*）
 * <p>
 * <b>按业务分桶</b>：minio.buckets.&lt;业务标识&gt; = 桶名，
 * 业务标识取自 {@link FileBizTypeEnum#getBucketKey()}。
 * <p>
 * 代码里保留兜底默认值：Nacos 上没配这一段，服务也能正常启动和上传
 * （当前 Nacos 就没有 minio 配置，全部走这里的默认值）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    /**
     * MinIO 服务地址（S3 API 端口，不是控制台端口 9001）
     */
    private String endpoint = "http://192.168.137.128:9000";

    /**
     * 访问密钥
     */
    private String accessKey = "minioadmin";

    /**
     * 密钥
     */
    private String secretKey = "minioadmin";

    /**
     * 按业务分桶：key = 业务标识，value = 桶名
     * <p>
     * 例：minio.buckets.avatar=miaowang-user、minio.buckets.post=miaowang-community
     */
    private Map<String, String> buckets = new HashMap<>(Map.of(
            FileBizTypeEnum.AVATAR.getBucketKey(), "miaowang-user",
            FileBizTypeEnum.POST_IMAGE.getBucketKey(), "miaowang-community",
            FileBizTypeEnum.HEALTH_IMAGE.getBucketKey(), "miaowang-health"));

    /**
     * 对象公共读访问入口（<b>不含桶名</b>），可选；不配则用 endpoint。
     * <p>
     * 最终 URL = publicBaseUrl + "/" + 桶名 + "/" + objectKey。
     * 留空即可（默认拼 endpoint），只有当外部访问地址和内部 endpoint 不一致时才需要配。
     */
    private String publicBaseUrl;

    /**
     * 允许上传的图片扩展名，逗号分隔
     */
    private String allowedExtensions = "jpg,jpeg,png,webp";

    /**
     * 单个图片大小上限（MB）
     * <p>
     * 按服务粒度配置，与 spring.servlet.multipart.max-file-size 对齐：
     * 头像这类小图建议调小（如 5），帖子图片可放大。
     */
    private Integer maxSizeMb = 50;

    /**
     * 是否自动创建桶并设置公共读策略（首次上传时生效）
     */
    private Boolean autoCreateBucket = true;
}