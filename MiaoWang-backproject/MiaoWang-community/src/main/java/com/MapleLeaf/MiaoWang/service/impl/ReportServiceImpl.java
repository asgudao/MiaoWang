package com.MapleLeaf.MiaoWang.service.impl;

import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.biz.user.UserContext;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.constant.CommunityConstant;
import com.MapleLeaf.MiaoWang.dao.entity.CommunityCommentDO;
import com.MapleLeaf.MiaoWang.dao.entity.CommunityPostDO;
import com.MapleLeaf.MiaoWang.dao.entity.CommunityReportDO;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunityCommentMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunityPostMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunityReportMapper;
import com.MapleLeaf.MiaoWang.dto.req.ReportCreateReqDTO;
import com.MapleLeaf.MiaoWang.enums.CommunityErrorCodeEnum;
import com.MapleLeaf.MiaoWang.service.BlockService;
import com.MapleLeaf.MiaoWang.service.ReportService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 举报实现
 * <p>
 * 只做"收下举报"：落一条待处理记录，不改被举报内容的状态。
 * 判定违规后下架/删评论是运营动作，得先有管理员角色，现在没有角色体系，硬做就是把权限逻辑写错。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl extends ServiceImpl<CommunityReportMapper, CommunityReportDO> implements ReportService {

    private final CommunityPostMapper postMapper;
    private final CommunityCommentMapper commentMapper;
    private final BlockService blockService;

    @Override
    public Long createReport(ReportCreateReqDTO requestParam) {
        // 先校验登录，再校验目标：顺序反了会让未登录请求探测出哪些帖子/评论 id 存在
        Long uid = currentLoginUid();
        String username = UserContext.getUsername();
        if (requestParam == null || requestParam.getTargetId() == null) {
            throw new ClientException(CommunityErrorCodeEnum.REPORT_TARGET_INVALID);
        }
        Integer reasonType = requestParam.getReasonType();
        if (reasonType == null || reasonType < CommunityConstant.REPORT_REASON_MIN
                || reasonType > CommunityConstant.REPORT_REASON_MAX) {
            throw new ClientException(CommunityErrorCodeEnum.REPORT_REASON_INVALID);
        }
        String reason = StrUtil.trim(requestParam.getReason());
        if (reason != null && reason.length() > CommunityConstant.REPORT_REASON_MAX_LENGTH) {
            throw new ClientException(CommunityErrorCodeEnum.REPORT_REASON_TOO_LONG);
        }
        // 举报的目标必须真实存在，且举报人自己看得见它：被拉黑的内容在他眼里等于不存在
        Set<Long> invisibleUids = blockService.invisibleUids(uid);
        Long targetUid;
        Long postId;
        if (CommunityConstant.TARGET_TYPE_POST.equals(requestParam.getTargetType())) {
            CommunityPostDO postDO = postMapper.selectOne(Wrappers.lambdaQuery(CommunityPostDO.class)
                    .eq(CommunityPostDO::getPostId, requestParam.getTargetId())
                    .eq(CommunityPostDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL));
            if (postDO == null || invisibleUids.contains(postDO.getUid())) {
                throw new ClientException(CommunityErrorCodeEnum.REPORT_TARGET_INVALID);
            }
            targetUid = postDO.getUid();
            postId = postDO.getPostId();
        } else if (CommunityConstant.TARGET_TYPE_COMMENT.equals(requestParam.getTargetType())) {
            CommunityCommentDO commentDO = commentMapper.selectOne(Wrappers.lambdaQuery(CommunityCommentDO.class)
                    .eq(CommunityCommentDO::getCommentId, requestParam.getTargetId())
                    .eq(CommunityCommentDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL));
            if (commentDO == null || invisibleUids.contains(commentDO.getUid())) {
                throw new ClientException(CommunityErrorCodeEnum.REPORT_TARGET_INVALID);
            }
            targetUid = commentDO.getUid();
            // 评论举报也把所属帖子记下来，审核端能一键跳到上下文
            postId = commentDO.getPostId();
        } else {
            throw new ClientException(CommunityErrorCodeEnum.REPORT_TARGET_INVALID);
        }
        if (uid.equals(targetUid)) {
            throw new ClientException(CommunityErrorCodeEnum.REPORT_SELF);
        }
        CommunityReportDO reportDO = new CommunityReportDO();
        reportDO.setUid(uid);
        reportDO.setUsername(username);
        reportDO.setTargetType(requestParam.getTargetType());
        reportDO.setTargetId(requestParam.getTargetId());
        reportDO.setPostId(postId);
        reportDO.setTargetUid(targetUid);
        reportDO.setReasonType(reasonType);
        reportDO.setReason(reason);
        reportDO.setStatus(CommunityConstant.REPORT_STATUS_PENDING);
        reportDO.setHandleUid(CommunityConstant.REPORT_NO_HANDLER);
        try {
            baseMapper.insert(reportDO);
        } catch (DuplicateKeyException e) {
            // 举报不是点赞那种幂等动作：重复举报要让用户知道"已经在处理"，不然他会反复提交
            throw new ClientException(CommunityErrorCodeEnum.REPORT_DUPLICATE);
        }
        log.info("收到社区举报, reportId={}, uid={}, targetType={}, targetId={}, reasonType={}",
                reportDO.getReportId(), uid, requestParam.getTargetType(), requestParam.getTargetId(), reasonType);
        return reportDO.getReportId();
    }

    private Long currentLoginUid() {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(userId)) {
            throw new ClientException(CommunityErrorCodeEnum.USER_NOT_LOGIN);
        }
        return Long.parseLong(userId);
    }
}