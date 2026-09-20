package com.MapleLeaf.MiaoWang.constant;

/**
 * 社区模块常量
 */
public final class CommunityConstant {

    private CommunityConstant() {
    }

    // ==================== 帖子状态 ====================

    /**
     * 草稿
     */
    public static final Integer POST_STATUS_DRAFT = 0;

    /**
     * 待审核
     */
    public static final Integer POST_STATUS_AUDITING = 1;

    /**
     * 已发布
     * <p>
     * 当前没有接审核环节，发帖即发布（status=2）；
     * 以后接入机器/人工审核时，把发帖默认值改成 1（待审核）即可，查询条件不用动。
     */
    public static final Integer POST_STATUS_PUBLISHED = 2;

    /**
     * 已驳回
     */
    public static final Integer POST_STATUS_REJECTED = 3;

    /**
     * 已下架
     */
    public static final Integer POST_STATUS_OFFLINE = 4;

    // ==================== 互动目标类型（点赞/收藏多态） ====================

    /**
     * 目标类型：帖子
     */
    public static final Integer TARGET_TYPE_POST = 1;

    /**
     * 目标类型：评论
     */
    public static final Integer TARGET_TYPE_COMMENT = 2;

    // ==================== 发帖限制 ====================

    /**
     * 一个帖子最多上传 9 张图片（服务层校验；数据库层不做强制）
     */
    public static final int MAX_POST_IMAGE_COUNT = 9;

    /**
     * 一个帖子最多上传 9 个视频（与图片同口径；视频和图片可以同时存在）
     */
    public static final int MAX_POST_VIDEO_COUNT = 9;

    /**
     * 一个帖子「图片 + 视频」<b>合计</b>最多 9 条（产品口径：卡片九宫格放不下第 10 格）
     * <p>
     * 与 {@link #MAX_POST_IMAGE_COUNT} / {@link #MAX_POST_VIDEO_COUNT} 数值恰好相同但**语义不同**，
     * 故意独立定义（和 {@link #LIST_IMAGE_PREVIEW_COUNT} 同一个理由）：
     * <ul>
     *     <li>那两个是**单一类型**的上限，决定"图片超了报 {@code B000303}、视频超了报 {@code B000332}"；</li>
     *     <li>这个是**两种媒体合起来**的上限，管的是"各占一半、单看都没超、加起来超了"这种情况
     *         （如 5 张图 + 5 个视频）—— 那两个码的文案在这种请求上都是**假话**
     *         （"最多 9 张图片"可用户只发了 5 张），所以必须由 {@code B000333} 承担。</li>
     * </ul>
     * ⚠️ 因此**不能**通过改 {@link #MAX_POST_IMAGE_COUNT} 的数值来实现这条规则：
     * 那会静默改掉 {@code B000303} 的语义与所有引用点（发帖/编辑各一处）。
     * 三个常量都要留着 —— 单类型上限决定"报哪个具体的码"，合计上限决定"合起来有没有超"。
     */
    public static final int MAX_POST_MEDIA_COUNT = 9;

    /**
     * 列表页每条帖子最多带回的图片张数（预览上限，当前 9）
     * <p>
     * 取 9 是因为前端卡片就是九宫格：第 1~9 格铺图、第 9 格上盖 "+N"，
     * 第 10 张起本来就渲染不出来 —— 多传的每一张都是纯流量和内存浪费。
     * <p>
     * 与 {@link #MAX_POST_IMAGE_COUNT} 数值恰好相同但**语义不同**，故意分开定义：
     * 那个是"一帖最多能存几张"（写侧约束），这个是"卡片最多渲染几张"（读侧约束）。
     * 将来放宽发帖张数（例如允许 12 张）时，卡片仍然只需要 9 格，不会被动跟着变大。
     */
    public static final int LIST_IMAGE_PREVIEW_COUNT = 9;

    /**
     * 列表卡片正文摘要最多保留多少个**码点**（code point，不是 UTF-16 char）
     * <p>
     * 取 200 的理由：
     * <ol>
     *     <li>卡片折叠态的正文大概 3~5 行（手机宽 ~20 字/行），200 字正好"看得出在说什么、又明显看不完"，
     *         用户有理由去点"展开"；</li>
     *     <li>一页最多 {@link #MAX_PAGE_SIZE} 帖 → 这一页摘要最多 50×200 字 ≈ 20KB，
     *         相对于"下发全文"（单帖就可能上千字）是可控的固定开销；</li>
     *     <li>存量数据全部落在 200 以内（实测最长 197 字），所以**上线这条规则不会让任何现有帖子的
     *         折叠态文字变短** —— 前端拿摘要当折叠文本用，观感与改动前一致。</li>
     * </ol>
     * ⚠️ 单位是**码点**：emoji / 生僻字占 2 个 char 但算 1 个字，用户看到的"字"最接近码点数；
     * 按 char 数会让"带 200 个 emoji 的帖子"只截出 100 个字。
     */
    public static final int LIST_CONTENT_EXCERPT_LENGTH = 200;

