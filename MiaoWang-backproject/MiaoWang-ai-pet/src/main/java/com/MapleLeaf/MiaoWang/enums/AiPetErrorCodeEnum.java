package com.MapleLeaf.MiaoWang.enums;

import com.MapleLeaf.MiaoWang.common.convention.errorcode.IErrorCode;

/**
 * ai-pet 模块错误码
 *
 * <p>段位分配（全项目约定，勿重复占用）：
 * <ul>
 *   <li>B0000xx / B0001xx：common 基础错误</li>
 *   <li>B0002xx：user</li>
 *   <li>B0003xx：community</li>
 *   <li>B0004xx：healthy</li>
 *   <li>B0005xx：vip</li>
 *   <li>B0006xx：common 文件/对象存储</li>
 *   <li>B0007xx：ai-knowledge</li>
 *   <li>B0008xx：pet</li>
 *   <li><b>B0009xx：ai-pet（本模块）</b></li>
 * </ul>
 *
 * <p>文案口径：这是"宠物在说话"的场景，所以错误文案尽量像宠物/铲屎官的日常口吻，
 * 但"次数用完""服务不可用"这类必须让用户知道下一步做什么的，仍然要说人话、给出路。
 */
public enum AiPetErrorCodeEnum implements IErrorCode {

    PET_CHAT_MESSAGE_EMPTY("B000900", "想说什么呢，先输入一句话吧"),
    PET_CHAT_NOT_LOGIN("B000901", "请先登录后再和它聊天"),
    PET_CHAT_QUOTA_EXHAUSTED("B000902", "今天和它聊天的次数用完了，开通会员可以聊更多"),
    PET_CHAT_VIP_SERVICE_ERROR("B000903", "会员权益服务暂不可用，请稍后重试"),
    PET_NOT_FOUND("B000904", "宠物不存在或已被删除"),
    PET_SERVICE_ERROR("B000905", "宠物档案服务暂不可用，请稍后重试"),
    PET_PROFILE_EMPTY("B000906", "还没有宠物档案，先添加一只再来找它聊天"),
    PET_CHAT_GENERATE_ERROR("B000907", "它这会儿没接上话，稍后再试试"),
    PET_CONVERSATION_NOT_FOUND("B000908", "会话不存在或已删除"),
    PET_CHAT_MESSAGE_TOO_LONG("B000909", "一次最多说 500 字，分几次说吧"),
    PET_CHAT_UNAVAILABLE("B000910", "它还在休息，聊天功能暂时不可用"),
    ;

    private final String code;

    private final String message;

    AiPetErrorCodeEnum(String code, String message) {
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