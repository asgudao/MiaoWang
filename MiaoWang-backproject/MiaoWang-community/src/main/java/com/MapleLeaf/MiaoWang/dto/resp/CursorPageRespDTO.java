package com.MapleLeaf.MiaoWang.dto.resp;

import lombok.Data;

import java.util.List;

/**
 * 游标分页返回（移动端滚动加载用）
 * <p>
 * 为什么评论不用页码：
 * 1. 评论是"新的在最前面"，翻页期间有人发了新评论，offset 分页就会让用户看到重复的评论（有人删评论则会漏）；
 * 2. LIMIT offset, size 要先把前 offset 行扫掉再丢弃，越翻越慢；游标是"从上一页最后一条往下取"，深翻页一样快；
 * 3. 不发 total：算 total 要对大表做 COUNT，移动端只需要知道"还有没有下一页"。
 *
 * @param <T> 记录类型
 */
@Data
public class CursorPageRespDTO<T> {

    /**
     * 本页记录
     */
    private List<T> records;

    /**
     * 本页请求的条数（不是实际返回的条数）
     */
    private long size;

    /**
     * 还有没有下一页
     */
    private boolean hasMore;

    /**
     * 下一页的游标：原样回传给接口的 lastId 参数即可
     * <p>
     * hasMore=false 时为 null。注意不同接口的游标方向不同（一级评论列表是"比它小"，回复列表是"比它大"），
     * 前端只管原样回传，不要自己加减。
     */
    private Long nextCursor;

    public static <T> CursorPageRespDTO<T> of(List<T> records, long size, boolean hasMore, Long nextCursor) {
        CursorPageRespDTO<T> respDTO = new CursorPageRespDTO<>();
        respDTO.setRecords(records);
        respDTO.setSize(size);
        respDTO.setHasMore(hasMore);
        respDTO.setNextCursor(nextCursor);
        return respDTO;
    }
}