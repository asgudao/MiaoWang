package com.MapleLeaf.MiaoWang.common.biz.file;

import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.config.MinioProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.util.List;

/**
 * 对象存储直链拼装的<b>唯一</b>位置（下发给客户端的 URL 只能从这里出）
 * <p>
 * <b>为什么要区分两个地址</b>：服务自己读对象走 SDK（{@code MinioClient} 用 {@code minio.endpoint}，
 * 内网/VM 地址即可），而下发给客户端的 URL 必须是<b>客户端访问得到</b>的地址。
 * 两者在网络不通的环境里天然不同：开发机上 MinIO 跑在 VM 网段
 * （{@code 192.168.137.128:9000}），开发机自己到得了、手机到不了 ——
 * 一旦把内网 endpoint 拼进返回给前端的 URL，PC 浏览器能显示、手机端整片图裂，
 * 且接口本身是通的（接口走网关的另一台地址），排查时极易误判成"图片丢了"。
 * <p>
 * <b>三级取址（优先级从高到低，下面的方法顺序就是决策顺序）</b>：
 * <ol>
 *   <li><b>显式配置</b> {@code minio.public-base-url}：配了就用它。生产填公网 endpoint 或 CDN 域名；
 *       同时它也是"不信任请求头"的那条路（Host 头由客户端控制，见下）</li>
 *   <li><b>没配 → 按本次请求进来的 Host 推导</b>：{@code X-Forwarded-Host} 优先（有反向代理/网关时），
 *       其次 {@code Host}，取 host 部分（<b>去掉端口</b>）；端口用 {@code minio.public-port}
 *       （默认 = {@code minio.endpoint} 的端口，今天即 9000）；协议取 {@code X-Forwarded-Proto}，
 *       没有就 {@code http}。这样开发期<b>一行配置都不用填</b>：客户端用哪个 IP 调的接口，
 *       图片地址就跟着用哪个 IP —— WLAN IP 变了也不用改配置</li>
 *   <li><b>兜底</b>：既没配、又拿不到请求上下文（定时任务 / 服务间 Feign / 单元测试）
 *       → 用 {@code minio.endpoint}（= 今天的行为）。<b>不抛异常、不拼 null / 空 host</b>：
 *       拿不到 host 就当第 2 级不成立，直接落到第 3 级</li>
 * </ol>
 * <p>
 * <b>权衡（写在明处）</b>：第 2 级信任了客户端可控的 Host 头（Host 头注入面）。
 * 影响面是"该次响应里的图片地址"，且它换来的是开发期零配置与 IP 漂移自愈，
 * 在开发/局域网场景是划算的；<b>生产必须显式配第 1 级</b>（配了就不看请求头）。
 * <p>
 * 新增任何"把对象地址交给客户端"的功能（新的上传接口、导出、分享链接…）都必须注入本类拼，
 * 不要再各写一份 base + "/" + bucket + "/" + objectKey（社区视频原来就是这么重复的一份）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ObjectStorageUrlBuilder {

    private final MinioProperties minioProperties;

    /**
     * 拼客户端可达的直链：对外入口 + "/" + 桶名 + "/" + objectKey
     *
     * @param bucket    MinIO 桶名
     * @param objectKey 对象 key（口径不变，仍然原样返回给前端）
     */
    public String buildPublicUrl(String bucket, String objectKey) {
        return StrUtil.removeSuffix(resolvePublicBaseUrl(), "/") + "/" + bucket + "/" + objectKey;
    }

    /**
     * 三级取址：显式配置 → 请求 Host 推导 → 内网 endpoint 兜底
     */
    private String resolvePublicBaseUrl() {
        // 1) 显式配置（生产走这条：公网 endpoint / CDN 域名）
        String configured = minioProperties.getPublicBaseUrl();
        if (StrUtil.isNotBlank(configured)) {
            return configured;
        }
        // 2) 按当前请求推导（开发期走这条：客户端用哪个 IP 访问，图片就用哪个 IP）
        String derived = deriveFromRequest();
        if (StrUtil.isNotBlank(derived)) {
            log.debug("下发对象 URL 使用请求 Host 推导出的对外地址：{}", derived);
            return derived;
        }
        // 3) 兜底：内网 endpoint（无请求上下文时与旧行为逐字一致）
        return minioProperties.getEndpoint();
    }

    /**
     * 从当前 HTTP 请求推导对外入口；拿不到请求上下文或 host 时返回 null（由调用方落到兜底）
     * <p>
     * 取上下文的手法与 common 的 {@code FeignHeaderPropagationConfig}、
     * vip 的 {@code VipAdminAuthSupport} 一致（{@code RequestContextHolder} + 空判断），不自创一套。
     */
    private String deriveFromRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            // 定时任务 / 异步线程 / 服务间 Feign / 单元测试：没有请求上下文，交给兜底
            return null;
        }
        HttpServletRequest request = servletAttributes.getRequest();
        if (request == null) {
            return null;
        }
        // X-Forwarded-Host 优先：经过网关/反向代理时，它才是客户端实际用的地址
        String host = normalizeHost(firstHeaderValue(request.getHeader("X-Forwarded-Host")));
        if (StrUtil.isBlank(host)) {
            host = normalizeHost(request.getHeader("Host"));
        }
        if (StrUtil.isBlank(host)) {
            host = normalizeHost(request.getServerName());
        }
        if (StrUtil.isBlank(host)) {
            // 连 host 都拿不到就不硬拼，落到内网 endpoint（宁可 PC 能看，也不能给出坏地址）
            return null;
        }
        // 协议：X-Forwarded-Proto 优先（https 反代场景），否则用请求本身的协议
        String scheme = firstHeaderValue(request.getHeader("X-Forwarded-Proto"));
        if (StrUtil.isBlank(scheme)) {
            scheme = StrUtil.blankToDefault(request.getScheme(), "http");
        }
        return scheme + "://" + host + ":" + resolvePublicPort();
    }

    /**
     * 推导用的端口：minio.public-port → 没配就用 minio.endpoint 的端口 → 再兜 9000
     * <p>
     * 为什么不把字段默认值写死成 9000：endpoint 换端口时（例如内网 MinIO 挪到 9100），
     * 推导端口应当跟着走，而不是悄悄指回 9000。今天 endpoint 是 9000，所以默认就是 9000。
     */
    private int resolvePublicPort() {
        Integer configuredPort = minioProperties.getPublicPort();
        if (configuredPort != null && configuredPort > 0 && configuredPort <= 65535) {
            return configuredPort;
        }
        Integer endpointPort = parsePort(minioProperties.getEndpoint());
        return endpointPort != null ? endpointPort : 9000;
    }

    /**
     * 取 endpoint 里的端口；没写端口就按协议默认端口（http=80 / https=443）
     */
    private static Integer parsePort(String endpoint) {
        if (StrUtil.isBlank(endpoint)) {
            return null;
        }
        try {
            URI uri = URI.create(endpoint.trim());
            if (uri.getPort() > 0) {
                return uri.getPort();
            }
            String scheme = StrUtil.blankToDefault(uri.getScheme(), "");
            if ("https".equalsIgnoreCase(scheme)) {
                return 443;
            }
            if ("http".equalsIgnoreCase(scheme)) {
                return 80;
            }
        } catch (Exception ignored) {
            // endpoint 写法不标准（没协议头等）时不做推断，交给上层兜 9000
        }
        return null;
    }

    /**
     * {@code X-Forwarded-*} 可能是 {@code "a, b"}（多级代理）：取第一个非空值
     */
    private static String firstHeaderValue(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        List<String> parts = StrUtil.splitTrim(value, ',');
        for (String part : parts) {
            if (StrUtil.isNotBlank(part)) {
                return part;
            }
        }
        return null;
    }

    /**
     * 从 {@code Host} 取值里剥离端口，并兼容 IPv6 字面量
     * <ul>
     *   <li>{@code 192.168.225.183:7000} → {@code 192.168.225.183}</li>
     *   <li>{@code [::1]:7000} → {@code [::1]}；裸 {@code ::1} → {@code [::1]}（拼 URL 必须带方括号）</li>
     *   <li>{@code example.com:} / {@code example.com} → {@code example.com}</li>
     * </ul>
     */
    private static String normalizeHost(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        String host = raw.trim();
        if (host.startsWith("[")) {
            int end = host.indexOf(']');
            return end > 0 ? host.substring(0, end + 1) : host;
        }
        int firstColon = host.indexOf(':');
        int lastColon = host.lastIndexOf(':');
        if (firstColon != lastColon) {
            // 多个冒号且没有方括号 = 裸 IPv6 字面量，整体是 host，没有端口
            return "[" + host + "]";
        }
        if (lastColon > 0) {
            String portPart = host.substring(lastColon + 1);
            // 冒号后面是端口（含"有冒号没端口"这种畸形写法）→ 一律只留 host
            if (portPart.isEmpty() || portPart.chars().allMatch(Character::isDigit)) {
                return host.substring(0, lastColon);
            }
        }
        return host;
    }
}