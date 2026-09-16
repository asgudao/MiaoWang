package com.MapleLeaf.MiaoWang.web;

import com.MapleLeaf.MiaoWang.common.convention.errorcode.BaseErrorCode;
import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.config.MinioProperties;
import com.MapleLeaf.MiaoWang.enums.CommunityErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 社区模块的补充异常处理
 * <p>
 * 为什么需要它：图片超过限制时会被容器层（spring.servlet.multipart）先拦下，
 * 公共模块的 GlobalExceptionHandler 只兜到 Throwable，前端只会看到笼统的"系统执行出错"。
 * 这里给出明确的错误码和提示。
 * <p>
 * @Order(HIGHEST_PRECEDENCE) 必须加：异常解析会按 advice 顺序找"第一个能处理的"，
 * 不加的话公共 advice 里的 Throwable 处理器会先把异常兜走，这个更精确的处理器就没机会生效。
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
@RequiredArgsConstructor
public class CommunityExceptionHandler {

    private final MinioProperties minioProperties;

    /**
     * 上传文件超过 multipart 限制
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> maxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        log.warn("上传文件超出限制: {}", ex.getMessage());
        return Results.failure(CommunityErrorCodeEnum.IMAGE_SIZE_EXCEED.code(),
                "图片大小超出限制，单张最大 " + minioProperties.getMaxSizeMb() + "MB");
    }

    /**
     * 请求体 JSON 解析失败
     * <p>
     * 不处理的话会掉进公共 advice 的 Throwable 分支，返回"系统执行出错"（B 段 = 服务端错了），
     * 但这其实是调用方内容有问题，应该按 A 段客户端错误返回，前端才不会误判成服务端故障。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> httpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("请求体解析失败: {}", ex.getMessage());
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), "请求体格式不正确，请检查 JSON");
    }
}