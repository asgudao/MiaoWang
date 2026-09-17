package com.MapleLeaf.MiaoWang.common.biz.file;

import com.MapleLeaf.MiaoWang.common.enums.FileBizTypeEnum;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储（对象存储实现，当前为 MinIO）
 * <p>
 * common 统一封装，业务服务只管选业务类型，不关心用的是哪个存储、
 * 桶怎么建、URL 怎么拼。
 */
public interface FileStorageService {

    /**
     * 上传图片
     *
     * @param file    图片文件
     * @param bizType 业务类型（决定存哪个桶、对象 key 放哪个目录）
     * @return 访问 URL + 对象 key
     */
    FileUploadRespDTO uploadImage(MultipartFile file, FileBizTypeEnum bizType);
}