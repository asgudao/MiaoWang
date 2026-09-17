package com.MapleLeaf.MiaoWang.enums;


import com.MapleLeaf.MiaoWang.common.convention.errorcode.IErrorCode;

/**
 * 健康档案错误码
 * 编码段位沿用各模块约定：B0004xx 属于健康档案业务
 */
public enum HealthyErrorCodeEnum implements IErrorCode {

    HEALTH_PET_NOT_FOUND("B000400", "宠物不存在或无权访问"),
    HEALTH_PET_SERVICE_ERROR("B000401", "宠物服务暂不可用，请稍后重试"),
    HEALTH_RECORD_NOT_FOUND("B000402", "记录不存在或无权操作"),
    HEALTH_RECORD_LIMIT("B000403", "健康记录条数已达当前档位上限，开通会员可无限记录"),
    HEALTH_EXPORT_NOT_ALLOWED("B000404", "当前档位不支持导出健康报告"),
    HEALTH_VIP_SERVICE_ERROR("B000405", "会员权益服务暂不可用，请稍后重试"),
    HEALTH_RECORD_SAVE_ERROR("B000406", "记录保存失败"),
    HEALTH_PARAM_ERROR("B000407", "记录信息不完整"),
    HEALTH_KNOWLEDGE_SERVICE_ERROR("B000408", "知识库服务暂不可用，请稍后重试"),
    HEALTH_SYMPTOM_NOT_FOUND("B000409", "症状选项不存在，请重新选择"),
    HEALTH_PET_ID_REQUIRED("B000410", "缺少宠物ID"),
    HEALTH_AI_NOT_ALLOWED("B000411", "当前档位不支持 AI 解读症状，开通 34.9 及以上会员可用"),
    HEALTH_AI_NO_SYMPTOM("B000412", "还没有症状记录，先记一条症状再做 AI 解读"),
    HEALTH_AI_SERVICE_ERROR("B000413", "AI 解读服务暂不可用，请稍后重试"),
    ;

    private final String code;

    private final String message;

    HealthyErrorCodeEnum(String code, String message) {
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