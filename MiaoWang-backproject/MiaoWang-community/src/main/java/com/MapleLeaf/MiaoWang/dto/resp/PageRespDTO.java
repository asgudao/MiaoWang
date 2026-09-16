package com.MapleLeaf.MiaoWang.dto.resp;

import lombok.Data;

import java.util.List;

/**
 * 通用分页返回
 * <p>
 * 不直接把 MyBatis-Plus 的 IPage 返回给前端：IPage 里带着 orders/optimizeCountSql 等
 * 内部字段，序列化出去又乱又容易被误用，这里只暴露前端真正需要的 5 个字段。
 */
@Data
public class PageRespDTO<T> {

    /**
     * 当前页码（从 1 开始）
     */
    private Long pageNum;

    /**
     * 每页条数
     */
    private Long pageSize;

    /**
     * 总条数
     */
    private Long total;

    /**
     * 总页数
     */
    private Long pages;

    /**
     * 当前页数据
     */
    private List<T> records;

    public static <T> PageRespDTO<T> of(long pageNum, long pageSize, long total, List<T> records) {
        PageRespDTO<T> pageRespDTO = new PageRespDTO<>();
        pageRespDTO.setPageNum(pageNum);
        pageRespDTO.setPageSize(pageSize);
        pageRespDTO.setTotal(total);
        pageRespDTO.setPages(pageSize <= 0 ? 0L : (total + pageSize - 1) / pageSize);
        pageRespDTO.setRecords(records);
        return pageRespDTO;
    }
}