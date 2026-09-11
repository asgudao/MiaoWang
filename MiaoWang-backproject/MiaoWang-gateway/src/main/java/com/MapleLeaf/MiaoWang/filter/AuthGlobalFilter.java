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
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

//全局过滤器，校验Token
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

    //校验http请求的header中是否有token，并且校验token有效性
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        //获取http请求的uri
        String requestUri = exchange.getRequest().getURI().getPath();
        //判断是否在在白名单中，如果在放行
        boolean isPass = isWhiteList(requestUri);
        if (isPass) {
            //放行
            return chain.filter(exchange);
        }
        //校验token
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
        //断了后续过滤器链条，并且返回JsonResult（登录过去或者未登录），结果json字符串
        ServerHttpResponse response = exchange.getResponse();
        //设置响应码（200）
        response.setStatusCode(HttpStatus.OK);
        //设置响应数据格式
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        //创建数据缓冲工厂
        DataBufferFactory dataBufferFactory = response.bufferFactory();
        //JsonResult转换成Json字符串
        JsonResult jsonResult = JsonResult.fail(-1, "未登录或者的登录过期");
        String jsonString;
        try {
            jsonString = objectMapper.writeValueAsString(jsonResult);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        //字符串包装成数据缓冲对象
        DataBuffer dataBuffer = dataBufferFactory.wrap(jsonString.getBytes());
        //数据缓冲对象响应输出
        return response.writeWith(Mono.just(dataBuffer));
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
