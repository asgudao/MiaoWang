package com.MapleLeaf.MiaoWang.enums;


/**
 * 额度周期：决定「每日次数」还是「每月次数」，以及 Redis 计数 key 的归档粒度
 */
public enum VipQuotaPeriodEnum {

    /**
     * 每日额度：key 按天归档（yyyyMMdd），当天到期
     */
    DAY("DAY", "每日"),

    /**
     * 每月额度：key 按月归档（yyyyMM），当月到期（免费档试用额度用这个）
     */
    MONTH("MONTH", "每月"),
    ;

    private final String period;

    private final String desc;

    VipQuotaPeriodEnum(String period, String desc) {
        this.period = period;
        this.desc = desc;
    }

    public String getPeriod() {
        return period;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 解析周期，不认识返回 null（由调用方兜底为 DAY）
     */
    public static VipQuotaPeriodEnum of(String period) {
        for (VipQuotaPeriodEnum item : values()) {
            if (item.period.equalsIgnoreCase(period)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 兜底解析：配置写错时按 DAY 处理，保证额度服务不因脏数据不可用
     */
    public static VipQuotaPeriodEnum ofOrDefault(String period) {
        VipQuotaPeriodEnum result = of(period);
        return result == null ? DAY : result;
    }
}