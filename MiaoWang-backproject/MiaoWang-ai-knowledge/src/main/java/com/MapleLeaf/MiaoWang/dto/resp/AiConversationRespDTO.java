package com.MapleLeaf.MiaoWang.dto.resp;

import com.MapleLeaf.MiaoWang.dao.entity.AiConversationDO;
import lombok.Data;

import java.util.Date;

/**
 * 会话列表项（历史会话列表用）
 *
 * <p>不返回消息正文：列表页只要标题 + 时间 + 条数，
 * 把每次会话的几条正文都带上会让列表接口的响应体膨胀十几倍。
 */
@Data
public class AiConversationRespDTO {

    private Long id;

    /**
     * 标题（首条提问的前 20 字）
     */
    private String title;

    /**
     * 消息条数（一问一答算 2 条）
     */
    private Integer messageCount;

    /**
     * 最后一条消息时间：列表排序依据
     */
    private Date lastMessageTime;

    private Date createTime;

    public static AiConversationRespDTO of(AiConversationDO row) {
        AiConversationRespDTO resp = new AiConversationRespDTO();
        resp.setId(row.getId());
        resp.setTitle(row.getTitle());
        resp.setMessageCount(row.getMessageCount());
        resp.setLastMessageTime(row.getLastMessageTime());
        resp.setCreateTime(row.getCreateTime());
        return resp;
    }
}