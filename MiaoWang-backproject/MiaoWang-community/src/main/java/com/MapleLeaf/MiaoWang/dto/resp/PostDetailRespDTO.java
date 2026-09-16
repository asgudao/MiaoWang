package com.MapleLeaf.MiaoWang.dto.resp;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 帖子详情
 */
@Data
public class PostDetailRespDTO {

    private Long postId;

    private Long sectionId;

    private Long uid;

    private String username;

    private String title;

    /**
     * 正文
     */
    private String content;

    private Integer status;

    private Integer viewCount;

    private Integer likeCount;

    private Integer favoriteCount;

    private Integer commentCount;

    /**
     * 当前登录用户是否已点赞
     */
    private Boolean liked;

    /**
     * 当前登录用户是否已收藏
     */
    private Boolean favorited;

    private Date createTime;

    /**
     * 图片明细（按 sort 升序，[0] 为封面）
     */
    private List<PostImageRespDTO> images;
}