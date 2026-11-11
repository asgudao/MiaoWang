package com.MapleLeaf.MiaoWang.enums;


/**
 * 会员档位编码
 * 只做代码里的常量引用（例如兜底取 FREE 档），
 * 价格与权益次数一律以 miaowang_vip_plan 表为准，不在代码里写死
 */
public enum VipPlanCodeEnum {

    FREE("FREE", "免费用户"),
    PET_49("PET_49", "宠物位扩容月卡"),
    AI_149("AI_149", "AI 基础月卡"),
    AI_249("AI_249", "AI 进阶月卡"),
    AI_349("AI_349", "AI 畅享月卡"),
    ALL_400("ALL_400", "全能月卡"),
    /**
     * 宠物级档位（{@code pet_scoped=1}）：绑定单只已离世的宠物，365 天，不可续。
     *
     * <p>它不参与用户级权益合并，也不会出现在通用档位列表里
     * （列表接口按 {@code pet_scoped=0} 过滤）——「纪念」功能只在宠物离世后才出现。
     */
    MEMORIAL_100("MEMORIAL_100", "宠物纪念年包"),
    ;

    private final String code;

    private final String name;

    VipPlanCodeEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}