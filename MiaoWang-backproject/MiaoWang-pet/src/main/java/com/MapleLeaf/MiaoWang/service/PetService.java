package com.MapleLeaf.MiaoWang.service;

import com.MapleLeaf.MiaoWang.dao.entity.PetDO;
import com.MapleLeaf.MiaoWang.dto.req.PetCreateReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PetRespDTO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;


public interface PetService extends IService<PetDO> {
    /**
     * 通过用户名获取宠物信息（一个用户可能养多条宠物，返回列表）
     * @param username 用户名
     * @return 宠物信息列表
     */
    List<PetRespDTO> getPetByUsername(String username);


    /**
     * 通过用户名创建新的宠物
     * @param requestParam 宠物信息
     * @return 请求内容
     */
    Void createPetByUsername(PetCreateReqDTO requestParam);

}
