package com.MapleLeaf.MiaoWang.dao.entity;

import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 帖子图片（图片本体存 MinIO，这里只存 URL 和对象 key）
 * <p>
 * 一个帖子最多 9 张（业务约束，服务层校验）。
 */
@Data
@TableName("miaowang_community_post_image")
@EqualsAndHashCode(callSuper = false)
public class CommunityPostImageDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long imageId;

    /**
     * 所属帖子 id
     */
    private Long postId;

    /**
     * 图片访问 URL（MinIO）
     */
    private String url;

    /**
     * MinIO 对象 key，删除/清理对象时用
     */
    private String objectKey;

    /**
     * 排序，0 为封面
     */
    private Integer sort;
}