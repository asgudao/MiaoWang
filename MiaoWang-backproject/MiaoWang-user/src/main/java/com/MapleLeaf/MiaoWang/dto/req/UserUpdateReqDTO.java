package com.MapleLeaf.MiaoWang.dto.req;


import lombok.Data;

@Data
public class UserUpdateReqDTO {
    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 邮箱
     */
    private String mail;

    /**
     * 主人称呼：宠物对主人的称呼，数据库默认值"主人"
     */
    private String ownerCallName;

}
