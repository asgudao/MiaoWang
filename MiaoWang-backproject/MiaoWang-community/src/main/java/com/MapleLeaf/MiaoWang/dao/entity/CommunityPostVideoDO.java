package com.MapleLeaf.MiaoWang.dao.entity;

import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 帖子视频（视频本体存 MinIO，这里只存 URL 和对象 key）
 * <p>
 * 和图片表同构：一帖最多 9 个、sort 决定展示顺序、编辑替换时旧行软删（留 objectKey 给清理任务）。
 * <p>
 * 为什么不把视频并进图片表：两者的桶、扩展名白名单、大小上限、前端播放方式（&lt;img&gt; vs &lt;video&gt;）
 * 都不一样，混一张表后每个查询都要带 type 过滤，还会让"最多 9 张图"这条约束变得含糊。
 */
@Data
@TableName("miaowang_community_post_video")
@EqualsAndHashCode(callSuper = false)
public class CommunityPostVideoDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long videoId;

    /**
     * 所属帖子 id
     */
    private Long postId;

    /**
     * 视频访问 URL（MinIO）
     */
    private String url;

    /**
     * MinIO 对象 key，删除/清理对象时用
     */
    private String objectKey;

    /**
     * 排序，0 为第 1 个视频
     */
    private Integer sort;
}