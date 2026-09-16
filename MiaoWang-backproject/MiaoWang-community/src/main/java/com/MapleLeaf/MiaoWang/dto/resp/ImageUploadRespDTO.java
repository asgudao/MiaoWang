package com.MapleLeaf.MiaoWang.dto.resp;

import lombok.Data;

/**
 * 图片上传返回
 * <p>
 * objectKey 要原样带回发帖接口，url 直接给前端预览用。
 */
@Data
public class ImageUploadRespDTO {

    /**
     * 图片访问 URL
     */
    private String url;

    /**
     * MinIO 对象 key
     */
    private String objectKey;
}