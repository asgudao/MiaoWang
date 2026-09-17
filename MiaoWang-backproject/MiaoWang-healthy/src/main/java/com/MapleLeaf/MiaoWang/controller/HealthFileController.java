package com.MapleLeaf.MiaoWang.controller;


import com.MapleLeaf.MiaoWang.common.biz.file.FileStorageService;
import com.MapleLeaf.MiaoWang.common.biz.file.FileUploadRespDTO;
import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.common.enums.FileBizTypeEnum;
import com.MapleLeaf.MiaoWang.support.HealthUserHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 健康档案图片上传（化验单、影像、症状照片）
 *
 * <p>复用 common 的 {@link FileStorageService}：健康模块不关心用的是 MinIO 还是别的存储，
 * 上传后拿到 URL 存进就诊记录的 attachments 或症状日记的 images 字段。
 *
 * <p>⚠️ 隐私提醒：common 的当前实现是「公共读桶 + 直链」，适合头像/帖子图片，
 * 但化验单属于敏感数据 —— 正式上线应换成私有桶 + 带签名的临时 URL
 * （代价是签名会过期，所以入库要存 objectKey 而不是 URL）。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/mapleleaf/miaowang/v1/health")
public class HealthFileController {

    private final FileStorageService fileStorageService;

    /**
     * 上传图片，返回访问 URL 与对象 key
     * 先确认登录身份再落文件，避免成为公开的图床
     */
    @PostMapping("/file")
    public Result<FileUploadRespDTO> upload(@RequestPart("file") MultipartFile file) {
        HealthUserHelper.currentUid();
        return Results.success(fileStorageService.uploadImage(file, FileBizTypeEnum.HEALTH_IMAGE));
    }
}