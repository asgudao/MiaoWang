package com.MapleLeaf.MiaoWang.support;


import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.biz.user.UserContext;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;

/**
 * 当前登录用户工具类
 * 统一从 UserContext 取身份（由 common 的 UserTransmitFilter 依据 header 还原），
 * 避免每个 Service 各写一遍解析与判空逻辑
 */
public final class VipUserHelper {

    private VipUserHelper() {
    }

    /**
     * 取当前登录用户ID，未登录直接抛客户端异常
     */
    public static Long currentUid() {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(userId)) {
            throw new ClientException("用户未登录或登录已过期");
        }
        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException e) {
            throw new ClientException("用户身份解析失败，请重新登录");
        }
    }

    /**
     * 取当前登录用户名（可能为 null，仅用于冗余存储，不参与鉴权）
     */
    public static String currentUsername() {
        return UserContext.getUsername();
    }
}