package com.MapleLeaf.MiaoWang.interceptor;


import com.MapleLeaf.MiaoWang.support.VipAdminContext;
import com.MapleLeaf.MiaoWang.support.VipAdminAuthSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 后台接口鉴权拦截器
 *
 * <p>网关只负责"放行"，真正的鉴权在这里：管理端用独立的 {@code X-Admin-Token}，
 * 与 C 端 Authorization 完全无关。
 *
 * <p>这里每请求都查一次库（而不是把管理员信息全塞进 token/Redis）：
 * 后台 QPS 极低，而"禁用管理员立即生效"比省一次查询重要得多。
 */
@Component
@RequiredArgsConstructor
public class VipAdminAuthInterceptor implements HandlerInterceptor {

    private final VipAdminAuthSupport vipAdminAuthSupport;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader(VipAdminContext.ADMIN_TOKEN_HEADER);
        // 校验不通过直接抛异常（由全局异常处理器转成统一返回体），不放行到 Controller
        VipAdminContext.set(vipAdminAuthSupport.requireAdminByToken(token));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                               Object handler, Exception exception) {
        // 线程池复用线程：必须清理，否则下一个请求可能读到上一个管理员的身份
        VipAdminContext.clear();
    }
}