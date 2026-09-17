package com.MapleLeaf.MiaoWang.service;


import com.MapleLeaf.MiaoWang.dto.req.SymptomSaveReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.SymptomLogRespDTO;

import java.util.List;

/**
 * 症状日记服务
 */
public interface SymptomLogService {

    /**
     * 新增症状记录，返回记录ID
     * 症状名以知识库条目为准（前端传的会被覆盖），避免同一症状出现多种写法
     */
    Long create(Long pid, SymptomSaveReqDTO requestParam);

    /**
     * 查询某只宠物的症状记录（按出现时间倒序）
     */
    List<SymptomLogRespDTO> list(Long pid);

    /**
     * 修改症状记录
     */
    void update(Long id, SymptomSaveReqDTO requestParam);

    /**
     * 逻辑删除症状记录
     */
    void delete(Long id);
}