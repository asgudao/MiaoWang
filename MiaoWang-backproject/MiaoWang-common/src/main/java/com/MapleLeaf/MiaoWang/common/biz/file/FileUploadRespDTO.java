package com.MapleLeaf.MiaoWang.common.biz.file;

import lombok.Data;

/**
 * 文件上传返回
 * <p>
 * url 直接给前端预览/展示，objectKey 各业务自己决定要不要入库
 * （入库了以后才能删对象，比如换头像时清理旧图、删帖子时清理图片）。
 */
@Data
public class FileUploadRespDTO {

    /**
     * 文件访问 URL
     */
    private String url;

    /**
     * MinIO 对象 key
     */
    private String objectKey;
}