    // ==================== 评论 ====================

    /**
     * 一级评论的 parentId
     */
    public static final Long COMMENT_PARENT_ROOT = 0L;

    /**
     * 评论最大长度（与 miaowang_community_comment.content varchar(1000) 对齐）
     */
    public static final int MAX_COMMENT_LENGTH = 1000;

    /**
     * 评论状态：已发布（当前没有审核环节，发表即发布）
     */
    public static final Integer COMMENT_STATUS_PUBLISHED = 2;

    /**
     * 评论列表里每条一级评论默认带出的回复条数（想看全部走回复的游标接口）
     */
    public static final int COMMENT_REPLY_PREVIEW_COUNT = 3;

    // ==================== 通知 ====================

    /**
     * 通知类型：点赞帖子
     */
    public static final Integer NOTIFY_TYPE_LIKE_POST = 1;

    /**
     * 通知类型：点赞评论
     */
    public static final Integer NOTIFY_TYPE_LIKE_COMMENT = 2;

    /**
     * 通知类型：评论帖子
     */
    public static final Integer NOTIFY_TYPE_COMMENT_POST = 3;

    /**
     * 通知类型：回复评论
     */
    public static final Integer NOTIFY_TYPE_REPLY_COMMENT = 4;

    /**
     * 通知类型：关注
     */
    public static final Integer NOTIFY_TYPE_FOLLOW = 5;

    /**
     * 通知类型：@提及（帖子或评论正文里 @ 到了你）
     */
    public static final Integer NOTIFY_TYPE_MENTION = 6;

    /**
     * 通知未读
     */
    public static final Integer NOTIFY_UNREAD = 0;

    /**
     * 通知已读
     */
    public static final Integer NOTIFY_READ = 1;

    /**
     * 关注类通知没有具体目标对象。
     * <p>
     * target_id / post_id 存 0 而不是 NULL：唯一键 (receiver_uid, type, target_id, actor_uid)
     * 里 NULL 不参与判等，存 NULL 会让"取关再关注"每次都插出新行，去重就失效了。
     */
    public static final Long NOTIFY_TARGET_NONE = 0L;

    /**
     * 通知里内容摘要的最大长度（content 字段 varchar(255)，截断后留足余量）
     */
    public static final int NOTIFY_CONTENT_MAX_LENGTH = 100;

    // ==================== 举报 ====================

    /**
     * 举报处理状态：待处理
     * <p>
     * 当前没有管理员角色与审核后台，举报只落库不判定，所以这一列暂时恒为 0；
     * 处理接口要等角色体系（谁能审）定下来再补。
     */
    public static final Integer REPORT_STATUS_PENDING = 0;

    /**
     * 举报处理状态：已处理（判定违规）
     */
    public static final Integer REPORT_STATUS_ACCEPTED = 1;

    /**
     * 举报处理状态：已驳回（判定未违规）
     */
    public static final Integer REPORT_STATUS_REJECTED = 2;

    /**
     * 举报理由分类最小值：广告引流
     */
    public static final Integer REPORT_REASON_MIN = 1;

    /**
     * 举报理由分类最大值：其他
     */
    public static final Integer REPORT_REASON_MAX = 6;

    /**
     * 举报补充说明最大长度（与 miaowang_community_report.reason varchar(255) 对齐）
     */
    public static final int REPORT_REASON_MAX_LENGTH = 200;

    /**
     * 举报未处理时的处理人 uid（0 = 还没人处理，不用 NULL 是为了排序/统计省心）
     */
    public static final Long REPORT_NO_HANDLER = 0L;

    // ==================== 话题 ====================

    /**
     * 一个帖子最多挂 5 个话题（解析层直接截断，不报错：多写的话题忽略即可）
     */
    public static final int MAX_POST_TOPIC_COUNT = 5;

    /**
     * 话题名最大长度（与 miaowang_community_topic.name varchar(32) 留出余量）
     */
    public static final int TOPIC_NAME_MAX_LENGTH = 20;

    // ==================== @提及 ====================

    /**
     * 一条内容里最多提醒 10 个人（防止有人 @ 一大串刷通知）
     */
    public static final int MAX_MENTION_COUNT = 10;

    // ==================== 搜索 ====================

    /**
     * 搜索关键词最大长度（LIKE 的入参，太长没意义还费 CPU）
     */
    public static final int SEARCH_KEYWORD_MAX_LENGTH = 50;

    // ==================== 分页 ====================

    /**
     * 默认页码
     */
    public static final long DEFAULT_PAGE_NUM = 1L;

    /**
     * 默认每页条数
     */
    public static final long DEFAULT_PAGE_SIZE = 10L;

    /**
     * 每页最大条数（防止前端传超大 pageSize 拖库）
     */
    public static final long MAX_PAGE_SIZE = 50L;

    // ==================== 删除标识 ====================

    /**
     * 未删除
     */
    public static final Integer DEL_FLAG_NORMAL = 0;

    /**
     * 已删除
     */
    public static final Integer DEL_FLAG_DELETED = 1;
}