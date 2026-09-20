package com.MapleLeaf.MiaoWang.dao.entity;

import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 电子宠物会话（一只宠物一条，长期存在）
 *
 * <h3>为什么不是 ai-knowledge 那种"一次对话一条会话"</h3>
 * 知识问答是"问完就走"的话题制，所以那里一次提问建一条会话、有历史列表；
 * 电子宠物是<b>陪伴</b>关系：主人和同一只宠物之间只应该有一条连续的记忆线 ——
 * "上次你说要带我去打疫苗"这种事跨会话就断了。
 * 因此这里按 <b>(uid, pid) 唯一</b>，删掉历史列表与"新建对话"的整套 UI，
 * 反而更符合产品语义。（表上真有 uk_uid_pid 唯一索引兜底，见 sql/ai-pet-ddl.sql）
 *
 * <h3>pet_name 是快照</h3>
 * 宠物改名后，历史消息里提到的名字应该保留当时的叫法，
 * 同时也让"这条会话聊的是谁"在宠物被删除后依然可读。
 */
@Data
@TableName("miaowang_ai_pet_conversation")
@EqualsAndHashCode(callSuper = false)
public class AiPetConversationDO extends BaseDO {

    /**
     * 会话ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户
     */
    private Long uid;

    /**
     * 宠物ID
     */
    private Long pid;

    /**
     * 宠物名快照（建会话 / 每次聊天时刷新，便于后台一眼看出聊的是谁）
     */
    private String petName;

    /**
     * 消息条数（冗余：首页/列表不必再 count 子查询）
     */
    private Integer messageCount;

    /**
     * 最后一条消息时间（首页判断"最近聊过没有"用；NULL = 还没消息）
     */
    private Date lastMessageTime;
}