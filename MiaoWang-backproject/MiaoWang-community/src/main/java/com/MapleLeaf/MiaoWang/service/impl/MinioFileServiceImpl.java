package com.MapleLeaf.MiaoWang.service.impl;

import com.MapleLeaf.MiaoWang.common.biz.file.FileStorageService;
import com.MapleLeaf.MiaoWang.common.biz.file.FileUploadRespDTO;
import com.MapleLeaf.MiaoWang.common.enums.FileBizTypeEnum;
import com.MapleLeaf.MiaoWang.dto.resp.ImageUploadRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VideoUploadRespDTO;
import com.MapleLeaf.MiaoWang.service.FileService;
import com.MapleLeaf.MiaoWang.service.VideoStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 社区文件上传
 * <p>
 * 图片：上传能力已统一到 common 的 {@link FileStorageService}，
 * 校验、分桶、对象目录、直链 URL 拼装都在那边，社区这层只做
 * "业务类型（帖子图片） → 社区自己的 DTO" 的适配。
 * <p>
 * 视频：走社区自己的 {@link VideoStorageService}（common 的扩展名白名单是图片口径，视频塞不进去），
 * 这里只做转发，让上传入口对上层保持一致。
 */
@Service
@RequiredArgsConstructor
public class MinioFileServiceImpl implements FileService {

    private final FileStorageService fileStorageService;
    private final VideoStorageService videoStorageService;

    @Override
    public ImageUploadRespDTO uploadImage(MultipartFile file) {
        FileUploadRespDTO uploadResult = fileStorageService.uploadImage(file, FileBizTypeEnum.POST_IMAGE);
        ImageUploadRespDTO respDTO = new ImageUploadRespDTO();
        respDTO.setUrl(uploadResult.getUrl());
        respDTO.setObjectKey(uploadResult.getObjectKey());
        return respDTO;
    }

    @Override
    public VideoUploadRespDTO uploadVideo(MultipartFile file) {
        return videoStorageService.uploadVideo(file);
    }
}