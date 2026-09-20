package com.MapleLeaf.MiaoWang.dao.entity;

import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 电子宠物消息（主人一句 / 宠物一句，各一行）
 *
 * <h3>role 与 kind 是两个维度，别混</h3>
 * <pre>
 *   role：谁说的    1 = 主人（用户输入）  2 = 宠物（模型生成）
 *   kind：怎么来的  0 = 一问一答      1 = 自动问候（宠物主动开口，用户没发消息）
 * </pre>
 * 分开存是因为两者的成本与配额口径不同：自动问候不消耗聊天次数（用户没主动发起），
 * 但它一样花 token。合成一个字段就没法既统计"用户聊了多少次"又统计"问候花了多少钱"。
 *
 * <h3>token / latency 落在消息行上</h3>
 * 这样"这次回答花了多少 token、等了多久"与回答本身一一对应，
 * 不必再去调用日志里按时间戳对齐（对齐本身就不可靠）。
 */
@Data
@TableName("miaowang_ai_pet_message")
@EqualsAndHashCode(callSuper = false)
public class AiPetMessageDO extends BaseDO {

    /** 角色：主人 */
    public static final int ROLE_OWNER = 1;

    /** 角色：宠物 */
    public static final int ROLE_PET = 2;

    /** 来源：一问一答（用户主动发起） */
    public static final int KIND_CHAT = 0;

    /** 来源：自动问候（宠物主动开口） */
    public static final int KIND_GREETING = 1;

    /**
     * 消息ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属会话
     */
    private Long conversationId;

    /**
     * 所属用户（冗余：按人查、鉴权都要用）
     */
    private Long uid;

    /**
     * 宠物ID（冗余：同一用户多只宠物时便于按宠物过滤）
     */
    private Long pid;

    /**
     * 角色：1=主人 2=宠物
     */
    private Integer role;

    /**
     * 来源：0=一问一答 1=自动问候
     */
    private Integer kind;

    /**
     * 消息正文
     */
    private String content;

    /**
     * 输入 token（宠物行才有）
     */
    private Integer tokenIn;

    /**
     * 输出 token（宠物行才有）
     */
    private Integer tokenOut;

    /**
     * 本次生成耗时（毫秒）
     */
    private Integer latencyMs;
}