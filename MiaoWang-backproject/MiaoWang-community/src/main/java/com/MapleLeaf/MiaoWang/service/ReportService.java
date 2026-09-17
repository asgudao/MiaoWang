package com.MapleLeaf.MiaoWang.service;

import com.MapleLeaf.MiaoWang.dto.req.ReportCreateReqDTO;

/**
 * 社区举报
 */
public interface ReportService {

    /**
     * 举报帖子或评论
     * <p>
     * 同一个举报人对同一条内容只能举报一次（唯一键兜底），
     * 重复举报返回 {@code B000323}，不是幂等成功 —— 重复点要让用户看到"已经在处理了"。
     *
     * @return 举报记录 id
     */
    Long createReport(ReportCreateReqDTO requestParam);
}