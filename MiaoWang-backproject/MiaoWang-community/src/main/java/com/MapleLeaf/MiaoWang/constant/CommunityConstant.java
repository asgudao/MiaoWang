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