package com.MapleLeaf.MiaoWang.dto.resp;

import com.MapleLeaf.MiaoWang.dao.entity.AiPetMessageDO;
import lombok.Data;

import java.util.Date;

/**
 * 电子宠物会话里的一条消息（给前端回放历史用）
 */
@Data
public class AiPetMessageRespDTO {

    /**
     * 消息ID
     */
    private Long id;

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
     * 消息时间
     */
    private Date createTime;

    /**
     * 由实体转响应（不需要 ObjectMapper，字段一一对应）
     */
    public static AiPetMessageRespDTO of(AiPetMessageDO row) {
        AiPetMessageRespDTO resp = new AiPetMessageRespDTO();
        resp.setId(row.getId());
        resp.setRole(row.getRole());
        resp.setKind(row.getKind());
        resp.setContent(row.getContent());
        resp.setCreateTime(row.getCreateTime());
        return resp;
    }
}