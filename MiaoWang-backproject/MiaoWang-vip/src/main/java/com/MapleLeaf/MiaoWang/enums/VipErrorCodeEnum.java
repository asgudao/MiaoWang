package com.MapleLeaf.MiaoWang.enums;


import com.MapleLeaf.MiaoWang.common.convention.errorcode.IErrorCode;

/**
 * VIP 模块错误码
 * 编码段位沿用各模块约定：B0003xx 属于会员业务
 */
public enum VipErrorCodeEnum implements IErrorCode {

    VIP_PLAN_NOT_EXIST("B000300", "会员档位不存在"),
    VIP_PLAN_NOT_PURCHASABLE("B000301", "该档位暂不支持购买"),
    VIP_ORDER_NOT_EXIST("B000302", "订单不存在"),
    VIP_ORDER_SAVE_ERROR("B000303", "订单创建失败"),
    VIP_MEMBERSHIP_SAVE_ERROR("B000304", "会员开通失败"),
    VIP_QUOTA_BIZ_ERROR("B000305", "额度类型不支持"),
    VIP_3D_ORDER_NOT_EXIST("B000306", "定制订单不存在"),
    VIP_3D_ORDER_STATUS_ERROR("B000307", "当前订单状态不允许该操作"),
    VIP_3D_ORDER_PARAM_ERROR("B000308", "定制信息不完整"),
    ;

    private final String code;

    private final String message;

    VipErrorCodeEnum(String code, String message) {
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