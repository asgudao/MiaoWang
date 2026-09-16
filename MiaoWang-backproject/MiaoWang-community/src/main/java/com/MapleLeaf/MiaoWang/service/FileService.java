package com.MapleLeaf.MiaoWang.service;

import com.MapleLeaf.MiaoWang.dto.resp.ImageUploadRespDTO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储（MinIO）
 */
public interface FileService {

    /**
     * 上传帖子图片，返回访问 URL 与对象 key
     */
    ImageUploadRespDTO uploadImage(MultipartFile file);
}