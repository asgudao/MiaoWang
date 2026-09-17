package com.MapleLeaf.MiaoWang.enums;


/**
 * 健康记录类型
 * 用于「每类记录条数上限」的额度校验与时间线聚合展示
 */
public enum HealthRecordTypeEnum {

    PREVENTIVE("preventive", "疫苗/驱虫"),
    MEDICAL("medical", "就诊"),
    MEDICATION("medication", "用药"),
    ALLERGY("allergy", "过敏史"),
    SYMPTOM("symptom", "症状日记"),
    WEIGHT("weight", "体重"),
    ;

    private final String type;

    private final String desc;

    HealthRecordTypeEnum(String type, String desc) {
        this.type = type;
        this.desc = desc;
    }

    public String getType() {
        return type;
    }

    public String getDesc() {
        return desc;
    }

    public static HealthRecordTypeEnum of(String type) {
        for (HealthRecordTypeEnum item : values()) {
            if (item.type.equalsIgnoreCase(type)) {
                return item;
            }
        }
        return null;
    }
}