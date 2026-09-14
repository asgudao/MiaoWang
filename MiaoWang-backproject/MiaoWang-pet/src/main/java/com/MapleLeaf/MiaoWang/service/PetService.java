package com.MapleLeaf.MiaoWang.service;

import com.MapleLeaf.MiaoWang.dao.entity.PetDO;
import com.MapleLeaf.MiaoWang.dto.req.PetCreateReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.PetUpdateReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PetRespDTO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;


public interface PetService extends IService<PetDO> {
    /**
     * 获取当前登录用户的宠物列表（一个用户可能养多条宠物，返回列表）
     * 身份从上下文 UserContext 获取，不信任前端传入，按稳定键 uid 查询
     * @return 宠物信息列表
     */
    List<PetRespDTO> getMyPets();


    /**
     * 创建新的宠物并返回新宠物完整信息（含 pid，前端免二次查询）
     * @param requestParam 宠物信息
     * @return 新创建的宠物信息
     */
    PetRespDTO createPetByUsername(PetCreateReqDTO requestParam);

    /**
     * 更新宠物信息（只能更新自己的宠物，含归属校验）
     * @param pid 宠物id（URL 路径传入）
     * @param requestParam 宠物更新信息
     * @return 请求内容
     */
    Void updatePetByPid(Long pid, PetUpdateReqDTO requestParam);

    /**
     * 删除宠物（逻辑删除，只能删自己的宠物，含归属校验）
     * @param pid 宠物id（URL 路径传入）
     * @return 请求内容
     */
    Void deletePetByPid(Long pid);

}
