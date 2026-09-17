package com.MapleLeaf.MiaoWang.service;


import com.MapleLeaf.MiaoWang.dto.req.AllergySaveReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.AllergyRecordRespDTO;

import java.util.List;

/**
 * 过敏史服务
 */
public interface AllergyRecordService {

    /**
     * 新增过敏记录，返回记录ID
     */
    Long create(Long pid, AllergySaveReqDTO requestParam);

    /**
     * 查询某只宠物的过敏史（重度在前）
     */
    List<AllergyRecordRespDTO> list(Long pid);

    /**
     * 修改过敏记录
     */
    void update(Long id, AllergySaveReqDTO requestParam);

    /**
     * 逻辑删除过敏记录
     */
    void delete(Long id);
}