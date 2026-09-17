package com.MapleLeaf.MiaoWang.controller;

import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.resp.ImageUploadRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VideoUploadRespDTO;
import com.MapleLeaf.MiaoWang.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 社区文件上传接口（图片 / 视频存 MinIO）
 */
@RestController
@RequiredArgsConstructor
public class CommunityFileController {

    private final FileService fileService;

    /**
     * 上传帖子图片
     * <p>
     * 单张上传，返回 url + objectKey；前端选 9 张就调 9 次（串行或并发都行），
     * 好处是单张失败不用整批重传。
     */
    @PostMapping("/mapleleaf/miaowang/v1/community/file/image")
    public Result<ImageUploadRespDTO> uploadImage(@RequestParam("file") MultipartFile file) {
        return Results.success(fileService.uploadImage(file));
    }

    /**
     * 上传帖子视频（mp4 / mov，单个 ≤ community.video.max-size-mb，默认 100MB）
     * <p>
     * 一帖最多一个视频，前端先传这里拿到 url + objectKey，再随发帖/编辑提交到 video 字段。
     * 分了两个接口而不是一个带 type 的接口：图片与视频的校验口径（扩展名、大小、目录）
     * 完全不同，合成一个接口的后果是参数校验里长出一堆 if。
     */
    @PostMapping("/mapleleaf/miaowang/v1/community/file/video")
    public Result<VideoUploadRespDTO> uploadVideo(@RequestParam("file") MultipartFile file) {
        return Results.success(fileService.uploadVideo(file));
    }
}