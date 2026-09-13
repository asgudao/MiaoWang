package com.MapleLeaf.MiaoWang.service;

import com.MapleLeaf.MiaoWang.dao.entity.PetDO;
import com.MapleLeaf.MiaoWang.dto.resp.PetRespDTO;
import com.baomidou.mybatisplus.extension.service.IService;


public interface PetService extends IService<PetDO> {
    /**
     * 通过用户名获取宠物信息
     * @param username 用户名
     * @return 宠物信息
     */
    PetRespDTO getPetByUsername(String username);
}
