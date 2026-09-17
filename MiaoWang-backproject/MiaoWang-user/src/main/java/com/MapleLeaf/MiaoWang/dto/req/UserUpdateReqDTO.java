package com.MapleLeaf.MiaoWang.dto.req;


import lombok.Data;

/**
 * 修改用户资料
 * <p>
 * ⚠️ 这里只放"资料"字段：密码走 PUT /user/password、手机号走 PUT /user/phone，
 * 那两个接口各自需要「旧密码校验」和「新号短信验证」。把 password/phone 放回这里，
 * 等于用一个改资料的请求就能绕过这两道校验（历史上正是这么漏的）。
 */
@Data
public class UserUpdateReqDTO {
    /**
     * 用户名
     */
    private String username;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 邮箱
     */
    private String mail;

    /**
     * 主人称呼：宠物对主人的称呼，数据库默认值"主人"
     */
    private String ownerCallName;

}
