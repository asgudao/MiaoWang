package com.MapleLeaf.MiaoWang.service;

import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.dao.entity.AiConversationDO;
import com.MapleLeaf.MiaoWang.dao.entity.AiMessageDO;
import com.MapleLeaf.MiaoWang.dao.mapper.AiConversationMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.AiMessageMapper;
import com.MapleLeaf.MiaoWang.dto.resp.AiConversationRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.AiMessageRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.RagSourceRespDTO;
import com.MapleLeaf.MiaoWang.enums.AiErrorCodeEnum;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * AI 会话与消息服务 —— 多轮对话、历史列表、答案反馈都落在这一层。
 *
 * <h3>一、会话是怎么产生的（首句即建会话）</h3>
 * 提问时若 {@code conversationId} 为空，就<b>自动新建一个会话</b>，标题取首条提问的前 20 字。
 * 前端点"新对话"只需要清掉本地的 conversationId，不必先调一次建会话接口 —— 少一次往返，
 * 也避免"建了会话却没提问"留下一堆空会话。
 *
 * <h3>二、多轮上下文怎么给模型</h3>
 * 不是把历史拼进一个字符串，而是还原成大模型标准的 messages 数组：
 * <pre>
 *   [System 人设] [历史第1轮 提问] [历史第1轮 回答] ... [本轮提问]
 * </pre>
 * 见 {@link #historyForPrompt}：从最近往回取最多 6 轮、总字符不超过 2000，
 * 这是最朴素的 token 预算裁剪（超了就把更早的丢掉）。
 *
 * <h3>三、并发安全的一个细节</h3>
 * 会话的 {@code message_count} 用 SQL 里的 {@code message_count = message_count + 1} 自增，
 * 而不是"查出来 +1 再写回" —— 后者在并发提问时会互相覆盖（丢失更新）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiConversationService {

    /** 消息角色：1=用户提问 2=AI回答，与 miaowang_ai_message.role 对齐 */
    public static final int ROLE_USER = 1;
    public static final int ROLE_AI = 2;

    /** 反馈：1=👍 2=👎 */
    public static final int FEEDBACK_UP = 1;
    public static final int FEEDBACK_DOWN = 2;

    /** 标题取提问的前 N 个字符 */
    private static final int TITLE_MAX = 20;

    /** 带进提示词的历史轮数上限（1 轮 = 问 + 答） */
    private static final int HISTORY_TURNS = 6;

    /** 历史字符预算：累计超过就丢更早的消息（中文 1 字 ≈ 1~1.5 token，2000 字约 2~3k token） */
    private static final int HISTORY_CHAR_BUDGET = 2000;

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    // ==================== 会话 ====================

    /**
     * 确定本次提问落在哪个会话：传了 conversationId 就校验归属，没传就新建。
     *
     * @return 会话ID
     */
    public Long resolveConversation(Long uid, Long conversationId, String question) {
        if (conversationId == null) {
            return create(uid, question);
        }
        requireOwned(uid, conversationId);
        return conversationId;
    }

    /**
     * 新建会话（标题 = 提问前 20 字）
     */
    public Long create(Long uid, String question) {
        AiConversationDO conversation = new AiConversationDO();
        conversation.setUid(uid);
        conversation.setTitle(buildTitle(question));
        conversation.setMessageCount(0);
        conversationMapper.insert(conversation);
        return conversation.getId();
    }

    /**
     * 校验会话属于当前用户。
     * <p>
     * 越权和不存在返回<b>同一个</b>错误码：如果"无权访问"单独报错，
     * 等于告诉攻击者"这个会话ID真实存在"，白送一个探测接口。
     */
    public AiConversationDO requireOwned(Long uid, Long conversationId) {
        AiConversationDO conversation = conversationMapper.selectOne(new QueryWrapper<AiConversationDO>()
                .eq("id", conversationId)
                .eq("uid", uid)
                .eq("del_flag", 0));
        if (conversation == null) {
            throw new ClientException(AiErrorCodeEnum.AI_CONVERSATION_NOT_FOUND);
        }
        return conversation;
    }

    /**
     * 历史会话列表（按最后活跃时间倒序，没消息的新会话排最后）
     */
    public List<AiConversationRespDTO> list(Long uid, Integer limit) {
        int size = normalizeLimit(limit, 20, 100);
        List<AiConversationDO> rows = conversationMapper.selectList(new QueryWrapper<AiConversationDO>()
                .eq("uid", uid)
                .eq("del_flag", 0)
                .orderByDesc("last_message_time", "id")
                .last("LIMIT " + size));
        return rows.stream().map(AiConversationRespDTO::of).toList();
    }

    /**
     * 删除会话：会话软删，它下面的消息一起软删。
     * 只删会话不删消息的话，历史接口仍能查到"已删除会话"的消息内容。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long uid, Long conversationId) {
        requireOwned(uid, conversationId);
        AiConversationDO update = new AiConversationDO();
        update.setId(conversationId);
        update.setDelFlag(1);
        conversationMapper.updateById(update);

        AiMessageDO messageUpdate = new AiMessageDO();
        messageUpdate.setDelFlag(1);
        messageMapper.update(messageUpdate, new QueryWrapper<AiMessageDO>()
                .eq("conversation_id", conversationId));
        log.info("用户 {} 删除了会话 {}（消息一并软删）", uid, conversationId);
    }

    // ==================== 消息 ====================

    /**
     * 写入用户提问
     */
    public Long appendUserMessage(Long uid, Long conversationId, String question) {
        AiMessageDO message = new AiMessageDO();
        message.setConversationId(conversationId);
        message.setUid(uid);
        message.setRole(ROLE_USER);
        message.setContent(question);
        messageMapper.insert(message);
        touchConversation(conversationId);
        return message.getId();
    }

    /**
     * 写入 AI 回答（含引用来源、token 用量、耗时）
     *
     * @param usage 可能为 null（模型没返回用量），此时 token 列为 NULL，不影响答案落库
     */
    public Long appendAssistantMessage(Long uid, Long conversationId, String answer,
                                       List<RagSourceRespDTO> sources, boolean hasKnowledge,
                                       Usage usage, long latencyMs) {
        AiMessageDO message = new AiMessageDO();
        message.setConversationId(conversationId);
        message.setUid(uid);
        message.setRole(ROLE_AI);
        message.setContent(answer);
        message.setSourcesJson(writeSources(sources));
        message.setHasKnowledge(hasKnowledge ? 1 : 0);
        if (usage != null) {
            message.setTokenIn(usage.getPromptTokens());
            message.setTokenOut(usage.getCompletionTokens());
        }
        message.setLatencyMs((int) Math.min(Math.max(latencyMs, 0), Integer.MAX_VALUE));
        messageMapper.insert(message);
        touchConversation(conversationId);
        return message.getId();
    }

    /**
     * 取"拼提示词用"的历史消息（<b>不含本轮提问</b>）：时间正序，最多 6 轮 + 2000 字预算。
     * <p>
     * 实现上是先按 id 倒序取最近 12 条，再从最新往回累计字符，超预算就停，最后翻回正序。
     * 这样保证"最近的对话一定在"，而不是从头截断导致最近的上下文丢失。
     */
    public List<AiMessageDO> historyForPrompt(Long conversationId) {
        List<AiMessageDO> rows = messageMapper.selectList(new QueryWrapper<AiMessageDO>()
                .eq("conversation_id", conversationId)
                .eq("del_flag", 0)
                .orderByDesc("id")
                .last("LIMIT " + (HISTORY_TURNS * 2)));
        List<AiMessageDO> picked = new ArrayList<>();
        int chars = 0;
        for (AiMessageDO row : rows) {
            int length = row.getContent() == null ? 0 : row.getContent().length();
            // 第一条无论多长都留着：否则一条超长消息会把历史清空
            if (!picked.isEmpty() && chars + length > HISTORY_CHAR_BUDGET) {
                break;
            }
            picked.add(row);
            chars += length;
        }
        Collections.reverse(picked);
        return picked;
    }

    /**
     * 会话消息历史（给前端回放，时间正序）
     */
    public List<AiMessageRespDTO> history(Long uid, Long conversationId, Integer limit) {
        requireOwned(uid, conversationId);
        int size = normalizeLimit(limit, 50, 200);
        List<AiMessageDO> rows = messageMapper.selectList(new QueryWrapper<AiMessageDO>()
                .eq("conversation_id", conversationId)
                .eq("del_flag", 0)
                .orderByDesc("id")
                .last("LIMIT " + size));
        // 倒序查是为了取"最近 N 条"，给前端要翻回时间正序
        List<AiMessageRespDTO> ordered = new ArrayList<>(rows.stream().map(this::toResp).toList());
        Collections.reverse(ordered);
        return ordered;
    }

    /**
     * 答案反馈：👍/👎。只能评价自己的、且是 AI 回答的消息。
     * <p>
     * 反馈与"重新生成"是配套的：踩了之后前端可以直接重发同一个问题（带上同一个 conversationId），
     * 后端不需要额外的重新生成接口 —— 旧的回答留在历史里，新的作为新的一轮追加。
     */
    public void feedback(Long uid, Long messageId, Integer type) {
        if (type == null || (type != FEEDBACK_UP && type != FEEDBACK_DOWN)) {
            throw new ClientException(AiErrorCodeEnum.AI_FEEDBACK_TYPE_INVALID);
        }
        AiMessageDO message = messageMapper.selectOne(new QueryWrapper<AiMessageDO>()
                .eq("id", messageId)
                .eq("uid", uid)
                .eq("del_flag", 0));
        // 只允许评价 AI 的回答：给用户自己的提问点赞没有意义
        if (message == null || message.getRole() == null || message.getRole() != ROLE_AI) {
            throw new ClientException(AiErrorCodeEnum.AI_MESSAGE_NOT_FOUND);
        }
        AiMessageDO update = new AiMessageDO();
        update.setId(messageId);
        update.setFeedback(type);
        update.setFeedbackTime(new Date());
        messageMapper.updateById(update);
    }

    // ==================== 内部工具 ====================

    /**
     * 会话的"最后活跃时间 + 条数"自增。
     * 用 SQL 表达式自增而不是读-改-写：读-改-写在并发提问时会丢掉另一次的自增。
     */
    private void touchConversation(Long conversationId) {
        conversationMapper.update(null, new UpdateWrapper<AiConversationDO>()
                .setSql("message_count = message_count + 1")
                .set("last_message_time", new Date())
                .eq("id", conversationId));
    }

    private AiMessageRespDTO toResp(AiMessageDO row) {
        AiMessageRespDTO resp = new AiMessageRespDTO();
        resp.setId(row.getId());
        resp.setRole(row.getRole());
        resp.setContent(row.getContent());
        resp.setHasKnowledge(row.getHasKnowledge() != null && row.getHasKnowledge() == 1);
        resp.setSources(readSources(row.getSourcesJson()));
        resp.setFeedback(row.getFeedback());
        resp.setCreateTime(row.getCreateTime());
        return resp;
    }

    /**
     * 来源列表 → JSON 存库。
     * 序列化失败不让整次问答失败：来源没了，答案还在，记个 warn 就行。
     */
    private String writeSources(List<RagSourceRespDTO> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (JsonProcessingException e) {
            log.warn("来源序列化失败，本次不落库来源：{}", e.getMessage());
            return null;
        }
    }

    private List<RagSourceRespDTO> readSources(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<RagSourceRespDTO>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("来源反序列化失败，历史里省略来源：{}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 标题：去掉换行、截前 20 字。列表页只显示标题，不拉正文。
     */
    private static String buildTitle(String question) {
        if (question == null) {
            return "";
        }
        String flat = question.replaceAll("\\s+", " ").trim();
        return flat.length() <= TITLE_MAX ? flat : flat.substring(0, TITLE_MAX);
    }

    private static int normalizeLimit(Integer limit, int defaultValue, int max) {
        if (limit == null || limit <= 0) {
            return defaultValue;
        }
        return Math.min(limit, max);
    }
}