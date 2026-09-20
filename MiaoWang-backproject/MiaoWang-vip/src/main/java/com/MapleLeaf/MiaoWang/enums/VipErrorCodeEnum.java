package com.MapleLeaf.MiaoWang.enums;


import com.MapleLeaf.MiaoWang.common.convention.errorcode.IErrorCode;

/**
 * VIP 模块错误码
 *
 * <p>段位分配（全项目约定，勿重复占用）：
 * <ul>
 *   <li>B0000xx / B0001xx：common 基础错误</li>
 *   <li>B0002xx：user</li>
 *   <li>B0003xx：community</li>
 *   <li>B0004xx：healthy</li>
 *   <li><b>B0005xx：vip（本模块）</b></li>
 *   <li>B0006xx：common 文件/对象存储</li>
 * </ul>
 */
public enum VipErrorCodeEnum implements IErrorCode {

    VIP_PLAN_NOT_EXIST("B000500", "会员档位不存在"),
    VIP_PLAN_NOT_PURCHASABLE("B000501", "该档位暂不支持购买"),
    VIP_ORDER_NOT_EXIST("B000502", "订单不存在"),
    VIP_ORDER_SAVE_ERROR("B000503", "订单创建失败"),
    VIP_MEMBERSHIP_SAVE_ERROR("B000504", "会员开通失败"),
    VIP_QUOTA_BIZ_ERROR("B000505", "额度类型不支持"),
    VIP_3D_ORDER_NOT_EXIST("B000506", "定制订单不存在"),
    VIP_3D_ORDER_STATUS_ERROR("B000507", "当前订单状态不允许该操作"),
    VIP_3D_ORDER_PARAM_ERROR("B000508", "定制信息不完整"),
    VIP_QUOTA_CALC_ERROR("B000509", "额度计数服务异常，请稍后重试"),
    VIP_3D_PET_NOT_OWNED("B000510", "宠物不存在或无权访问"),
    VIP_3D_PET_SERVICE_ERROR("B000511", "宠物服务暂不可用，请稍后重试"),
    VIP_ORDER_STATUS_ERROR("B000512", "订单状态不允许该操作"),
    VIP_PAY_CHANNEL_UNSUPPORTED("B000513", "暂不支持该支付渠道"),
    VIP_PAY_SIGN_INVALID("B000514", "支付回调验签失败"),
    VIP_PAY_AMOUNT_MISMATCH("B000515", "支付金额与订单不一致"),
    VIP_PAY_REPLAY("B000516", "支付回调已过期或重复"),
    VIP_PAY_NOTIFY_NOT_ALLOWED("B000517", "该支付渠道不接受外部回调"),
    VIP_REFUND_WINDOW_EXPIRED("B000518", "已超过自助退款期限，请联系客服"),
    VIP_REFUND_FAILED("B000519", "退款失败，请稍后重试"),
    VIP_PROMO_NOT_EXIST("B000520", "兑换码不存在或已失效"),
    VIP_PROMO_EXPIRED("B000521", "兑换码不在有效期内"),
    VIP_PROMO_USED_UP("B000522", "兑换码名额已用完"),
    VIP_PROMO_USED_BY_ME("B000523", "你已经使用过这个兑换码"),
    VIP_PROMO_TYPE_MISMATCH("B000524", "兑换码类型不匹配"),
    VIP_PROMO_MIN_AMOUNT("B000525", "订单金额未达到该优惠码的使用门槛"),
    VIP_PROMO_NOT_APPLICABLE("B000526", "该优惠码不适用于当前档位"),
    VIP_PROMO_ORDER_NO_REFUND("B000527", "促销订单不支持退款"),
    VIP_ADMIN_UNAUTHORIZED("B000528", "请先登录后台"),
    VIP_ADMIN_FORBIDDEN("B000529", "当前角色没有该操作权限"),
    VIP_ADMIN_LOGIN_FAILED("B000530", "账号或密码错误"),
    VIP_ADMIN_DISABLED("B000531", "账号已被禁用"),
    VIP_ADMIN_USERNAME_EXIST("B000532", "登录名已存在"),
    VIP_PAY_CHANNEL_NOT_CONFIGURED("B000533", "支付渠道未配置，请先在后端配置渠道密钥"),
    VIP_PAY_CHANNEL_ERROR("B000534", "支付渠道调用失败，请稍后重试"),
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