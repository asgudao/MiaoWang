package com.MapleLeaf.MiaoWang.service;


import com.MapleLeaf.MiaoWang.dto.req.MedicationSaveReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.MedicationRecordRespDTO;

import java.util.List;

/**
 * 用药记录服务
 */
public interface MedicationRecordService {

    /**
     * 新增用药记录，返回记录ID
     */
    Long create(Long pid, MedicationSaveReqDTO requestParam);

    /**
     * 查询某只宠物的用药记录
     *
     * @param status 状态筛选：0=用药中 1=已结束 2=已停用，不传返回全部
     */
    List<MedicationRecordRespDTO> list(Long pid, Integer status);

    /**
     * 修改用药记录（也用于「结束用药」：把 status 改成 1 并补 endAt）
     */
    void update(Long id, MedicationSaveReqDTO requestParam);

    /**
     * 逻辑删除用药记录
     */
    void delete(Long id);
}