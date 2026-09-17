package com.MapleLeaf.MiaoWang.common.enums;

/**
 * 文件业务类型
 * <p>
 * 一个枚举同时决定两件事：<b>存哪个桶</b>、<b>对象 key 放哪个目录</b>。
 * 之所以不把所有图片都塞进同一个桶：不同业务的容量、权限策略、生命周期、
 * 清理策略都不一样（头像能随便覆盖，社区帖子图片要留证），分桶后互不影响。
 */
public enum FileBizTypeEnum {

    /**
     * 用户头像：桶 minio.buckets.avatar，对象目录 user/avatar/
     */
    AVATAR("avatar", "user/avatar/"),

    /**
     * 社区帖子图片：桶 minio.buckets.post，对象目录 community/post/
     */
    POST_IMAGE("post", "community/post/"),

    /**
     * 健康档案图片（化验单、影像、症状照片）：桶 minio.buckets.health，对象目录 health/record/
     */
    HEALTH_IMAGE("health", "health/record/"),
    ;

    /**
     * 业务桶标识：对应配置 minio.buckets.&lt;bucketKey&gt;
     */
    private final String bucketKey;

    /**
     * 对象 key 目录前缀
     */
    private final String objectDir;

    FileBizTypeEnum(String bucketKey, String objectDir) {
        this.bucketKey = bucketKey;
        this.objectDir = objectDir;
    }

    public String getBucketKey() {
        return bucketKey;
    }

    public String getObjectDir() {
        return objectDir;
    }
}