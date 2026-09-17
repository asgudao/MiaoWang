package com.MapleLeaf.MiaoWang.common.constant;


/**
 * 喵汪 Redis 缓存 key 常量类
 */
public class RedisCacheConstant {

    public static final String LOCK_USER_REGISTER_KEY = "MiaoWang:lock_user-register:";

    // ==================== 登录态 ====================

    /**
     * 登录态：MiaoWang_login_&lt;用户名&gt;
     * <p>
     * hash 结构：field = JWT token，value = UserInfoDTO 的 JSON
     */
    public static final String USER_LOGIN_KEY = "MiaoWang_login_";

    // ==================== 图形验证码 / 短信验证码 ====================

    /**
     * 图形验证码：MiaoWang:captcha:&lt;captchaKey&gt;，value = 验证码文本
     */
    public static final String CAPTCHA_KEY = "MiaoWang:captcha:";

    /**
     * 短信验证码：MiaoWang:sms:code:&lt;场景&gt;:&lt;手机号&gt;，value = 6 位数字码
     * <p>
     * 带场景是为了"注册的码不能拿来登录"：两个场景各存一份，互不通用。
     */
    public static final String SMS_CODE_KEY = "MiaoWang:sms:code:";

    /**
     * 短信校验失败次数：MiaoWang:sms:fail:&lt;场景&gt;:&lt;手机号&gt;
     * <p>
     * 6 位数字码只有 100 万种组合，不限次数就能被脚本暴力试出来，所以失败次数要计数封顶。
     */
    public static final String SMS_FAIL_KEY = "MiaoWang:sms:fail:";

    /**
     * 短信发送间隔限制：MiaoWang:sms:limit:&lt;手机号&gt;，存在即表示还在冷却期
     */
    public static final String SMS_SEND_LIMIT_KEY = "MiaoWang:sms:limit:";

    /**
     * 短信每日条数：MiaoWang:sms:daily:&lt;手机号&gt;，value = 当日已发条数（自然日过期）
     */
    public static final String SMS_DAILY_LIMIT_KEY = "MiaoWang:sms:daily:";

    // ==================== 知识库 ====================

    /**
     * 知识碎片浏览量增量：MiaoWang:knowledge:view:&lt;碎片id&gt;，value = 还没写回 MySQL 的增量
     * <p>
     * 详情页每读一次只做一次 INCR（不碰数据库），由 knowledge 服务的定时任务
     * {@code KnowledgeViewCountFlushTask} 合并写回，避免"读放大变写放大"。
     */
    public static final String KNOWLEDGE_VIEW_KEY = "MiaoWang:knowledge:view:";
}