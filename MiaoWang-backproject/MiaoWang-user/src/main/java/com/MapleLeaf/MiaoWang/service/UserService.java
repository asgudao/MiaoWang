package com.MapleLeaf.MiaoWang.service;

import com.MapleLeaf.MiaoWang.common.result.JsonResult;
import com.MapleLeaf.MiaoWang.dao.entity.UserDO;
import com.MapleLeaf.MiaoWang.dto.req.UserLoginReqDTO;
import com.baomidou.mybatisplus.extension.service.IService;


public interface UserService extends IService<UserDO> {
    JsonResult login(UserLoginReqDTO requestParam);
}
