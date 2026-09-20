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
     * MinIO <b>内网</b>服务地址（S3 API 端口，不是控制台端口 9001）
     * <p>
     * 这是<b>服务自己</b>读写对象（putObject / getObject / 建桶 / 设桶策略）用的地址，
     * 由 {@code MinioConfig} 装配 {@code MinioClient} 时消费 —— <b>永远不变</b>。
     * <b>不要拿它拼下发给客户端的 URL</b>：客户端不一定访问得到这个地址。
     * 下发 URL 的取址见 {@link #publicBaseUrl} / {@link #publicPort}，
     * 以及 {@code ObjectStorageUrlBuilder} 的三级优先级（本字段是最后一级兜底）。
     * <p>
     * 顺带兼作"推导端口"的来源：{@link #publicPort} 不配时，按本地址的端口推导（今天 = 9000）。
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
     * 对象<b>对外可达</b>入口（Nacos key：{@code minio.public-base-url}，<b>不含桶名</b>），可选，
     * 是"下发 URL 取址"的<b>第 1 优先级</b>。
     * <p>
     * 最终下发给客户端的 URL = publicBaseUrl + "/" + 桶名 + "/" + objectKey。
     * <p>
     * <b>留空（默认）不再直接回退 endpoint</b>，而是先按本次请求的 Host 推导
     * （见 {@code ObjectStorageUrlBuilder} 的三级取址）；只有拿不到请求上下文时才回退
     * {@link #endpoint}。所以：
     * <ul>
     *   <li>开发/局域网：<b>什么都不用配</b> —— 手机用哪个 IP 调接口，图片地址就用哪个 IP</li>
     *   <li>生产：填公网 endpoint 或 CDN 域名（如 {@code https://cdn.example.com}）。
     *       配了就完全不看请求头，也就没有 Host 头被伪造的顾虑</li>
     * </ul>
     * <p>
     * 拼装点唯一收敛在 {@code ObjectStorageUrlBuilder#buildPublicUrl}，改这里即可全局生效
     * （社区图片/视频、头像、健康档案）。
     */
    private String publicBaseUrl;

    /**
     * 按请求 Host 推导"对外地址"时用的端口（Nacos key：{@code minio.public-port}），可选。
     * <p>
     * <b>默认（不配）= {@link #endpoint} 里的端口</b>，今天即 <b>9000</b>；解析不出来才兜 9000。
     * 之所以默认跟着 endpoint 的端口走而不是写死：内网 MinIO 换端口时推导端口应当跟着换。
     * <p>
     * 什么时候需要显式配：<b>客户端访问对象存储的端口 ≠ MinIO 端口</b>。
     * 典型是开发期用 {@code MiaoWang-frontproject/tools/minio-forward.js} 转发，
     * 而本机 9000 已被占用、转发工具改听 9100 → 这里配 {@code 9100}。
     */
    private Integer publicPort;

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