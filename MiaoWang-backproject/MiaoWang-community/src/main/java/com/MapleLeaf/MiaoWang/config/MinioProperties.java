package com.MapleLeaf.MiaoWang.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MinIO 对象存储配置（对应 Nacos 里的 minio.*）
 * <p>
 * 代码里保留兜底默认值，与 MilvusProperties 的写法一致：
 * 即使 Nacos 上还没配这一段，服务也能正常启动（上传时才真正用到这些值）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    /**
     * MinIO 服务地址（S3 API 端口）
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
     * 存放社区图片的桶名
     */
    private String bucket = "miaowang-community";

    /**
     * 图片访问地址前缀：桶设为公共读后，图片 URL = public-base-url + "/" + objectKey
     */
    private String publicBaseUrl = "http://192.168.137.128:9000/miaowang-community";

    /**
     * 允许上传的图片扩展名，逗号分隔
     */
    private String allowedExtensions = "jpg,jpeg,png,webp";

    /**
     * 单张图片大小上限（MB）
     */
    private Integer maxSizeMb = 50;

    /**
     * 是否自动创建桶并设置公共读策略（首次上传时生效）
     */
    private Boolean autoCreateBucket = true;
}