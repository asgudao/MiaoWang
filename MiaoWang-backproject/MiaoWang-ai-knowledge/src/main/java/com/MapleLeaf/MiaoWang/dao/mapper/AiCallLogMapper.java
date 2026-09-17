package com.MapleLeaf.MiaoWang.dao.mapper;

import com.MapleLeaf.MiaoWang.dao.entity.AiCallLogDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * AI 调用日志 Mapper
 * <p>
 * 统计聚合没有写自定义 SQL 方法，而是在 Service 里用 {@code selectMaps} + QueryWrapper 拼聚合表达式
 * （见 AiCallLogService.stat）：聚合发生在数据库侧（GROUP BY），Java 只负责搬运结果，
 * 又不必手写 XML（省掉 {@code &gt;=} 这类 XML 转义坑）。
 */
public interface AiCallLogMapper extends BaseMapper<AiCallLogDO> {
}