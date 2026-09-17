package com.MapleLeaf.MiaoWang.enums;


/**
 * 严重程度（过敏史 / 症状日记共用）
 * 1/2/3 这种魔法数字散在代码里最容易对接出错，统一用枚举转换
 */
public enum HealthSeverityEnum {

    MILD(1, "轻度"),
    MODERATE(2, "中度"),
    SEVERE(3, "重度"),
    ;

    private final Integer severity;

    private final String desc;

    HealthSeverityEnum(Integer severity, String desc) {
        this.severity = severity;
        this.desc = desc;
    }

    public Integer getSeverity() {
        return severity;
    }

    public String getDesc() {
        return desc;
    }

    public static String descOf(Integer severity) {
        if (severity == null) {
            return null;
        }
        for (HealthSeverityEnum item : values()) {
            if (item.severity.equals(severity)) {
                return item.desc;
            }
        }
        return "未知";
    }
}