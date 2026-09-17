package com.MapleLeaf.MiaoWang.support;

import com.MapleLeaf.MiaoWang.common.biz.user.UserContext;
import com.MapleLeaf.MiaoWang.enums.AiErrorCodeEnum;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import org.springframework.stereotype.Component;

/**
 * 取当前登录用户ID。
 *
 * <p>身份来源：common 的 {@code UserTransmitFilter} 依据请求头 {@code username} + {@code token}
 * 还原出的登录态（{@link UserContext}）。
 *
 * <p>为什么不直接复用 {@code RagQuotaGuard.currentUid()}：那个类管的是"额度"，
 * 让会话/消息接口去调它会把两件事绑在一起（将来额度逻辑变了会误伤会话接口）。
 * 这里只做"身份"这一件事，两种取值语义分开：
 * <ul>
 *   <li>{@link #require()}：未登录直接抛 {@code B000701}</li>
 *   <li>{@link #orNull()}：未登录返回 null（运维类接口用，比如重建索引时顺手记 uid）</li>
 * </ul>
 */
@Component
public class CurrentUid {

    /**
     * 取当前登录用户ID，未登录抛 B000701
     */
    public Long require() {
        Long uid = orNull();
        if (uid == null) {
            throw new ClientException(AiErrorCodeEnum.AI_NOT_LOGIN);
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