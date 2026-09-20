package com.MapleLeaf.MiaoWang.service;

import com.MapleLeaf.MiaoWang.config.AiPetProperties;
import com.MapleLeaf.MiaoWang.dao.entity.AiPetConversationDO;
import com.MapleLeaf.MiaoWang.dao.entity.AiPetMessageDO;
import com.MapleLeaf.MiaoWang.dao.mapper.AiPetConversationMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.AiPetMessageMapper;
import com.MapleLeaf.MiaoWang.dto.resp.AiPetMessageRespDTO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 电子宠物会话与消息服务 —— 多轮记忆、历史回放都落在这一层。
 *
 * <h3>一、一只宠物一条会话（这是与 ai-knowledge 最大的不同）</h3>
 * 知识问答是话题制（一次提问一条会话、有历史列表）；宠物是陪伴关系，
 * 主人和同一只宠物之间只该有一条连续的记忆线，所以按 {@code (uid, pid)} 唯一。
 * 好处是删掉了"会话列表"和"新建对话"两套 UI 与接口，语义还更准。
 *
 * <h3>二、并发下的"首次同时开口"</h3>
 * 首页自动问候与用户点进对话页可能几乎同时发生，两个请求都会发现"还没有会话"并尝试新建。
 * 处理方式是<b>让数据库来保证唯一</b>：表上有 {@code uk_uid_pid} 唯一索引，
 * 后到的那个 insert 会抛 {@link DuplicateKeyException}，捕获后改成查询即可。
 * 比"先 select 再 insert"或"用分布式锁"都更简单，而且少一次锁租约的成本。
 * 注意：这个方法<b>不能加 @Transactional</b> —— 一旦在事务里，捕获唯一键冲突后事务已被标记
 * rollback-only，后续那条 select 也活不下来。
 *
 * <h3>三、message_count 用 SQL 自增</h3>
 * {@code message_count = message_count + 1} 而不是"查出来 +1 再写回"：
 * 后者在并发聊天时会互相覆盖（丢失更新）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPetConversationService {

    /** 会话还没消息时的兜底名字 */
    private static final String UNKNOWN_PET = "";

    private final AiPetConversationMapper conversationMapper;
    private final AiPetMessageMapper messageMapper;
    private final AiPetProperties aiPetProperties;

    /**
     * 取（或建）一只宠物的会话
     *
     * @param petName 宠物名快照，每次调用都会刷新（宠物改名后后台仍能看出聊的是谁）
     */
    public Long getOrCreate(Long uid, Long pid, String petName) {
        AiPetConversationDO existing = selectOne(uid, pid);
        if (existing != null) {
            refreshPetName(existing, petName);
            return existing.getId();
        }
        AiPetConversationDO conversation = new AiPetConversationDO();
        conversation.setUid(uid);
        conversation.setPid(pid);
        conversation.setPetName(petName == null ? UNKNOWN_PET : petName);
        conversation.setMessageCount(0);
        try {
            conversationMapper.insert(conversation);
            return conversation.getId();
        } catch (DuplicateKeyException e) {
            // 并发下另一个请求已经建好了：唯一索引挡住了重复插入，这里改成读它建好的那条
            log.info("会话并发创建被唯一索引拦下，改为复用已有会话：uid={}, pid={}", uid, pid);
            AiPetConversationDO winner = selectOne(uid, pid);
            if (winner == null) {
                // 极端情况：对方刚建好又被删了。重抛，让上层看到真实原因而不是一个空指针
                throw e;
            }
            return winner.getId();
        }
    }

    /**
     * 会话里的历史消息（给前端回放，时间正序）
     *
     * <p>{@code pid} 不传时的口径：<b>最近聊过的那只宠物</b>（按 {@code last_message_time} 取最新的会话），
     * 一条会话都没有就返回空数组。
     * <p>为什么这里不跟 chat / greet 一样默认"第一只宠物"：那两个接口要先调 pet 服务拿档案
     * （默认宠物是档案列表的第一条），而"回放历史"完全可以只查库 ——
     * 对"看看我们最近聊了什么"这个动作来说，最近聊过的那只也确实是更合适的默认值。
     * 前端带 {@code pid} 时就是明确指定，不存在歧义。
     */
    public List<AiPetMessageRespDTO> history(Long uid, Long pid, Integer limit) {
        int size = normalizeLimit(limit, 50, 200);
        AiPetConversationDO conversation = pid == null ? selectLatest(uid) : selectOne(uid, pid);
        if (conversation == null) {
            // 还没聊过不是错误：返回空列表，前端显示"还没说过话"的引导态
            return List.of();
        }
        List<AiPetMessageDO> rows = messageMapper.selectList(new QueryWrapper<AiPetMessageDO>()
                .eq("conversation_id", conversation.getId())
                .eq("del_flag", 0)
                .orderByDesc("id")
                .last("LIMIT " + size));
        // 倒序查是为了取"最近 N 条"，给前端要翻回时间正序
        List<AiPetMessageRespDTO> ordered = new ArrayList<>(rows.stream().map(AiPetMessageRespDTO::of).toList());
        Collections.reverse(ordered);
        return ordered;
    }

    /**
     * 取"拼提示词用"的历史消息（<b>不含本轮提问</b>）：时间正序，最多 N 轮 + 字符预算。
     *
     * <p>实现是先按 id 倒序取最近 {@code turns * 2} 条，再从最新往回累计字符，超预算就停，最后翻回正序。
     * 这样保证"最近的对话一定在"，而不是从头截断导致最近的上下文丢失。
     * 自动问候的消息也在里面（它就是宠物说过的话），所以问候会成为后续对话的自然上下文。
     */
    public List<AiPetMessageDO> historyForPrompt(Long conversationId) {
        int turns = Math.max(aiPetProperties.getHistoryTurns() == null ? 6 : aiPetProperties.getHistoryTurns(), 1);
        int budget = aiPetProperties.getHistoryCharBudget() == null ? 2000 : aiPetProperties.getHistoryCharBudget();
        List<AiPetMessageDO> rows = messageMapper.selectList(new QueryWrapper<AiPetMessageDO>()
                .eq("conversation_id", conversationId)
                .eq("del_flag", 0)
                .orderByDesc("id")
                .last("LIMIT " + (turns * 2)));
        List<AiPetMessageDO> picked = new ArrayList<>();
        int chars = 0;
        for (AiPetMessageDO row : rows) {
            int length = row.getContent() == null ? 0 : row.getContent().length();
            // 第一条无论多长都留着：否则一条超长消息会把历史直接清空
            if (!picked.isEmpty() && chars + length > budget) {
                break;
            }
            picked.add(row);
            chars += length;
        }
        Collections.reverse(picked);
        return picked;
    }

    /**
     * 最近一条"宠物说过的话"，且不早于 {@code after}；没有就返回 null。
     *
     * <p>给自动问候用：如果这个时段里宠物已经开过口（比如主人刚聊完又回到首页），
     * 就复用那句话，而不是再生成一条新的。这是"一个时段只说一次"的第二道闸门
     * （第一道是 Redis 缓存，见 {@code GreetingService}）。
     */
    public AiPetMessageDO latestPetMessage(Long conversationId, Date after) {
        QueryWrapper<AiPetMessageDO> query = new QueryWrapper<AiPetMessageDO>()
                .eq("conversation_id", conversationId)
                .eq("del_flag", 0)
                .eq("role", AiPetMessageDO.ROLE_PET)
                .orderByDesc("id")
                .last("LIMIT 1");
        if (after != null) {
            query.ge("create_time", after);
        }
        return messageMapper.selectOne(query);
    }

    /**
     * 写入主人的话
     */
    public Long appendOwnerMessage(Long uid, Long pid, Long conversationId, String content) {
        AiPetMessageDO message = new AiPetMessageDO();
        message.setConversationId(conversationId);
        message.setUid(uid);
        message.setPid(pid);
        message.setRole(AiPetMessageDO.ROLE_OWNER);
        message.setKind(AiPetMessageDO.KIND_CHAT);
        message.setContent(content);
        messageMapper.insert(message);
        touch(conversationId);
        return message.getId();
    }

    /**
     * 写入宠物说的话（一问一答的回答，或主动问候）
     *
     * @param kind  0=一问一答 1=自动问候
     * @param usage 可能为 null（模型没返回用量），此时 token 列留 NULL，不影响消息落库
     */
    public Long appendPetMessage(Long uid, Long pid, Long conversationId, String content, int kind,
                                 Usage usage, long latencyMs) {
        AiPetMessageDO message = new AiPetMessageDO();
        message.setConversationId(conversationId);
        message.setUid(uid);
        message.setPid(pid);
        message.setRole(AiPetMessageDO.ROLE_PET);
        message.setKind(kind);
        message.setContent(content);
        if (usage != null) {
            message.setTokenIn(usage.getPromptTokens());
            message.setTokenOut(usage.getCompletionTokens());
        }
        message.setLatencyMs((int) Math.min(Math.max(latencyMs, 0), Integer.MAX_VALUE));
        messageMapper.insert(message);
        touch(conversationId);
        return message.getId();
    }

    /**
     * 宠物改名后刷新会话上的名字快照；没变就不写库
     */
    private void refreshPetName(AiPetConversationDO conversation, String petName) {
        if (petName == null || petName.isBlank() || petName.equals(conversation.getPetName())) {
            return;
        }
        conversationMapper.update(null, new UpdateWrapper<AiPetConversationDO>()
                .set("pet_name", petName)
                .eq("id", conversation.getId()));
    }

    private AiPetConversationDO selectOne(Long uid, Long pid) {
        return conversationMapper.selectOne(new QueryWrapper<AiPetConversationDO>()
                .eq("uid", uid)
                .eq("pid", pid)
                .eq("del_flag", 0));
    }

    /**
     * 该用户"最近聊过的那只宠物"的会话（还没聊过时返回 null）
     */
    private AiPetConversationDO selectLatest(Long uid) {
        return conversationMapper.selectOne(new QueryWrapper<AiPetConversationDO>()
                .eq("uid", uid)
                .eq("del_flag", 0)
                .isNotNull("last_message_time")
                .orderByDesc("last_message_time")
                .last("LIMIT 1"));
    }

    /**
     * 会话的"最后活跃时间 + 条数"自增。用 SQL 表达式自增而不是读-改-写，避免并发丢失更新。
     */
    private void touch(Long conversationId) {
        conversationMapper.update(null, new UpdateWrapper<AiPetConversationDO>()
                .setSql("message_count = message_count + 1")
                .set("last_message_time", new Date())
                .eq("id", conversationId));
    }

    private static int normalizeLimit(Integer limit, int defaultValue, int max) {
        if (limit == null || limit <= 0) {
            return defaultValue;
        }
        return Math.min(limit, max);
    }
}