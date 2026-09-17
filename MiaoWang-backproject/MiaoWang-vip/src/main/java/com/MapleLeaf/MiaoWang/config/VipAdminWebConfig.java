package com.MapleLeaf.MiaoWang.config;


import com.MapleLeaf.MiaoWang.interceptor.VipAdminAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 后台接口拦截器注册
 *
 * <p>只拦 {@code /vip/admin/**}：C 端接口的鉴权仍然由网关负责，后台自成一套。
 */
@Configuration
@RequiredArgsConstructor
public class VipAdminWebConfig implements WebMvcConfigurer {

    /**
     * 后台接口前缀
     */
    private static final String ADMIN_PATH_PATTERN = "/mapleleaf/miaowang/v1/vip/admin/**";

    /**
     * 登录接口本身当然不能要求先登录
     */
    private static final String ADMIN_LOGIN_PATH = "/mapleleaf/miaowang/v1/vip/admin/login";

    private final VipAdminAuthInterceptor vipAdminAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(vipAdminAuthInterceptor)
                .addPathPatterns(ADMIN_PATH_PATTERN)
                .excludePathPatterns(ADMIN_LOGIN_PATH);
    }
}