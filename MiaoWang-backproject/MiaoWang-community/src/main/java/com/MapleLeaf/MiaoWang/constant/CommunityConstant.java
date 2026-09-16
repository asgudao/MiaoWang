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

    // ==================== MinIO 对象 key ====================

    /**
     * 社区帖子图片的对象 key 前缀
     */
    public static final String POST_IMAGE_PREFIX = "community/post/";

    /**
     * 未删除
     */
    public static final Integer DEL_FLAG_NORMAL = 0;

    /**
     * 已删除
     */
    public static final Integer DEL_FLAG_DELETED = 1;
}