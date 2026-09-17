package com.MapleLeaf.MiaoWang.dao.mapper;

import com.MapleLeaf.MiaoWang.dao.entity.KnowledgeFragmentDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface KnowledgeFragmentMapper extends BaseMapper<KnowledgeFragmentDO> {

    /**
     * 浏览量增量合并写回：把定时任务一个窗口内的多次阅读合并成一条 UPDATE
     * <p>
     * 故意不动 update_time：浏览量变化不代表"内容被修改"，免得污染内容最后修改时间。
     *
     * @param id    碎片 id
     * @param delta 本轮写回的增量（正数）
     * @return 影响行数
     */
    @Update("UPDATE knowledge_fragment SET view_count = view_count + #{delta} WHERE id = #{id}")
    int addViewCount(@Param("id") Long id, @Param("delta") long delta);
}