package com.MapleLeaf.MiaoWang.dto.req;

import lombok.Data;

/**
 * 举报请求
 */
@Data
public class ReportCreateReqDTO {

    /**
     * 目标类型：1帖子 2评论
     */
    private Integer targetType;

    /**
     * 目标 id（帖子 id 或评论 id）
     */
    private Long targetId;

    /**
     * 举报理由分类：1广告引流 2辱骂攻击 3色情低俗 4违法违规 5虚假信息 6其他
     */
    private Integer reasonType;

    /**
     * 补充说明（可选）
     */
    private String reason;
}