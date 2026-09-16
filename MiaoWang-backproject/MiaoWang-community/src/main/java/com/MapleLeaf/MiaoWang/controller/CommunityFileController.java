package com.MapleLeaf.MiaoWang.controller;

import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.resp.ImageUploadRespDTO;
import com.MapleLeaf.MiaoWang.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 社区文件上传接口（图片存 MinIO）
 */
@RestController
@RequiredArgsConstructor
public class CommunityFileController {

    private final FileService fileService;

    /**
     * 上传帖子图片
     * <p>
     * 单张上传，返回 url + objectKey；前端选 9 张就调 9 次（串行或并发都行），
     * 好处是单张失败不用整批重传（视频后期接入时同样走这个口子）。
     */
    @PostMapping("/mapleleaf/miaowang/v1/community/file/image")
    public Result<ImageUploadRespDTO> uploadImage(@RequestParam("file") MultipartFile file) {
        return Results.success(fileService.uploadImage(file));
    }
}