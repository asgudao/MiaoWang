package com.MapleLeaf.MiaoWang.dto.resp;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 帖子卡片（列表用，**不含正文全文**，只带一小段正文摘要）
 * <p>
 * 列表页**不额外查正文**：帖子查询是整行 select（没有列投影），{@code content} 本来就在这一行上，
 * 摘要只是把它在内存里截断一下 —— 所以本 DTO 的新字段**不产生任何 SQL**（见
 * {@link com.MapleLeaf.MiaoWang.support.PostContentExcerpt}）。
 * 之所以仍然只给摘要而不给全文：一页最多 50 帖，全文（单帖可能上千字）下发会把响应撑大；
 * 而卡片要"折叠 + 展开"，折的是**正文**（真实标题只有 14~19 字，永远折不到、按钮永远点不出东西）。
 * 图片走"一条 IN 批量查 + 只带前几张预览"：
 * 卡片要能直接铺九宫格，所以不能只给封面；但一页最多 50 帖，图片全量下发会把响应撑大，
 * 于是带前 {@link com.MapleLeaf.MiaoWang.constant.CommunityConstant#LIST_IMAGE_PREVIEW_COUNT} 张
 * + 一个总数（前端在第 9 张上盖 "+N"）。
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
     * 正文摘要（卡片折叠态的正文，**不是全文**）
     * <p>
     * 口径（详见 {@code support/PostContentExcerpt.java}）：
     * <ul>
     *     <li>按**码点**截断到 {@link com.MapleLeaf.MiaoWang.constant.CommunityConstant#LIST_CONTENT_EXCERPT_LENGTH}
     *         （200）个字，中文/emoji 都不会被切成半个字符；</li>
     *     <li>是正文的**纯前缀**：不 trim、不折叠换行、不转义 HTML、**不加省略号**
     *         （"…"与"展开"按钮是前端的表现层决定）—— 所以"折叠时看到的"与"展开后开头的"完全接得上；</li>
     *     <li>**没有正文时是空串而不是 null**，也不缺字段（前端可以无脑 {@code contentExcerpt.length}）；
     *         判据与写侧"内容不能同时为空"用的是同一个 {@code StrUtil.isBlank}，
     *         所以"只有空白的正文"两边含义一致（都算没有正文）。</li>
     * </ul>
     * ⚠️ 与详情 DTO 的 {@code content} 的关系：详情给**全文、未截断**，列表给摘要；
     * 前端"展开"必须去读详情，**不能**指望列表把全文带出来。
     * ⚠️ 本 DTO **故意没有** {@code content} 字段：列表不下发全文这件事要在类型上就能看出来
     * （门禁里有断言钉住）。
     */
    private String contentExcerpt;

    /**
     * 正文是否被截断（= 正文码点数 > {@code LIST_CONTENT_EXCERPT_LENGTH}）
     * <p>
     * 前端判据只有这一条：**`contentTruncated === true` 才显示"展开"**。
     * 为什么不给一个 {@code contentLength} 让前端自己比：JS 的 {@code String.length} 数的是
     * **UTF-16 单元**，Java 这边数的是**码点**，一条带 emoji 的正文两者能差出好几个
     * → 前端 {@code contentLength > contentExcerpt.length} 这种写法会在带 emoji 的帖子上**算错**
     * （明明截断了却判成没截断，"展开"按钮消失）。截断与否只有服务端自己知道得最准，
     * 所以直接下发**布尔结论**，前端不做第二次比较。
     * <p>
     * 无正文时恒为 {@code false}（那串空摘要不是"被截断的正文"，也因此不会出现"空摘要 + 展开按钮"）。
     */
    private Boolean contentTruncated;

    /**
     * 封面图 URL
     * <p>
     * 口径不变：仍然等于图片按 sort 升序的第 0 张（见 PostServiceImpl 发帖/编辑处的冗余写入）。
     */
    private String coverUrl;

    /**
     * 图片预览（按 sort 升序，[0] 即封面；最多带 LIST_IMAGE_PREVIEW_COUNT 张）
     * <p>
     * 与 {@link PostDetailRespDTO#getImages()} **同名、同元素类型、同排序**，
     * 前端可以用同一套渲染/类型；区别只有"列表页会被截断"这一条，截断了多少看 imageCount。
     * 没有图片时是空数组而不是 null（前端靠它决定整块图片区域不渲染）。
     */
    private List<PostImageRespDTO> images;

    /**
     * 该帖图片总数（不受 images 截断影响）
     * <p>
     * 命名跟同 DTO 里已有的 viewCount / likeCount / favoriteCount / commentCount 一致；
     * 前端算 "+N" 用 imageCount - images.length（当前写入侧上限就是 9，通常为 0）。
     */
    private Integer imageCount;

    private Integer isTop;

    private Integer viewCount;

    private Integer likeCount;

    private Integer favoriteCount;

    /**
     * 评论条数（卡片外面显示"多少条评论"就用它）
     * <p>
     * 来自帖子行的**冗余计数** {@code miaowang_community_post.comment_count}：
     * 发评论时 SQL 原子 +1、删评论时按删掉的条数 -1（带 gt 兜底，不会被减成负数），
     * 所以列表**不需要**为了这个数字多查一次库（0 条 SQL）。
     * <p>
     * ⚠️ 口径：**含回复** —— 评论表每插一行就 +1，一级评论和楼中楼回复都算。
     * 而评论列表接口默认只列**一级评论**，所以这个数字**可能大于**评论列表第一页/总数的条数
     * （例：真实帖 178 的 commentCount = 4，但一级评论只有 2 条）。
     * 这是刻意的："N 条评论"在社区里一直指"含回复的总量"；前端不要拿它当列表长度用。
     * <p>
     * **没有评论时是 0 而不是 null**（列默认值 0），列表外面直接显示 0 即可。
     */
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