package com.MapleLeaf.MiaoWang.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.constant.CommunityConstant;
import com.MapleLeaf.MiaoWang.dao.entity.CommunityCommentDO;
import com.MapleLeaf.MiaoWang.dao.entity.CommunityPostDO;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunityCommentMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunityPostMapper;
import com.MapleLeaf.MiaoWang.service.UsernameResolver;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户名 → uid（社区本地兜底实现，等用户服务接口就绪后改成 Feign 调用）
 * <p>
 * 兜底口径与 {@link UserInfoResolverImpl} 一致：社区的发帖/评论记录里冗余了
 * (uid, username)，所以在社区露过面的人能解析到；从没在社区出现过的用户名解析不到，
 * 这时 @提及 不报错、只是不发通知（正文里 @xxx 的文字照样保留）。
 * <p>
 * 两次 SQL 搞定整批用户名（帖子表一次、评论表一次），调用方不用担心 N+1。
 */
@Service
@RequiredArgsConstructor
public class CommunityUsernameResolver implements UsernameResolver {

    private final CommunityPostMapper postMapper;
    private final CommunityCommentMapper commentMapper;

    @Override
    public Map<String, Long> resolveUids(Collection<String> usernames) {
        if (CollUtil.isEmpty(usernames)) {
            return Collections.emptyMap();
        }
        // 去空白、去重，避免把空串带进 IN 里
        Set<String> distinctNames = usernames.stream()
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
        if (distinctNames.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> names = new ArrayList<>(distinctNames);
        Map<String, Long> uidMap = new HashMap<>(distinctNames.size());
        // 先帖子表后评论表，先命中的算数（同一个 username 的 uid 是固定的）
        collect(postMapper.selectMaps(Wrappers.<CommunityPostDO>query()
                .select("username AS username", "MAX(uid) AS uid")
                .in("username", names)
                .eq("del_flag", CommunityConstant.DEL_FLAG_NORMAL)
                .groupBy("username")), uidMap);
        collect(commentMapper.selectMaps(Wrappers.<CommunityCommentDO>query()
                .select("username AS username", "MAX(uid) AS uid")
                .in("username", names)
                .eq("del_flag", CommunityConstant.DEL_FLAG_NORMAL)
                .groupBy("username")), uidMap);
        return uidMap;
    }

    private void collect(List<Map<String, Object>> rows, Map<String, Long> uidMap) {
        for (Map<String, Object> row : rows) {
            Object username = row.get("username");
            Object uid = row.get("uid");
            if (username == null || !(uid instanceof Number)) {
                continue;
            }
            // key 统一小写：库默认 utf8mb4_general_ci 不区分大小写，
            // 写 @MapleLeaf 还是 @mapleleaf 都该解析到同一个人
            uidMap.putIfAbsent(username.toString().toLowerCase(Locale.ROOT), ((Number) uid).longValue());
        }
    }
}