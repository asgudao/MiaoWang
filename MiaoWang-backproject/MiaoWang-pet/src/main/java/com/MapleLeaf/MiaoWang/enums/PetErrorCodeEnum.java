package com.MapleLeaf.MiaoWang.enums;


import com.MapleLeaf.MiaoWang.common.convention.errorcode.IErrorCode;

/**
 * pet 模块错误码
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
 *   <li><b>B0008xx：pet（本模块）</b></li>
 * </ul>
 */
public enum PetErrorCodeEnum implements IErrorCode {

    PET_LIMIT_EXCEED("B000800", "宠物数量已达当前档位上限，开通会员可提升"),
    PET_VIP_SERVICE_ERROR("B000801", "会员权益服务暂不可用，请稍后重试"),
    ;

    private final String code;

    private final String message;

    PetErrorCodeEnum(String code, String message) {
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