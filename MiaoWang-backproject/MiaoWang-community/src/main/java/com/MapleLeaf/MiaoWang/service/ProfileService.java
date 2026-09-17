package com.MapleLeaf.MiaoWang.service;

import com.MapleLeaf.MiaoWang.dto.resp.PetBriefRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.UserProfileRespDTO;

import java.util.List;

/**
 * 个人主页服务
 */
public interface ProfileService {

    /**
     * 个人主页头部信息：帖子数、获赞总数、关注数、粉丝数、是否已关注
     *
     * @param uid 要看的用户；传 null = 看自己（需要登录）
     * @return 头部信息
     */
    UserProfileRespDTO getProfile(Long uid);

    /**
     * 他的宠物（跨服务调用宠物服务；宠物服务不可用时降级为空列表，不阻塞主页）
     *
     * @param uid 用户id
     * @return 宠物列表
     */
    List<PetBriefRespDTO> listPets(Long uid);
}