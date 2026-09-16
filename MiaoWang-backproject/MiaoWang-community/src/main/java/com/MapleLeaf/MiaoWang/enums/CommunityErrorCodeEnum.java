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
    IMAGE_EMPTY("B000306", "上传的图片不能为空"),
    IMAGE_TYPE_NOT_ALLOWED("B000307", "图片格式不支持，仅支持 jpg/jpeg/png/webp"),
    IMAGE_SIZE_EXCEED("B000308", "图片大小超出限制"),
    IMAGE_UPLOAD_ERROR("B000309", "图片上传失败，请稍后重试"),
    USER_NOT_LOGIN("B000310", "用户未登录或登录已过期"),
    COMMENT_NOT_EXIST("B000311", "评论不存在或已被删除"),
    COMMENT_NO_PERMISSION("B000312", "只能删除自己的评论"),
    COMMENT_CONTENT_EMPTY("B000313", "评论内容不能为空"),
    COMMENT_CONTENT_TOO_LONG("B000314", "评论内容过长"),
    COMMENT_PARENT_INVALID("B000315", "被回复的评论不存在或不属于该帖子"),
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