package com.MapleLeaf.MiaoWang.dto.resp;

import lombok.Data;

/**
 * 打开宠物页面时的自动问候响应
 */
@Data
public class AiPetGreetRespDTO {

    /**
     * 宠物主动说的话。为 null（问候关闭 / 服务未接上模型）时前端不显示问候气泡。
     */
    private String greeting;

    /**
     * 宠物名
     */
    private String petName;

    /**
     * 宠物ID
     */
    private Long pid;

    /**
     * 会话ID（前端点进对话页时可以直接用它拉历史）
     */
    private Long conversationId;

    /**
     * 本次是否复用了已有内容（缓存命中，或这一时段宠物已经说过话）而没有重新生成。
     * <p>前端可以用它决定要不要播"它正在打字"的动效：复用是瞬时的，不该有等待感。
     */
    private Boolean cached;
}