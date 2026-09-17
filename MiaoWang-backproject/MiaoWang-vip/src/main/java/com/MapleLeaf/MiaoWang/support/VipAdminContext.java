package com.MapleLeaf.MiaoWang.support;


import com.MapleLeaf.MiaoWang.dao.entity.VipAdminDO;

/**
 * 后台管理员上下文（ThreadLocal，请求级）
 *
 * <p>为什么不用 UserContext 那套：后台身份与 C 端身份是两套体系，共用上下文会让
 * "一个普通用户 token 进来，代码里以为自己是管理员"这类越权变成可能。物理隔开最省心。
 *
 * <p>存进 ThreadLocal 前会把密码哈希抹掉：上下文只服务于鉴权与审计，不需要哈希，
 * 少一份内存里的敏感数据就少一份泄露面。
 */
public final class VipAdminContext {

    /**
     * 后台 token 请求头（前端带这个头，不是 C 端的 Authorization）
     */
    public static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";

    private static final ThreadLocal<VipAdminDO> CURRENT_ADMIN = new ThreadLocal<>();

    private VipAdminContext() {
    }

    public static void set(VipAdminDO adminDO) {
        if (adminDO != null) {
            adminDO.setPasswordHash(null);
        }
        CURRENT_ADMIN.set(adminDO);
    }

    public static VipAdminDO get() {
        return CURRENT_ADMIN.get();
    }

    public static Long currentAdminId() {
        VipAdminDO adminDO = CURRENT_ADMIN.get();
        return adminDO == null ? null : adminDO.getId();
    }

    public static String currentAdminName() {
        VipAdminDO adminDO = CURRENT_ADMIN.get();
        return adminDO == null ? null : adminDO.getUsername();
    }

    public static void clear() {
        CURRENT_ADMIN.remove();
    }
}