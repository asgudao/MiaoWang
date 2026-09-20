package com.MapleLeaf.MiaoWang.persona;

import java.time.LocalDateTime;

/**
 * 一天里的时段划分 —— 自动问候的"时间感"来源。
 *
 * <p>两个用途，必须是同一份定义（否则会出现"缓存说现在算早上、提示词说现在是下午"的错位）：
 * <ol>
 *   <li>拼问候提示词：让宠物说得出"早呀""这么晚还不睡"</li>
 *   <li>拼缓存 key：<b>一个时段最多生成一次问候</b>，这是自动问候不烧钱的关键</li>
 * </ol>
 *
 * <p>分段刻意与前端首页那句「早上好，主人」保持一致（前端的 greet 计算在两端页面里），
 * 用户看到的两句问候才不会互相打架。
 */
public enum TimeSlot {

    /** 0~5 点 */
    NIGHT("night", "深夜"),

    /** 6~10 点 */
    MORNING("morning", "早上"),

    /** 11~13 点 */
    NOON("noon", "中午"),

    /** 14~17 点 */
    AFTERNOON("afternoon", "下午"),

    /** 18~23 点 */
    EVENING("evening", "晚上");

    private final String key;

    private final String label;

    TimeSlot(String key, String label) {
        this.key = key;
        this.label = label;
    }

    /**
     * 用于缓存 key 的短标识（英文，避免中文进 Redis key 后的转义烦恼）
     */
    public String key() {
        return key;
    }

    /**
     * 说给人话/模型听的时段名（深夜/早上/中午/下午/晚上）
     */
    public String label() {
        return label;
    }

    /**
     * 按小时判断时段
     */
    public static TimeSlot of(LocalDateTime time) {
        int hour = time.getHour();
        if (hour < 6) {
            return NIGHT;
        }
        if (hour < 11) {
            return MORNING;
        }
        if (hour < 14) {
            return NOON;
        }
        if (hour < 18) {
            return AFTERNOON;
        }
        return EVENING;
    }

    /**
     * 当前时段开始的那一刻（用于判断"这个时段宠物是不是已经开过口了"）
     */
    public LocalDateTime startOf(LocalDateTime time) {
        LocalDateTime dayStart = time.toLocalDate().atStartOfDay();
        return switch (this) {
            case NIGHT -> dayStart;
            case MORNING -> dayStart.plusHours(6);
            case NOON -> dayStart.plusHours(11);
            case AFTERNOON -> dayStart.plusHours(14);
            case EVENING -> dayStart.plusHours(18);
        };
    }

    /**
     * 当前时段结束的那一刻（用于把问候缓存的 TTL 精确卡到时段边界：
     * 时段内再进首页一律命中缓存，跨到下一个时段才允许重新生成）
     */
    public LocalDateTime endOf(LocalDateTime time) {
        LocalDateTime dayStart = time.toLocalDate().atStartOfDay();
        return switch (this) {
            case NIGHT -> dayStart.plusHours(6);
            case MORNING -> dayStart.plusHours(11);
            case NOON -> dayStart.plusHours(14);
            case AFTERNOON -> dayStart.plusHours(18);
            case EVENING -> dayStart.plusDays(1);
        };
    }
}