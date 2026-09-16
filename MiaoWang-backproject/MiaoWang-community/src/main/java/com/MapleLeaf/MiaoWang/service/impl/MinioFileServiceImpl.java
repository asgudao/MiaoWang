package com.MapleLeaf.MiaoWang.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.config.MinioProperties;
import com.MapleLeaf.MiaoWang.constant.CommunityConstant;
import com.MapleLeaf.MiaoWang.dto.resp.ImageUploadRespDTO;
import com.MapleLeaf.MiaoWang.enums.CommunityErrorCodeEnum;
import com.MapleLeaf.MiaoWang.service.FileService;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * MinIO 图片上传实现
 * <p>
 * 存的是"对象 key + 直链 URL"：MySQL 里只留 URL，图片本体在 MinIO。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioFileServiceImpl implements FileService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @Override
    public ImageUploadRespDTO uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ClientException(CommunityErrorCodeEnum.IMAGE_EMPTY);
        }
        // 1. 扩展名白名单校验（不能只看 Content-Type，前端可以伪造）
        String extension = StrUtil.emptyToDefault(FileUtil.extName(file.getOriginalFilename()), "")
                .toLowerCase(Locale.ROOT);
        List<String> allowedExtensions = StrUtil.split(minioProperties.getAllowedExtensions(), ',').stream()
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
        if (!allowedExtensions.contains(extension)) {
            throw new ClientException(CommunityErrorCodeEnum.IMAGE_TYPE_NOT_ALLOWED);
        }
        // 2. 大小校验（容器层 spring.servlet.multipart.max-file-size 已先拦一道，这里再兜一道）
        long maxSizeBytes = (long) minioProperties.getMaxSizeMb() * 1024 * 1024;
        if (file.getSize() > maxSizeBytes) {
            throw new ClientException("图片大小超出限制，单张最大 " + minioProperties.getMaxSizeMb() + "MB",
                    CommunityErrorCodeEnum.IMAGE_SIZE_EXCEED);
        }
        // 3. 按月日分目录，避免单目录对象过多；用 UUID 防重名覆盖
        String objectKey = CommunityConstant.POST_IMAGE_PREFIX
                + DateUtil.format(new Date(), "yyyy/MM/dd") + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        ensureBucket();
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectKey)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            log.error("MinIO 图片上传失败, objectKey={}", objectKey, e);
            throw new ClientException(CommunityErrorCodeEnum.IMAGE_UPLOAD_ERROR);
        }
        ImageUploadRespDTO respDTO = new ImageUploadRespDTO();
        respDTO.setObjectKey(objectKey);
        respDTO.setUrl(buildPublicUrl(objectKey));
        return respDTO;
    }

    /**
     * 桶不存在就建，并把桶策略设为公共读
     * <p>
     * 之所以用"公共读 + 直链"而不是预签名 URL：URL 要存进 MySQL 长期使用，
     * 预签名 URL 几小时后就 403，历史帖子的图片会集体失效。
     */
    private void ensureBucket() {
        if (!Boolean.TRUE.equals(minioProperties.getAutoCreateBucket())) {
            return;
        }
        String bucket = minioProperties.getBucket();
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO 桶不存在，已自动创建：{}", bucket);
            }
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(bucket)
                    .config(publicReadPolicy(bucket))
                    .build());
        } catch (Exception e) {
            // 建桶/设策略失败不影响上传本身（比如账号没有改策略权限），记录日志即可
            log.warn("MinIO 桶检查或公共读策略设置失败, bucket={}", bucket, e);
        }
    }

    private String publicReadPolicy(String bucket) {
        return "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\","
                + "\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],"
                + "\"Resource\":[\"arn:aws:s3:::" + bucket + "/*\"]}]}";
    }

    private String buildPublicUrl(String objectKey) {
        String baseUrl = minioProperties.getPublicBaseUrl();
        if (StrUtil.isBlank(baseUrl)) {
            baseUrl = StrUtil.removeSuffix(minioProperties.getEndpoint(), "/") + "/" + minioProperties.getBucket();
        }
        return StrUtil.removeSuffix(baseUrl, "/") + "/" + objectKey;
    }
}