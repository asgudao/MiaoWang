package com.MapleLeaf.MiaoWang.openfegin.config;


import cn.hutool.core.util.StrUtil;
import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign 透传登录头（username / token）
 *
 * <p>为什么必须透传：Feign 调用是「服务端发起的第二次 HTTP 请求」，
 * 浏览器原始请求头不会自动带过去；而下游服务的 UserTransmitFilter 靠
 * username + token 去 Redis 还原登录用户（UserContext）。
 * 不透传的话，下游永远认为自己「没登录」，按用户隔离的接口会全部失败。
 *
 * <p>注意：这里<b>刻意不加 @Configuration</b> —— 加了会被 common 的包扫描当成全局配置，
 * 导致所有服务、所有 Feign 调用都挂上这个拦截器。只在需要的 FeignClient 上用
 * {@code configuration} 属性引用，作用范围才精确可控。
 */
public class FeignHeaderPropagationConfig {

    /**
     * 把当前请求的登录头原样带给下游服务
     */
    @Bean
    public RequestInterceptor loginHeaderPropagationInterceptor() {
        return template -> {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                // 非 HTTP 请求线程（定时任务、异步线程）里没有请求上下文，直接跳过
                return;
            }
            HttpServletRequest request = attributes.getRequest();
            String username = request.getHeader("username");
            String token = request.getHeader("token");
            if (StrUtil.isNotBlank(username)) {
                template.header("username", username);
            }
            if (StrUtil.isNotBlank(token)) {
                template.header("token", token);
            }
        };
    }
}