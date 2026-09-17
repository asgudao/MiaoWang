package com.MapleLeaf.MiaoWang.service;


import com.MapleLeaf.MiaoWang.dto.req.WeightSaveReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.WeightCurvePointRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.WeightRecordRespDTO;

import java.util.List;

/**
 * 体重记录服务
 */
public interface WeightRecordService {

    /**
     * 新增体重记录，返回记录ID
     */
    Long create(Long pid, WeightSaveReqDTO requestParam);

    /**
     * 查询某只宠物的体重记录（按测量时间倒序）
     */
    List<WeightRecordRespDTO> list(Long pid);

    /**
     * 修改体重记录
     */
    void update(Long id, WeightSaveReqDTO requestParam);

    /**
     * 逻辑删除体重记录
     */
    void delete(Long id);

    /**
     * 体重曲线：按天聚合，每天取最后一次测量
     *
     * @param months 回溯月数（1~24），默认 6
     */
    List<WeightCurvePointRespDTO> curve(Long pid, Integer months);
}