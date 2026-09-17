package com.MapleLeaf.MiaoWang.dto.resp;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 帖子卡片（列表用，不含正文和图片明细）
 * <p>
 * 列表页只查帖子表：正文可能很长不必带，图片数量走封面 + 计数，避免联表。
 */
@Data
public class PostRespDTO {

    private Long postId;

    private Long sectionId;

    /**
     * 作者 uid（前端用它判断"是不是我发的"，决定是否显示删除按钮）
     */
    private Long uid;

    /**
     * 作者用户名
     */
    private String username;

    private String title;

    /**
     * 封面图 URL
     */
    private String coverUrl;

    private Integer isTop;

    private Integer viewCount;

    private Integer likeCount;

    private Integer favoriteCount;

    private Integer commentCount;

    /**
     * 当前登录用户是否已点赞（未登录恒为 false）
     */
    private Boolean liked;

    /**
     * 当前登录用户是否已收藏（未登录恒为 false）
     */
    private Boolean favorited;

    /**
     * 话题标签（按正文出现顺序，最多 5 个；没有话题时是空数组而不是 null）
     */
    private List<TopicRespDTO> topics;

    /**
     * 视频列表（最多 9 个，没有视频时是空数组而不是 null）
     */
    private List<PostVideoRespDTO> videos;

    private Date createTime;
}