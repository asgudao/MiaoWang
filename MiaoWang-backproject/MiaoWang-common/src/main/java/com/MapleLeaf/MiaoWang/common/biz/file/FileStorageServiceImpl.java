package com.MapleLeaf.MiaoWang.common.biz.file;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.config.MinioProperties;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.common.enums.FileBizTypeEnum;
import com.MapleLeaf.MiaoWang.common.enums.FileErrorCodeEnum;
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
public class FileStorageServiceImpl implements FileStorageService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @Override
    public FileUploadRespDTO uploadImage(MultipartFile file, FileBizTypeEnum bizType) {
        if (file == null || file.isEmpty()) {
            throw new ClientException(FileErrorCodeEnum.FILE_EMPTY);
        }
        // 1. 扩展名白名单校验（不能只看 Content-Type，前端可以伪造）
        String extension = StrUtil.emptyToDefault(FileUtil.extName(file.getOriginalFilename()), "")
                .toLowerCase(Locale.ROOT);
        List<String> allowedExtensions = StrUtil.split(minioProperties.getAllowedExtensions(), ',').stream()
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
        if (!allowedExtensions.contains(extension)) {
            throw new ClientException(FileErrorCodeEnum.FILE_TYPE_NOT_ALLOWED);
        }
        // 2. 大小校验（容器层 spring.servlet.multipart.max-file-size 已先拦一道，这里再兜一道）
        long maxSizeBytes = (long) minioProperties.getMaxSizeMb() * 1024 * 1024;
        if (file.getSize() > maxSizeBytes) {
            throw new ClientException("文件大小超出限制，单个最大 " + minioProperties.getMaxSizeMb() + "MB",
                    FileErrorCodeEnum.FILE_SIZE_EXCEED);
        }
        // 3. 按业务取桶（各业务独立桶，不混在一起）
        String bucket = resolveBucket(bizType);
        // 4. 业务目录 + 日期分目录，避免单目录对象过多；UUID 防重名覆盖
        String objectKey = bizType.getObjectDir()
                + DateUtil.format(new Date(), "yyyy/MM/dd") + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        ensureBucket(bucket);
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            log.error("MinIO 图片上传失败, bucket={}, objectKey={}", bucket, objectKey, e);
            throw new ClientException(FileErrorCodeEnum.FILE_UPLOAD_ERROR);
        }
        FileUploadRespDTO respDTO = new FileUploadRespDTO();
        respDTO.setObjectKey(objectKey);
        respDTO.setUrl(buildPublicUrl(bucket, objectKey));
        return respDTO;
    }

    /**
     * 按业务类型取桶名：minio.buckets.&lt;业务标识&gt;
     */
    private String resolveBucket(FileBizTypeEnum bizType) {
        String bucket = minioProperties.getBuckets().get(bizType.getBucketKey());
        if (StrUtil.isBlank(bucket)) {
            log.error("未配置对象存储桶, bizType={}, bucketKey={}", bizType, bizType.getBucketKey());
            throw new ClientException(FileErrorCodeEnum.FILE_BUCKET_NOT_CONFIGURED);
        }
        return bucket.trim();
    }

    /**
     * 桶不存在就建，并把桶策略设为公共读
     * <p>
     * 之所以用"公共读 + 直链"而不是预签名 URL：URL 要存进 MySQL 长期使用，
     * 预签名 URL 几小时后会 403，历史图片会集体失效。
     */
    private void ensureBucket(String bucket) {
        if (!Boolean.TRUE.equals(minioProperties.getAutoCreateBucket())) {
            return;
        }
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

    private String buildPublicUrl(String bucket, String objectKey) {
        String baseUrl = minioProperties.getPublicBaseUrl();
        if (StrUtil.isBlank(baseUrl)) {
            baseUrl = minioProperties.getEndpoint();
        }
        return StrUtil.removeSuffix(baseUrl, "/") + "/" + bucket + "/" + objectKey;
    }
}