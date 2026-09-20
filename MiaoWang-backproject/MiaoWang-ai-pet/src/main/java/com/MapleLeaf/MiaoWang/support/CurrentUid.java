package com.MapleLeaf.MiaoWang.support;

import com.MapleLeaf.MiaoWang.common.biz.user.UserContext;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.enums.AiPetErrorCodeEnum;
import org.springframework.stereotype.Component;

/**
 * 取当前登录用户ID
 *
 * <p>身份来源：common 的 {@code UserTransmitFilter} 依据请求头 {@code username} + {@code token}
 * 还原出的登录态（{@link UserContext}）。
 *
 * <p>本模块所有接口都要登录：宠物档案、聊天次数、会话记忆都是按账号走的，
 * 没有 uid 就什么都做不了（也就没有"游客模式"）。
 */
@Component
public class CurrentUid {

    /**
     * 取当前登录用户ID，未登录抛 B000901
     */
    public Long require() {
        Long uid = orNull();
        if (uid == null) {
            throw new ClientException(AiPetErrorCodeEnum.PET_CHAT_NOT_LOGIN);
        }
        return uid;
    }

    /**
     * 取当前登录用户ID，未登录（或登录态里的 uid 非法）返回 null，不抛异常
     */
    public Long orNull() {
        String userId = UserContext.getUserId();
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}