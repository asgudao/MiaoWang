package com.MapleLeaf.MiaoWang.service;

import java.util.Collection;
import java.util.Map;

/**
 * 用户名 → uid 解析（@提及用）
 * <p>
 * 与 {@link UserInfoResolver} 正好是反方向：那个是 uid → 昵称，这个是昵称 → uid。
 * 当前是社区本地兜底实现（见 CommunityUsernameResolver），等用户服务给出
 * "按用户名查 uid"的接口后换成 Feign 实现即可，调用方一行都不用动。
 */
public interface UsernameResolver {

    /**
     * 批量解析：一次 SQL 查完整批用户名，不做逐个查询
     *
     * @param usernames 用户名集合（忽略大小写）
     * @return key 统一为小写（Locale.ROOT）的用户名 → uid；查不到的用户名不会出现在结果里
     */
    Map<String, Long> resolveUids(Collection<String> usernames);
}