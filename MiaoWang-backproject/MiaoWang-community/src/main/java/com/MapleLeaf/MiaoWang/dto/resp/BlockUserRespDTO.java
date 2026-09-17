package com.MapleLeaf.MiaoWang.dto.resp;

import lombok.Data;

import java.util.Date;

/**
 * 黑名单里的一个用户
 */
@Data
public class BlockUserRespDTO {

    /**
     * 被拉黑的用户 uid
     */
    private Long uid;

    /**
     * 昵称（从社区内容里解析；解不出来为 null）
     */
    private String username;

    /**
     * 拉黑时间
     */
    private Date createTime;
}