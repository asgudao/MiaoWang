package com.MapleLeaf.MiaoWang.dto.req;

import lombok.Data;

/**
 * 答案反馈请求参数
 */
@Data
public class AiMessageFeedbackReqDTO {

    /**
     * 1=👍 有帮助 2=👎 没帮助
     */
    private Integer type;
}