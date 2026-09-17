package com.MapleLeaf.MiaoWang.service.impl;

import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.config.MinioProperties;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.common.enums.FileErrorCodeEnum;
import com.MapleLeaf.MiaoWang.config.CommunityVideoProperties;
import com.MapleLeaf.MiaoWang.dto.resp.VideoUploadRespDTO;
import com.MapleLeaf.MiaoWang.enums.CommunityErrorCodeEnum;
import com.MapleLeaf.MiaoWang.service.VideoStorageService;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 社区视频上传（MinIO）
 * <p>
 * 为什么不直接复用 common 的 {@code FileStorageService}：它的扩展名白名单与大小上限是
 * <b>图片口径</b>（minio.allowed-extensions / minio.max-size-mb），视频塞进去要么被拒、要么
 * 为了视频把图片的闸门一起放宽。所以视频在社区自己这层做校验，只复用同一个 MinioClient
 * 与同一个桶（{@code minio.buckets.post}），对象目录换成 {@code community/video/}。
 * <p>
 * 校验口径（刻意的取舍）：<b>扩展名是主判据</b>，content-type 只记告警不拒绝 ——
 * 部分安卓机型录出来的视频 content-type 是 application/octet-stream，
 * 只按 content-type 卡会把正常用户挡在门外。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioVideoStorageServiceImpl implements VideoStorageService {

    /**
     * 扩展名 → content-type：写进对象元数据后，浏览器才能直接内联播放，
     * 否则会变成下载（application/octet-stream）
     */
    private static final Map<String, String> CONTENT_TYPE_MAP = Map.of(
            "mp4", "video/mp4",
            "mov", "video/quicktime");

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final CommunityVideoProperties videoProperties;

    @Override
    public VideoUploadRespDTO uploadVideo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ClientException(CommunityErrorCodeEnum.VIDEO_FILE_EMPTY);
        }
        String originalFilename = StrUtil.blankToDefault(file.getOriginalFilename(), "");
        String extension = StrUtil.subAfter(originalFilename, ".", true).toLowerCase(Locale.ROOT);
        List<String> allowedExtensions = Arrays.stream(
                        StrUtil.blankToDefault(videoProperties.getAllowedExtensions(), "").split(","))
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
        if (StrUtil.isBlank(extension) || !allowedExtensions.contains(extension)) {
            throw new ClientException(CommunityErrorCodeEnum.VIDEO_FORMAT_INVALID);
        }
        String contentType = file.getContentType();
        if (StrUtil.isNotBlank(contentType) && !contentType.toLowerCase(Locale.ROOT).startsWith("video/")) {
            log.warn("视频 content-type 不是 video/*，按扩展名放行, contentType={}, filename={}", contentType, originalFilename);
        }
        long maxSizeBytes = (long) videoProperties.getMaxSizeMb() * 1024 * 1024;
        if (file.getSize() > maxSizeBytes) {
            throw new ClientException(CommunityErrorCodeEnum.VIDEO_SIZE_EXCEED);
        }
        String bucket = resolveBucket();
        ensureBucket(bucket);
        // 对象 key 带日期目录：出问题时能按天定位，也方便将来按前缀批量清理
        String objectKey = videoProperties.getObjectDir()
                + LocalDate.now().format(DAY_FORMAT) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(CONTENT_TYPE_MAP.getOrDefault(extension, "application/octet-stream"))
                    .build());
        } catch (Exception e) {
            log.error("MinIO 视频上传失败, bucket={}, objectKey={}", bucket, objectKey, e);
            // 第三方依赖失败沿用 common 的文件错误码段（C 段 = 外部依赖），不自己造一个 B0003xx
            throw new ClientException(FileErrorCodeEnum.FILE_UPLOAD_ERROR);
        }
        VideoUploadRespDTO respDTO = new VideoUploadRespDTO();
        respDTO.setUrl(buildPublicUrl(bucket, objectKey));
        respDTO.setObjectKey(objectKey);
        respDTO.setSize(file.getSize());
        respDTO.setFormat(extension);
        return respDTO;
    }

    /**
     * 按配置取桶名：minio.buckets.&lt;bucketKey&gt;，取不到就退回社区桶默认名
     */
    private String resolveBucket() {
        Map<String, String> buckets = minioProperties.getBuckets();
        String bucket = buckets == null ? null : buckets.get(videoProperties.getBucketKey());
        return StrUtil.blankToDefault(bucket, "miaowang-community");
    }

    /**
     * 桶不存在时创建并设成公共读；关掉 auto-create 就直接跳过，交给运维预建
     */
    private void ensureBucket(String bucket) {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (exists) {
                return;
            }
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(bucket)
                    .config(publicReadPolicy(bucket))
                    .build());
            log.info("已创建 MinIO 桶并设为公共读, bucket={}", bucket);
        } catch (Exception e) {
            // 桶名不存在这类问题不该直接 500：真正的问题会在 putObject 时以业务错误码暴露
            log.warn("检查/创建 MinIO 桶失败，继续尝试上传, bucket={}", bucket, e);
        }
    }

    /**
     * 公共读策略：桶里的对象只读、可列出，和图片桶保持一致（前端要能直接 <video src> 播放）
     */
    private String publicReadPolicy(String bucket) {
        return "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\","
                + "\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],"
                + "\"Resource\":[\"arn:aws:s3:::" + bucket + "/*\"]}]}";
    }

    /**
     * 拼直链：publicBaseUrl 不配就用 endpoint
     */
    private String buildPublicUrl(String bucket, String objectKey) {
        String baseUrl = StrUtil.blankToDefault(minioProperties.getPublicBaseUrl(), minioProperties.getEndpoint());
        return StrUtil.removeSuffix(baseUrl, "/") + "/" + bucket + "/" + objectKey;
    }
}