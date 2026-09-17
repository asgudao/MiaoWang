package com.MapleLeaf.MiaoWang.enums;


import com.MapleLeaf.MiaoWang.common.convention.errorcode.IErrorCode;

/**
 * ai-knowledge 模块错误码
 *
 * <p>段位分配（全项目约定，勿重复占用）：
 * <ul>
 *   <li>B0000xx / B0001xx：common 基础错误</li>
 *   <li>B0002xx：user</li>
 *   <li>B0003xx：community</li>
 *   <li>B0004xx：healthy</li>
 *   <li>B0005xx：vip</li>
 *   <li>B0006xx：common 文件/对象存储</li>
 *   <li><b>B0007xx：ai-knowledge（本模块）</b></li>
 * </ul>
 */
public enum AiErrorCodeEnum implements IErrorCode {

    AI_QUESTION_EMPTY("B000700", "问题不能为空"),
    AI_NOT_LOGIN("B000701", "请先登录后再使用宠物健康问答"),
    AI_QUOTA_EXHAUSTED("B000702", "AI 问答次数已用完，开通会员可提升次数"),
    AI_VIP_SERVICE_ERROR("B000703", "会员权益服务暂不可用，请稍后重试"),
    AI_CONVERSATION_NOT_FOUND("B000704", "会话不存在或已删除"),
    AI_MESSAGE_NOT_FOUND("B000705", "消息不存在"),
    AI_FEEDBACK_TYPE_INVALID("B000706", "反馈类型只能是 1（赞）或 2（踩）"),
    ;

    private final String code;

    private final String message;

    AiErrorCodeEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}