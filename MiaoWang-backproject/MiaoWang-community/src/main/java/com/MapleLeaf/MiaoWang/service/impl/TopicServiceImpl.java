package com.MapleLeaf.MiaoWang.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.constant.CommunityConstant;
import com.MapleLeaf.MiaoWang.dao.entity.CommunityPostTopicDO;
import com.MapleLeaf.MiaoWang.dao.entity.CommunityTopicDO;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunityPostTopicMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunityTopicMapper;
import com.MapleLeaf.MiaoWang.dto.resp.TopicRespDTO;
import com.MapleLeaf.MiaoWang.enums.CommunityErrorCodeEnum;
import com.MapleLeaf.MiaoWang.service.TopicService;
import com.MapleLeaf.MiaoWang.support.ContentTagParser;
import com.MapleLeaf.MiaoWang.support.SqlLikeUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 社区话题实现
 * <p>
 * 计数口径：post_count 只在"关联行真的插进去了 / 真的删掉了"时动。
 * 所以同一帖里重复写同一个话题不会把计数刷高（uk_post_topic 挡住重复插入），
 * 编辑帖子时也只对"新增 / 移除"的话题动差量，还在的话题计数保持不变。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TopicServiceImpl extends ServiceImpl<CommunityTopicMapper, CommunityTopicDO> implements TopicService {

    private final CommunityPostTopicMapper postTopicMapper;

    @Override
    public void syncPostTopics(Long postId, String title, String content) {
        if (postId == null) {
            return;
        }
        for (String name : ContentTagParser.parseTopics(title, content)) {
            Long topicId = resolveTopicId(name);
            if (topicId == null || !insertRelation(postId, topicId)) {
                continue;
            }
            addPostCount(topicId, 1);
        }
    }

    @Override
    public void replacePostTopics(Long postId, String title, String content) {
        if (postId == null) {
            return;
        }
        Set<Long> oldTopicIds = selectTopicIdsByPostId(postId);
        if (CollUtil.isNotEmpty(oldTopicIds)) {
            postTopicMapper.delete(Wrappers.lambdaQuery(CommunityPostTopicDO.class)
                    .eq(CommunityPostTopicDO::getPostId, postId));
        }
        Set<Long> newTopicIds = new LinkedHashSet<>();
        for (String name : ContentTagParser.parseTopics(title, content)) {
            Long topicId = resolveTopicId(name);
            if (topicId == null || !newTopicIds.add(topicId)) {
                continue;
            }
            insertRelation(postId, topicId);
        }
        // 计数只动差量：还在的话题不动，否则编辑一次热门榜就抖一次
        for (Long topicId : newTopicIds) {
            if (!oldTopicIds.contains(topicId)) {
                addPostCount(topicId, 1);
            }
        }
        for (Long topicId : oldTopicIds) {
            if (!newTopicIds.contains(topicId)) {
                addPostCount(topicId, -1);
            }
        }
    }

    @Override
    public void removePostTopics(Long postId) {
        if (postId == null) {
            return;
        }
        Set<Long> topicIds = selectTopicIdsByPostId(postId);
        if (CollUtil.isEmpty(topicIds)) {
            return;
        }
        postTopicMapper.delete(Wrappers.lambdaQuery(CommunityPostTopicDO.class)
                .eq(CommunityPostTopicDO::getPostId, postId));
        // 一条帖子对同一个话题只可能有一行（唯一键），所以按话题去重后各 -1
        for (Long topicId : topicIds) {
            addPostCount(topicId, -1);
        }
    }

    @Override
    public Map<Long, List<TopicRespDTO>> listTopicsByPostIds(Collection<Long> postIds) {
        if (CollUtil.isEmpty(postIds)) {
            return Collections.emptyMap();
        }
        // 两次 SQL 搞定整页：先查关联（按 id 升序 = 正文出现顺序），再批量查话题
        List<CommunityPostTopicDO> relations = postTopicMapper.selectList(Wrappers.lambdaQuery(CommunityPostTopicDO.class)
                .in(CommunityPostTopicDO::getPostId, postIds)
                .eq(CommunityPostTopicDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL)
                .orderByAsc(CommunityPostTopicDO::getId));
        if (CollUtil.isEmpty(relations)) {
            return Collections.emptyMap();
        }
        Set<Long> topicIds = relations.stream().map(CommunityPostTopicDO::getTopicId).collect(Collectors.toSet());
        Map<Long, CommunityTopicDO> topicMap = baseMapper.selectList(Wrappers.lambdaQuery(CommunityTopicDO.class)
                        .in(CommunityTopicDO::getTopicId, topicIds)
                        .eq(CommunityTopicDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL))
                .stream()
                .collect(Collectors.toMap(CommunityTopicDO::getTopicId, topic -> topic, (existing, replacement) -> existing));
        Map<Long, List<TopicRespDTO>> result = new LinkedHashMap<>();
        for (CommunityPostTopicDO relation : relations) {
            CommunityTopicDO topicDO = topicMap.get(relation.getTopicId());
            if (topicDO == null) {
                continue;
            }
            result.computeIfAbsent(relation.getPostId(), key -> new ArrayList<>()).add(toResp(topicDO));
        }
        return result;
    }

    @Override
    public List<TopicRespDTO> hotTopics(Long size) {
        long limit = normalizeSize(size);
        // 并列时按 topic_id 倒序（新话题在前），保证同一份数据每次返回的顺序都一样
        return baseMapper.selectList(Wrappers.lambdaQuery(CommunityTopicDO.class)
                        .eq(CommunityTopicDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL)
                        .orderByDesc(CommunityTopicDO::getPostCount)
                        .orderByDesc(CommunityTopicDO::getTopicId)
                        .last("LIMIT " + limit))
                .stream().map(this::toResp).collect(Collectors.toList());
    }

    @Override
    public List<TopicRespDTO> searchTopics(String keyword, Long size) {
        String trimmed = StrUtil.trim(keyword);
        if (StrUtil.isBlank(trimmed) || trimmed.length() > CommunityConstant.TOPIC_NAME_MAX_LENGTH) {
            throw new ClientException(CommunityErrorCodeEnum.SEARCH_KEYWORD_INVALID);
        }
        long limit = normalizeSize(size);
        // 前缀匹配（likeRight）能用上 uk_name 索引，不做 %kw% 全表扫；
        // 关键词里的 % / _ 先转义，否则搜 "%" 会把所有话题都捞出来
        return baseMapper.selectList(Wrappers.lambdaQuery(CommunityTopicDO.class)
                        .eq(CommunityTopicDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL)
                        .likeRight(CommunityTopicDO::getName, SqlLikeUtil.escape(trimmed))
                        .orderByDesc(CommunityTopicDO::getPostCount)
                        .orderByDesc(CommunityTopicDO::getTopicId)
                        .last("LIMIT " + limit))
                .stream().map(this::toResp).collect(Collectors.toList());
    }

    @Override
    public TopicRespDTO getTopic(Long topicId) {
        if (topicId == null) {
            throw new ClientException(CommunityErrorCodeEnum.TOPIC_NOT_EXIST);
        }
        CommunityTopicDO topicDO = baseMapper.selectOne(Wrappers.lambdaQuery(CommunityTopicDO.class)
                .eq(CommunityTopicDO::getTopicId, topicId)
                .eq(CommunityTopicDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL));
        if (topicDO == null) {
            throw new ClientException(CommunityErrorCodeEnum.TOPIC_NOT_EXIST);
        }
        return toResp(topicDO);
    }

    /**
     * 按名字拿话题 id，没有就顺手建一个
     * <p>
     * 并发下两个请求可能同时发现"话题不存在"，这时靠 uk_name 兜底：谁先插进去谁赢，
     * 输的那个捕获唯一键冲突后把现成的话题查回来。
     */
    private Long resolveTopicId(String name) {
        if (StrUtil.isBlank(name)) {
            return null;
        }
        CommunityTopicDO exist = baseMapper.selectOne(Wrappers.lambdaQuery(CommunityTopicDO.class)
                .eq(CommunityTopicDO::getName, name)
                .eq(CommunityTopicDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL));
        if (exist != null) {
            return exist.getTopicId();
        }
        CommunityTopicDO topicDO = new CommunityTopicDO();
        topicDO.setName(name);
        topicDO.setPostCount(0);
        try {
            baseMapper.insert(topicDO);
            return topicDO.getTopicId();
        } catch (DuplicateKeyException e) {
            log.debug("话题已被并发创建，改用现成的, name={}", name);
            CommunityTopicDO created = baseMapper.selectOne(Wrappers.lambdaQuery(CommunityTopicDO.class)
                    .eq(CommunityTopicDO::getName, name)
                    .eq(CommunityTopicDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL));
            return created == null ? null : created.getTopicId();
        }
    }

    /**
     * 挂关联；返回"是不是真的插进去了"——重复挂同一话题返回 false，计数不能跟着动
     */
    private boolean insertRelation(Long postId, Long topicId) {
        CommunityPostTopicDO relation = new CommunityPostTopicDO();
        relation.setPostId(postId);
        relation.setTopicId(topicId);
        try {
            postTopicMapper.insert(relation);
            return true;
        } catch (DuplicateKeyException e) {
            log.debug("帖子已经挂过这个话题，跳过, postId={}, topicId={}", postId, topicId);
            return false;
        }
    }

    /**
     * 原子自增 / 自减：交给数据库算，不用"读出来 +1 再写回去"
     */
    private void addPostCount(Long topicId, int delta) {
        baseMapper.update(null, Wrappers.lambdaUpdate(CommunityTopicDO.class)
                .setSql(delta > 0 ? "post_count = post_count + 1" : "post_count = post_count - 1")
                .eq(CommunityTopicDO::getTopicId, topicId));
    }

    private Set<Long> selectTopicIdsByPostId(Long postId) {
        return postTopicMapper.selectList(Wrappers.lambdaQuery(CommunityPostTopicDO.class)
                        .eq(CommunityPostTopicDO::getPostId, postId)
                        .eq(CommunityPostTopicDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL))
                .stream().map(CommunityPostTopicDO::getTopicId).collect(Collectors.toSet());
    }

    private TopicRespDTO toResp(CommunityTopicDO topicDO) {
        TopicRespDTO respDTO = new TopicRespDTO();
        respDTO.setTopicId(topicDO.getTopicId());
        respDTO.setName(topicDO.getName());
        respDTO.setPostCount(topicDO.getPostCount());
        return respDTO;
    }

    private long normalizeSize(Long size) {
        if (size == null || size < 1) {
            return CommunityConstant.DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, CommunityConstant.MAX_PAGE_SIZE);
    }
}