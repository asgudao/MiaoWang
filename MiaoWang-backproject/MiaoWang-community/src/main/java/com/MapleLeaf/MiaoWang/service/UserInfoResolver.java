package com.MapleLeaf.MiaoWang.service;

import java.util.Collection;
import java.util.Map;

/**
 * uid → 昵称 解析器
 * <p>
 * 关注关系、粉丝关系、通知里手里只有 uid，要显示昵称就得回查用户服务。
 * 用户服务目前只有"按 username 查"的接口（/user/{username}），没有"按 uid 批量查"，
 * 所以这里先做成一个可替换的解析入口，当前的兜底实现是查社区自己的帖子/评论里的冗余昵称。
 * <p>
 * 用户服务补上"按 uid 批量查昵称/头像"的接口之后，**只需改这个接口的实现类一处**，
 * 换成 OpenFeign 批量调用即可，调用方（关注/粉丝/通知列表）都不用动。
 */
public interface UserInfoResolver {

    /**
     * 批量解析 uid → 昵称（一次查询解决，不要每条记录调一次）
     *
     * @param uids 用户id集合
     * @return uid → 昵称；解析不到的 uid 不会出现在返回值里
     */
    Map<Long, String> resolveUsernames(Collection<Long> uids);
}