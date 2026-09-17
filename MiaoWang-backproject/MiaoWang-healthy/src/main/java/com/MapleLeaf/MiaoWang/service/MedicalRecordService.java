package com.MapleLeaf.MiaoWang.service;


import com.MapleLeaf.MiaoWang.dto.req.MedicalSaveReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.MedicalRecordRespDTO;

import java.util.List;

/**
 * 就诊记录（病史）服务
 */
public interface MedicalRecordService {

    /**
     * 新增就诊记录，返回记录ID
     */
    Long create(Long pid, MedicalSaveReqDTO requestParam);

    /**
     * 查询某只宠物的就诊记录（按就诊时间倒序）
     */
    List<MedicalRecordRespDTO> list(Long pid);

    /**
     * 修改就诊记录
     */
    void update(Long id, MedicalSaveReqDTO requestParam);

    /**
     * 逻辑删除就诊记录
     */
    void delete(Long id);
}