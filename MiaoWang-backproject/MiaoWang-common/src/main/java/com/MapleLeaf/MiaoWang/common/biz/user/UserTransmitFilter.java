package com.MapleLeaf.MiaoWang.common.biz.user;

import com.alibaba.fastjson2.JSON;
import cn.hutool.core.util.StrUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;

/**
 * 用户信息传输过滤器
 */
@RequiredArgsConstructor
public class UserTransmitFilter implements Filter {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        String userName = httpServletRequest.getHeader("username");
        String token = httpServletRequest.getHeader("token");
        // 空头保护：只有同时携带 username 和 token 的请求才还原登录用户；登录/注册等公开请求直接放行
        if (StrUtil.isAllNotBlank(userName, token)) {
            Object userInfoJsonStr = stringRedisTemplate.opsForHash().get("MiaoWang_login_" + userName, token);
            if (userInfoJsonStr != null) {
                UserInfoDTO userInfoDTO = JSON.parseObject(userInfoJsonStr.toString(), UserInfoDTO.class);
                UserContext.setUser(userInfoDTO);
            }
        }
        try {
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            UserContext.removeUser();
        }
    }
}