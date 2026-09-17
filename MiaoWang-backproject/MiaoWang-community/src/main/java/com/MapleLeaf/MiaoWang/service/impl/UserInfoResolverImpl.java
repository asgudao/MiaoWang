package com.MapleLeaf.MiaoWang.service.impl;

import com.MapleLeaf.MiaoWang.constant.CommunityConstant;
import com.MapleLeaf.MiaoWang.dao.entity.CommunityPostDO;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunityPostMapper;
import com.MapleLeaf.MiaoWang.service.UserInfoResolver;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * uid → 昵称 解析器（临时兜底实现，等用户服务接口就绪后改成 Feign 调用）
 * <p>
 * 兜底思路：社区的发帖记录里冗余了 (uid, username)，发过帖的人就能解析出昵称；
 * 从没在社区出现过的人解析不到，昵称返回 null，前端按 uid 兜底展示。
 * <p>
 * 一次 SQL 查完整批 uid（GROUP BY uid），不是每个 uid 查一次，调用方不用担心 N+1。
 */
@Service
@RequiredArgsConstructor
public class UserInfoResolverImpl implements UserInfoResolver {

    private final CommunityPostMapper postMapper;

    @Override
    public Map<Long, String> resolveUsernames(Collection<Long> uids) {
        if (uids == null || uids.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> distinctUids = new HashSet<>(uids);
        // MAX(username) 只是为了让 GROUP BY 只出一行（同一个 uid 的昵称正常只有一份，
        // 改过昵称的话这里取字典序最大的那个，仅影响展示，不影响任何归属判断）
        List<Map<String, Object>> rows = postMapper.selectMaps(Wrappers.<CommunityPostDO>query()
                .select("uid AS uid", "MAX(username) AS username")
                .in("uid", distinctUids)
                .eq("del_flag", CommunityConstant.DEL_FLAG_NORMAL)
                .groupBy("uid"));
        Map<Long, String> usernameMap = new HashMap<>(distinctUids.size());
        for (Map<String, Object> row : rows) {
            Object uid = row.get("uid");
            Object username = row.get("username");
            if (uid instanceof Number && username != null) {
                usernameMap.put(((Number) uid).longValue(), username.toString());
            }
        }
        return usernameMap;
    }
}