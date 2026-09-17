package com.MapleLeaf.MiaoWang.enums;

import com.MapleLeaf.MiaoWang.common.convention.errorcode.IErrorCode;

/**
 * 社区模块错误码
 * <p>
 * 沿用项目错误码分段习惯：A 段=用户端错误，B 段=系统执行出错。
 * 社区业务错误统一放在 B000300 段（用户模块占用 B000200 段）。
 */
public enum CommunityErrorCodeEnum implements IErrorCode {

    SECTION_NOT_EXIST("B000300", "社区版块不存在或已停用"),
    POST_NOT_EXIST("B000301", "帖子不存在或已被删除"),
    POST_NO_PERMISSION("B000302", "只能操作自己发布的帖子"),
    POST_IMAGE_TOO_MANY("B000303", "一个帖子最多上传 9 张图片"),
    POST_CONTENT_EMPTY("B000304", "帖子内容和图片不能同时为空"),
    POST_SECTION_REQUIRED("B000305", "请选择要发布的版块"),
    IMAGE_SIZE_EXCEED("B000308", "图片大小超出限制"),
    USER_NOT_LOGIN("B000310", "用户未登录或登录已过期"),
    COMMENT_NOT_EXIST("B000311", "评论不存在或已被删除"),
    COMMENT_NO_PERMISSION("B000312", "只有评论作者或该帖楼主可以删除这条评论"),
    COMMENT_CONTENT_EMPTY("B000313", "评论内容不能为空"),
    COMMENT_CONTENT_TOO_LONG("B000314", "评论内容过长"),
    COMMENT_PARENT_INVALID("B000315", "被回复的评论不存在或不属于该帖子"),
    FOLLOW_SELF("B000316", "不能关注自己"),
    NOTIFICATION_NOT_EXIST("B000317", "通知不存在或无权操作"),
    SENSITIVE_WORD("B000318", "内容包含敏感词，请修改后再发布"),
    REPORT_TARGET_INVALID("B000319", "举报目标不存在或已被删除"),
    REPORT_SELF("B000320", "不能举报自己发布的内容"),
    REPORT_REASON_INVALID("B000321", "举报理由不合法"),
    REPORT_REASON_TOO_LONG("B000322", "举报补充说明过长"),
    REPORT_DUPLICATE("B000323", "你已经举报过这条内容，正在处理中"),
    BLOCK_SELF("B000324", "不能拉黑自己"),
    BLOCK_USER_INVALID("B000325", "要拉黑的用户不存在"),
    TOPIC_NOT_EXIST("B000326", "话题不存在"),
    SEARCH_KEYWORD_INVALID("B000327", "搜索关键词为空或过长"),
    VIDEO_FORMAT_INVALID("B000328", "视频格式不支持，仅支持 mp4/mov"),
    VIDEO_FILE_EMPTY("B000329", "上传的视频文件不能为空"),
    VIDEO_SIZE_EXCEED("B000330", "视频大小超出限制"),
    VIDEO_PARAM_INVALID("B000331", "视频参数不完整，请重新上传后再提交"),
    POST_VIDEO_TOO_MANY("B000332", "一个帖子最多上传 9 个视频"),
    ;

    private final String code;

    private final String message;

    CommunityErrorCodeEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}