package com.MapleLeaf.MiaoWang.filter;

import com.MapleLeaf.MiaoWang.common.JsonResult;
import com.MapleLeaf.MiaoWang.common.JwtTokenUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 全局过滤器：内部接口隔离 + Token 校验
 *
 * <p><b>顺序很重要：先拦内部接口，再校验登录。</b>
 * 各模块的 {@code /internal/**}（如 vip 的 benefit / quota / 3d-complete）是给其他服务调的，
 * 它们本身没有登录态、也拿不到调用方身份。如果只靠"要不要登录"来兜，任何登录用户都能：
 * 拿别人的 uid 查权益、刷光别人的额度、甚至把别人的 3D 定制单改成已完成并写入任意模型地址
 * （那个地址是要在对方首页展示的）。
 *
 * <p>服务间 Feign 调用走 Nacos 直连、不经过网关，所以<b>在网关拒绝就等于外部不可达</b>，
 * 不需要每个模块各写一套内部鉴权。
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {
    @Autowired
    private GatewayAuthProperties authProperties;

    @Autowired
    private AntPathMatcher antPathMatcher;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 校验请求的uri是否在白名单中
     */
    private boolean isWhiteList(String path) {
        List<String> urls = authProperties.getWhiteList();
        if (urls == null || urls.isEmpty()) {
            return false;
        }
        return urls.stream().anyMatch(pattern -> antPathMatcher.match(pattern, path));
    }

    /**
     * 校验请求的uri是否命中内部接口黑名单
     */
    private boolean isDenyList(String path) {
        List<String> urls = authProperties.getDenyList();
        if (urls == null || urls.isEmpty()) {
            return false;
        }
        return urls.stream().anyMatch(pattern -> antPathMatcher.match(pattern, path));
    }

    //校验http请求的header中是否有token，并且校验token有效性
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        //获取http请求的uri
        String requestUri = exchange.getRequest().getURI().getPath();
        //① 内部接口：一律拒绝外部访问（服务间 Feign 直连 Nacos，不经过这里）
        if (isDenyList(requestUri)) {
            log.warn("外部请求命中内部接口黑名单，已拒绝：{}", requestUri);
            return writeJsonResult(exchange, HttpStatus.FORBIDDEN,
                    JsonResult.fail(-1, "内部接口不对外开放"));
        }
        //② 判断是否在在白名单中，如果在放行
        boolean isPass = isWhiteList(requestUri);
        if (isPass) {
            //放行
            return chain.filter(exchange);
        }
        //③ 校验token
        //获取token
        List<String> tokenValues = exchange.getRequest().getHeaders().get("token");
        if (tokenValues != null && tokenValues.size() > 0) {
            //获取tokenValue
            String tokenValue = tokenValues.get(0);
            //校验
            boolean ok = JwtTokenUtil.isTokenValid(tokenValue);
            if (ok) {
                //放行
                return chain.filter(exchange);
            }
            log.warn("token无效或者已经过期,token={}", tokenValue);
        }
        //断了后续过滤器链条，并且返回JsonResult（登录过去或者未登录），结果json字符串。
        //这里刻意保持 HTTP 200 + state=-1：前端封装层是按 state 判断的，改成 401 会连带改前端
        return writeJsonResult(exchange, HttpStatus.OK, JsonResult.fail(-1, "未登录或者的登录过期"));
    }

    /**
     * 把 JsonResult 写回响应（网关的返回体约定是 JsonResult{state,message,data}）
     */
    private Mono<Void> writeJsonResult(ServerWebExchange exchange, HttpStatus status, JsonResult jsonResult) {
        ServerHttpResponse response = exchange.getResponse();
        //设置响应码
        response.setStatusCode(status);
        //设置响应数据格式
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        //JsonResult转换成Json字符串并包装成数据缓冲对象
        DataBuffer dataBuffer;
        try {
            dataBuffer = response.bufferFactory().wrap(objectMapper.writeValueAsBytes(jsonResult));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        //数据缓冲对象响应输出
        return response.writeWith(Mono.just(dataBuffer));
    }

    @Override
    public int getOrder() {
        return 0;
    }
}