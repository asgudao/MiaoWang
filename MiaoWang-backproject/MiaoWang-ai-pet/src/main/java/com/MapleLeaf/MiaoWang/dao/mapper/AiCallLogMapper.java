package com.MapleLeaf.MiaoWang.dao.mapper;

import com.MapleLeaf.MiaoWang.dao.entity.AiCallLogDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * AI 调用日志 Mapper（与 ai-knowledge 共用 miaowang_ai_call_log 表，本模块只写 biz=chat 的行）
 */
public interface AiCallLogMapper extends BaseMapper<AiCallLogDO> {
}