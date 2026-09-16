package com.MapleLeaf.MiaoWang.service;

import com.MapleLeaf.MiaoWang.dto.resp.SectionRespDTO;

import java.util.List;

/**
 * 社区版块
 */
public interface SectionService {

    /**
     * 查询启用的版块列表（按 sort 升序）
     */
    List<SectionRespDTO> listEnabledSections();
}