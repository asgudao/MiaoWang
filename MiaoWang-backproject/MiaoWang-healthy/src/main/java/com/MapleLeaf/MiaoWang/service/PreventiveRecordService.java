package com.MapleLeaf.MiaoWang.service;


import com.MapleLeaf.MiaoWang.dto.req.PreventiveSaveReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PreventiveRecordRespDTO;

import java.util.List;

/**
 * 疫苗/驱虫记录服务
 */
public interface PreventiveRecordService {

    /**
     * 新增记录，返回记录ID
     *
     * @param pid 宠物ID（会校验归属与记录额度）
     */
    Long create(Long pid, PreventiveSaveReqDTO requestParam);

    /**
     * 查询某只宠物的记录
     *
     * @param type 类型筛选：VACCINE/DEWORM_IN/DEWORM_OUT，不传返回全部
     */
    List<PreventiveRecordRespDTO> list(Long pid, String type);

    /**
     * 修改记录
     */
    void update(Long id, PreventiveSaveReqDTO requestParam);

    /**
     * 逻辑删除记录
     */
    void delete(Long id);
}