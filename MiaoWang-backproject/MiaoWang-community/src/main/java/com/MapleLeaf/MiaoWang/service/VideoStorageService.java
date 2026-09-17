package com.MapleLeaf.MiaoWang.service;

import com.MapleLeaf.MiaoWang.dto.resp.VideoUploadRespDTO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 社区视频上传
 */
public interface VideoStorageService {

    /**
     * 上传一个帖子视频，返回可播放的 URL 与对象 key
     *
     * @param file 视频文件（mp4 / mov）
     */
    VideoUploadRespDTO uploadVideo(MultipartFile file);
}