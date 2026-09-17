package com.MapleLeaf.MiaoWang.enums;


/**
 * 疫苗/驱虫记录类型
 * 两者字段结构一致，所以合并成一张表，用这个枚举区分
 */
public enum PreventiveTypeEnum {

    /**
     * 疫苗
     */
    VACCINE("VACCINE", "疫苗"),

    /**
     * 体内驱虫
     */
    DEWORM_IN("DEWORM_IN", "体内驱虫"),

    /**
     * 体外驱虫
     */
    DEWORM_OUT("DEWORM_OUT", "体外驱虫"),
    ;

    private final String type;

    private final String desc;

    PreventiveTypeEnum(String type, String desc) {
        this.type = type;
        this.desc = desc;
    }

    public String getType() {
        return type;
    }

    public String getDesc() {
        return desc;
    }

    public static PreventiveTypeEnum of(String type) {
        for (PreventiveTypeEnum item : values()) {
            if (item.type.equalsIgnoreCase(type)) {
                return item;
            }
        }
        return null;
    }

    public static String descOf(String type) {
        PreventiveTypeEnum item = of(type);
        return item == null ? "其他" : item.getDesc();
    }
}