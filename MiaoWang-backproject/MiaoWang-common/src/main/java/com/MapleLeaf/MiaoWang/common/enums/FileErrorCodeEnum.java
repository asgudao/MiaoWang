package com.MapleLeaf.MiaoWang.common.enums;

import com.MapleLeaf.MiaoWang.common.convention.errorcode.IErrorCode;

/**
 * 文件上传错误码（common 统一，各业务服务复用）
 * <p>
 * 分段沿用项目习惯：
 * A 段 = 客户端错误（文件本身不合法，用户自己改就行）
 * B 段 = 服务端配置/执行问题
 * C 段 = 调用第三方服务出错（这里是 MinIO）
 */
public enum FileErrorCodeEnum implements IErrorCode {

    FILE_EMPTY("A000600", "上传的文件不能为空"),
    FILE_TYPE_NOT_ALLOWED("A000601", "文件格式不支持，仅支持 jpg/jpeg/png/webp"),
    FILE_SIZE_EXCEED("A000602", "文件大小超出限制"),

    FILE_BUCKET_NOT_CONFIGURED("B000600", "未配置对象存储桶，请联系管理员"),

    FILE_UPLOAD_ERROR("C000100", "文件上传失败，请稍后重试"),
    ;

    private final String code;

    private final String message;

    FileErrorCodeEnum(String code, String message) {
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