package com.MapleLeaf.MiaoWang.dao.mapper;

import com.MapleLeaf.MiaoWang.dao.entity.CommunityCommentDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 社区评论 Mapper
 */
public interface CommunityCommentMapper extends BaseMapper<CommunityCommentDO> {

    /**
     * 批量取每条一级评论下的前 N 条回复（一次查询拿到整页的回复预览，不是每条评论查一次）
     * <p>
     * 用 MySQL 8 的窗口函数：先按 parent_id 分组、组内按 comment_id 正序编号，
     * 再只取编号 &lt;= N 的行。这样即使某条评论下面有上千条回复，也只会取回 N 条。
     *
     * @param parentIds 一级评论 id 列表（非空）
     * @param status    评论状态
     * @param limit     每条评论取几条回复
     */
    @Select("<script>"
            + "SELECT * FROM ("
            + "  SELECT c.*, ROW_NUMBER() OVER (PARTITION BY c.parent_id ORDER BY c.comment_id ASC) AS rn"
            + "  FROM miaowang_community_comment c"
            + "  WHERE c.status = #{status} AND c.del_flag = 0"
            + "    AND c.parent_id IN "
            + "    <foreach collection='parentIds' item='parentId' open='(' separator=',' close=')'>#{parentId}</foreach>"
            + ") t WHERE t.rn &lt;= #{limit}"
            + " ORDER BY t.parent_id ASC, t.comment_id ASC"
            + "</script>")
    List<CommunityCommentDO> selectPreviewReplies(@Param("parentIds") List<Long> parentIds,
                                                  @Param("status") Integer status,
                                                  @Param("limit") int limit);
}