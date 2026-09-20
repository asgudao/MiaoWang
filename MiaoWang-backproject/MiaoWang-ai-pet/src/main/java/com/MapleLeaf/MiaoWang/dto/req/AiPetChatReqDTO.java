package com.MapleLeaf.MiaoWang.dto.req;

import lombok.Data;

/**
 * 和电子宠物聊天请求
 *
 * <p>{@code pid} 可以不传：用户可能有多只宠物，不传就聊"第一只"（默认宠物）。
 * 前端从首页进来时通常带当前展示的那只，从对话页进来时带页面上选中的那只。
 */
@Data
public class AiPetChatReqDTO {

    /**
     * 宠物ID；不传 = 用当前用户的第一只宠物
     */
    private Long pid;

    /**
     * 主人说的话
     */
    private String message;
}