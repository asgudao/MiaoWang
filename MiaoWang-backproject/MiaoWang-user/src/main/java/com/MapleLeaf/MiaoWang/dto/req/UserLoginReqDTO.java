package com.MapleLeaf.MiaoWang.dto.req;

import lombok.Data;

@Data
public class UserLoginReqDTO {

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

}
