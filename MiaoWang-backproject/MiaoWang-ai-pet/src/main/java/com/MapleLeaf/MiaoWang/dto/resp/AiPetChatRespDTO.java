package com.MapleLeaf.MiaoWang.dto.resp;

import lombok.Data;

/**
 * 和电子宠物聊天的响应
 */
@Data
public class AiPetChatRespDTO {

    /**
     * 宠物说了什么
     */
    private String answer;

    /**
     * 宠物名（前端气泡上要显示"是谁在说话"）
     */
    private String petName;

    /**
     * 这只宠物的ID
     */
    private Long pid;

    /**
     * 会话ID（同一只宠物长期同一个会话，回放历史时用）
     */
    private Long conversationId;

    /**
     * 本条宠物消息的ID
     */
    private Long messageId;

    /**
     * 本次生成耗时（毫秒），前端可用来解释"为什么等了这么久"
     */
    private Integer latencyMs;

    /**
     * 本周期还剩几次聊天；-1 = 不限次数；null = 没取到（不影响聊天本身）
     * <p>放出来的原因：不显示剩余次数，用户被"次数用完"挡住时会很突兀。
     */
    private Integer quotaRemaining;

    /**
     * 额度周期描述（每日/每月），与 quotaRemaining 配套显示
     */
    private String quotaPeriodDesc;
}