package com.MapleLeaf.MiaoWang.service;

import com.MapleLeaf.MiaoWang.common.convention.exception.AbstractException;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.common.convention.exception.ServiceException;
import com.MapleLeaf.MiaoWang.config.AiPetProperties;
import com.MapleLeaf.MiaoWang.dao.entity.AiPetMessageDO;
import com.MapleLeaf.MiaoWang.dto.resp.AiPetChatRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.AiPetGreetRespDTO;
import com.MapleLeaf.MiaoWang.enums.AiPetErrorCodeEnum;
import com.MapleLeaf.MiaoWang.persona.PetPersonaFactory;
import com.MapleLeaf.MiaoWang.persona.PetProfile;
import com.MapleLeaf.MiaoWang.persona.PetProfileLoader;
import com.MapleLeaf.MiaoWang.persona.TimeSlot;
import com.MapleLeaf.MiaoWang.support.AiCallLogRecorder;
import com.MapleLeaf.MiaoWang.support.PetChatQuotaGuard;
import com.MapleLeaf.MiaoWang.support.TokenUsageExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 电子宠物对话服务 —— 本模块的主流程都在这个类里。
 *
 * <h3>一、"宠物知道自己是谁"是怎么实现的（不做 RAG、不做微调）</h3>
 * <pre>
 *   pet 服务（宠物档案）───┐
 *                          ├──► PetProfileLoader 翻译成人话 ──► PetPersonaFactory 拼成系统提示词
 *   登录态（uid/用户名）───┘                                          │
 *                                                                     ▼
 *   会话里最近若干轮消息 ────────────────────────────────────► 大模型 → 宠物说的话
 * </pre>
 * 名字、主人的称呼、品种年龄绝育这些"关于我"的事实全部来自宠物档案，
 * 每轮都重新拼一次提示词（用户可能中途改了宠物名或称呼）。
 *
 * <h3>二、两种入口，一条模型调用</h3>
 * <ul>
 *   <li>{@link #chat}：主人先说，宠物回。走额度（biz=chat）、走历史、落一条 kind=0 的消息</li>
 *   <li>{@link #greet}：宠物先开口（首页自动问候）。<b>不走额度</b>、只在一个时段内调用一次模型、
 *       落一条 kind=1 的消息。它是"回来时它在等你"这种感觉的全部来源</li>
 * </ul>
 *
 * <h3>三、自动问候为什么必须有缓存（这是本模块最该讲清的一处工程取舍）</h3>
 * 首页是人进得最频繁的页面。不做任何处理的话，"每次进首页 = 一次大模型调用"，
 * 一个活跃用户一天就能烧掉几十次调用，而且首屏要多等好几秒。
 * 处理办法是两道闸门：
 * <ol>
 *   <li><b>Redis 缓存</b>：key 含"日期 + 时段"，TTL 精确卡到时段结束。
 *       同一时段内重复进首页只是一次 GET，瞬时返回</li>
 *   <li><b>回查已有发言</b>：缓存没命中但宠物在这个时段已经说过话（比如刚聊完又回首页），
 *       直接复用那句，也补进缓存。挡住"刚聊完就冒出第二句问候"</li>
 * </ol>
 * 结果：一只宠物一个时段最多一次模型调用（一天最多 5 次），而用户感知是"每次打开它都在说话"。
 *
 * <h3>四、失败边界</h3>
 * <ul>
 *   <li>聊天失败：回退刚扣的额度并抛出业务错误（失败不扣次数），画面要有明确交代</li>
 *   <li>问候失败：<b>只记日志、返回空问候</b>。问候是锦上添花，不能因为它把首页弄成错误页</li>
 * </ul>
 */
@Slf4j
@Service
public class AiPetChatService {

    /**
     * 问候缓存 key 前缀。key 形如 {@code MiaoWang:ai-pet:greet:<uid>:<pid>:<yyyy-MM-dd>:<slot>}
     * （日期是 {@link LocalDate#toString()} 的 ISO 形式，带横杠，别写成 yyyyMMdd；
     * 时段是 {@code TimeSlot#key()} 的小写值，如 morning）
     * —— 把日期与时段都放进 key，就不需要额外存"生成时间"再自己比对。
     */
    private static final String GREET_KEY_PREFIX = "MiaoWang:ai-pet:greet:";

    /** 时段边界留的余量：TTL 卡到时段结束后再多留一点，避免边界上同一时段被生成两次 */
    private static final Duration GREET_TTL_MARGIN = Duration.ofMinutes(1);

    /** 回复长度上限的兜底：提示词里已经要求 50 字，模型偶尔会超。超了只在日志里提醒，不截断（截断会把话说一半） */
    private static final int ANSWER_WARN_LENGTH = 300;

    /** 说给用户听的兜底名字（宠物没起名时气泡上显示的角色名） */
    private static final String UNNAMED_DISPLAY = "未命名";

    private final PetProfileLoader petProfileLoader;
    private final PetPersonaFactory petPersonaFactory;
    private final AiPetConversationService conversationService;
    private final PetChatQuotaGuard quotaGuard;
    private final AiCallLogRecorder aiCallLogRecorder;
    private final AiPetProperties aiPetProperties;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 用 ObjectProvider 而不是直接注入 ChatModel：模型 Bean 只在配了 api-key 时才存在
     * （见 {@code DashScopeConfig} 的 @ConditionalOnProperty）。直接注入的话，
     * 没配 key 的环境连服务都起不来 —— 而"没配 key 时服务照常启动、只有聊天接口报错"
     * 才是对开发与联调更友好的行为。
     */
    private final ObjectProvider<ChatModel> chatModelProvider;

    public AiPetChatService(PetProfileLoader petProfileLoader,
                            PetPersonaFactory petPersonaFactory,
                            AiPetConversationService conversationService,
                            PetChatQuotaGuard quotaGuard,
                            AiCallLogRecorder aiCallLogRecorder,
                            AiPetProperties aiPetProperties,
                            StringRedisTemplate stringRedisTemplate,
                            @Qualifier("dashscopeChatModel") ObjectProvider<ChatModel> chatModelProvider) {
        this.petProfileLoader = petProfileLoader;
        this.petPersonaFactory = petPersonaFactory;
        this.conversationService = conversationService;
        this.quotaGuard = quotaGuard;
        this.aiCallLogRecorder = aiCallLogRecorder;
        this.aiPetProperties = aiPetProperties;
        this.stringRedisTemplate = stringRedisTemplate;
        this.chatModelProvider = chatModelProvider;
    }

    // ==================== 主人先说，宠物回 ====================

    /**
     * 聊天主流程。顺序刻意如此：
     * <pre>
     *   校验 → 取档案（顺带校验归属）→ 扣额度 → 取历史 → 落主人消息 → 调模型 → 落宠物消息
     * </pre>
     * "取历史"必须在"落主人消息"之前，否则本轮提问会在提示词里出现两次。
     */
    public AiPetChatRespDTO chat(Long uid, Long pid, String message) {
        String question = normalizeQuestion(message);
        PetProfile profile = petProfileLoader.load(uid, pid);
        Long petId = profile.pid();

        // 先扣后调：扣不到额度直接拒绝，避免"答案已经产生成本、次数却扣不回来"
        PetChatQuotaGuard.QuotaState quota = quotaGuard.consumeOrThrow(uid);
        long start = System.currentTimeMillis();
        Long conversationId = null;
        try {
            conversationId = conversationService.getOrCreate(uid, petId, profile.name());
            // 历史要在写入本轮提问之前取
            List<AiPetMessageDO> history = conversationService.historyForPrompt(conversationId);
            conversationService.appendOwnerMessage(uid, petId, conversationId, question);

            ModelReply reply = callModel(profile, history, question, false, null);
            long latency = System.currentTimeMillis() - start;
            warnIfTooLong(reply.text(), uid, "chat");

            Long messageId = conversationService.appendPetMessage(uid, petId, conversationId, reply.text(),
                    AiPetMessageDO.KIND_CHAT, reply.usage(), latency);
            aiCallLogRecorder.recordSuccess(AiCallLogRecorder.SCENE_PET_CHAT, uid, conversationId, messageId,
                    reply.usage(), latency);

            AiPetChatRespDTO resp = new AiPetChatRespDTO();
            resp.setAnswer(reply.text());
            resp.setPetName(displayName(profile));
            resp.setPid(petId);
            resp.setConversationId(conversationId);
            resp.setMessageId(messageId);
            resp.setLatencyMs((int) latency);
            resp.setQuotaRemaining(quota.remaining());
            resp.setQuotaPeriodDesc(quota.periodDesc());
            return resp;
        } catch (RuntimeException e) {
            // 生成失败不扣次数：只有真扣过（非不限次数档位）才需要回退
            if (quota.counted()) {
                quotaGuard.refund(uid);
            }
            aiCallLogRecorder.recordFailure(AiCallLogRecorder.SCENE_PET_CHAT, uid, conversationId,
                    errorCodeOf(e), e.getMessage(), System.currentTimeMillis() - start);
            throw e;
        }
    }

    // ==================== 宠物先开口（首页自动问候） ====================

    /**
     * 打开宠物页面时的自动问候。
     *
     * <p>注意两个"不"：<b>不扣聊天次数</b>（用户没主动发起，扣次数没法解释）、
     * <b>不因模型失败而报错</b>（问候失败就安静地不显示，首页不能因此变成错误页）。
     */
    public AiPetGreetRespDTO greet(Long uid, Long pid) {
        PetProfile profile = petProfileLoader.load(uid, pid);
        Long petId = profile.pid();
        AiPetGreetRespDTO resp = new AiPetGreetRespDTO();
        resp.setPetName(displayName(profile));
        resp.setPid(petId);

        // 关闭问候时不建会话、不调模型，直接回一个空问候（前端据此不显示气泡）
        if (!greetingEnabled()) {
            resp.setCached(Boolean.TRUE);
            return resp;
        }

        LocalDateTime now = LocalDateTime.now();
        TimeSlot slot = TimeSlot.of(now);
        Long conversationId = conversationService.getOrCreate(uid, petId, profile.name());
        resp.setConversationId(conversationId);

        // 闸门一：时段内已生成过 → 直接命中
        String cacheKey = greetCacheKey(uid, petId, now, slot);
        String cached = readCache(cacheKey);
        if (cached != null) {
            resp.setGreeting(cached);
            resp.setCached(Boolean.TRUE);
            return resp;
        }

        // 闸门二：这个时段宠物已经开过口（通常是刚聊完又回到首页）→ 复用那句
        Date slotStart = toDate(slot.startOf(now));
        AiPetMessageDO latest = conversationService.latestPetMessage(conversationId, slotStart);
        if (latest != null && latest.getContent() != null && !latest.getContent().isBlank()) {
            writeCache(cacheKey, latest.getContent(), now, slot);
            resp.setGreeting(latest.getContent());
            resp.setCached(Boolean.TRUE);
            return resp;
        }

        // 真·未命中：生成一次问候
        long start = System.currentTimeMillis();
        try {
            List<AiPetMessageDO> history = conversationService.historyForPrompt(conversationId);
            ModelReply reply = callModel(profile, history, null, true, slot);
            long latency = System.currentTimeMillis() - start;
            Long messageId = conversationService.appendPetMessage(uid, petId, conversationId, reply.text(),
                    AiPetMessageDO.KIND_GREETING, reply.usage(), latency);
            aiCallLogRecorder.recordSuccess(AiCallLogRecorder.SCENE_PET_GREET, uid, conversationId, messageId,
                    reply.usage(), latency);
            writeCache(cacheKey, reply.text(), now, slot);
            resp.setGreeting(reply.text());
            resp.setCached(Boolean.FALSE);
            return resp;
        } catch (Exception e) {
            // 问候是锦上添花：失败只记日志，让首页照常显示（前端不显示问候气泡即可）
            aiCallLogRecorder.recordFailure(AiCallLogRecorder.SCENE_PET_GREET, uid, conversationId,
                    errorCodeOf(e), e.getMessage(), System.currentTimeMillis() - start);
            log.warn("自动问候生成失败，本次不显示问候：uid={}, pid={}, err={}", uid, petId, e.getMessage());
            resp.setCached(Boolean.TRUE);
            return resp;
        }
    }

    // ==================== 模型调用 ====================

    /**
     * 组装 messages 并调用模型。
     *
     * <p>多轮不是把历史拼成一个大字符串，而是还原成模型标准的 messages 数组：
     * {@code [System 人设] [历史主人] [历史宠物] … [本轮] }，模型才能分清哪句是谁说的。
     *
     * @param userText 本轮主人说的话
     * @param greeting true = 本轮不是主人说话，而是"系统让宠物先开口"
     * @param slot     仅问候时使用（拼问候指令）
     */
    private ModelReply callModel(PetProfile profile, List<AiPetMessageDO> history, String userText,
                                 boolean greeting, TimeSlot slot) {
        List<Message> messages = new ArrayList<>(history.size() + 2);
        messages.add(new SystemMessage(petPersonaFactory.systemPrompt(profile)));
        messages.addAll(toMessages(history));
        messages.add(new UserMessage(greeting
                ? petPersonaFactory.greetingInstruction(profile, slot)
                : userText));

        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            // 模型 Bean 不存在 = 这台机器没配 api-key，属配置问题而不是用户问题
            log.error("ChatModel 未装配（llm.dashscope.chat.api-key 未配置），无法生成回复");
            throw new ServiceException(AiPetErrorCodeEnum.PET_CHAT_UNAVAILABLE);
        }
        ChatResponse response = chatModel.call(new Prompt(messages));
        String text = clean(TokenUsageExtractor.textOf(response));
        if (text == null || text.isBlank()) {
            // 空回复必须当失败：模型被安全策略拦下、或思考模型把所有内容都放进了思考里，
            // 这时候回一条空消息给用户，比报错更让人困惑
            throw new ServiceException(AiPetErrorCodeEnum.PET_CHAT_GENERATE_ERROR);
        }
        return new ModelReply(text, TokenUsageExtractor.usageOf(response));
    }

    /**
     * 库里的消息 → 大模型的 messages 格式
     */
    private static List<Message> toMessages(List<AiPetMessageDO> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        List<Message> messages = new ArrayList<>(history.size());
        for (AiPetMessageDO row : history) {
            if (row.getContent() == null || row.getContent().isBlank()) {
                continue;
            }
            messages.add(row.getRole() != null && row.getRole() == AiPetMessageDO.ROLE_OWNER
                    ? new UserMessage(row.getContent())
                    : new AssistantMessage(row.getContent()));
        }
        return messages;
    }

    // ==================== 小工具 ====================

    /**
     * 提问清洗与校验：
     * <ul>
     *   <li>空白 → B000900</li>
     *   <li>超长 → B000909（挡住"一次贴一整本书"把单次成本打爆）</li>
     * </ul>
     * 长度按字符数算（中文 1 字 ≈ 1~1.5 token），阈值可在 Nacos 调。
     */
    private String normalizeQuestion(String message) {
        if (message == null || message.isBlank()) {
            throw new ClientException(AiPetErrorCodeEnum.PET_CHAT_MESSAGE_EMPTY);
        }
        String question = message.trim();
        int max = aiPetProperties.getMaxQuestionLength() == null ? 500 : aiPetProperties.getMaxQuestionLength();
        if (question.length() > max) {
            log.info("宠物聊天提问超长被拒：len={}, max={}", question.length(), max);
            throw new ClientException(AiPetErrorCodeEnum.PET_CHAT_MESSAGE_TOO_LONG);
        }
        return question;
    }

    /**
     * 回复清洗：去首尾空白与包裹的引号。
     * 模型很喜欢把整句话用「」或英文引号包起来，直接显示会显得像在引用别人说话。
     */
    private static String clean(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text.trim();
        if (cleaned.length() >= 2) {
            char first = cleaned.charAt(0);
            char last = cleaned.charAt(cleaned.length() - 1);
            boolean wrapped = (first == '「' && last == '」')
                    || (first == '“' && last == '”')
                    || (first == '"' && last == '"')
                    || (first == '\'' && last == '\'');
            if (wrapped) {
                cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
            }
        }
        return cleaned;
    }

    private static void warnIfTooLong(String text, Long uid, String scene) {
        if (text != null && text.length() > ANSWER_WARN_LENGTH) {
            // 不截断（截断会把话说一半），只把"提示词没管住"这件事暴露出来
            log.warn("宠物回复偏长，提示词可能需要收紧：scene={}, uid={}, len={}", scene, uid, text.length());
        }
    }

    /**
     * 气泡上显示的角色名：没起名时给「未命名」，与两端界面的兜底口径一致
     */
    private static String displayName(PetProfile profile) {
        return profile.named() ? profile.name() : UNNAMED_DISPLAY;
    }

    private boolean greetingEnabled() {
        return !Boolean.FALSE.equals(aiPetProperties.getGreetingEnabled());
    }

    /**
     * 缓存 key：日期 + 时段都进 key，就不必额外存"生成时间"再自己判断是否过期
     */
    private static String greetCacheKey(Long uid, Long pid, LocalDateTime now, TimeSlot slot) {
        return GREET_KEY_PREFIX + uid + ":" + pid + ":"
                + now.toLocalDate() + ":" + slot.key();
    }

    private String readCache(String key) {
        try {
            return stringRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            // Redis 挂了不该让首页问候整体失败：当作未命中，走生成流程
            log.warn("读问候缓存失败，按未命中处理：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 写入问候缓存，<b>TTL 精确卡到当前时段结束</b>（再多留 1 分钟余量）。
     *
     * <p>这是"一个时段一次生成"的关键：不用固定 TTL（固定 2 小时会出现同一时段生成两三次），
     * 也不用每天零点过期（那样早上生成的"早呀"会一直用到深夜）。
     */
    private void writeCache(String key, String value, LocalDateTime now, TimeSlot slot) {
        try {
            Duration ttl = Duration.between(now, slot.endOf(now)).plus(GREET_TTL_MARGIN);
            if (ttl.isNegative() || ttl.isZero()) {
                ttl = GREET_TTL_MARGIN;
            }
            stringRedisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            // 写缓存失败只影响"下次还得再生成一次"，不影响本次结果
            log.warn("写问候缓存失败：{}", e.getMessage());
        }
    }

    private static Date toDate(LocalDateTime time) {
        return Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * 取异常里的业务错误码；非业务异常退化成异常类名（排障时"是什么错"比"错误消息"更好聚合）
     */
    private static String errorCodeOf(Throwable e) {
        if (e == null) {
            return null;
        }
        if (e instanceof AbstractException ae) {
            return ae.getErrorCode();
        }
        return e.getClass().getSimpleName();
    }

    /**
     * 一次模型调用的结果
     *
     * @param text  宠物说的话（已清洗，保证非空）
     * @param usage token 用量，可能为 null（模型没返回时成本列留空，不记 0）
     */
    private record ModelReply(String text, Usage usage) {
    }
}