package com.MapleLeaf.MiaoWang.dao.entity;


import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("miaowang_user")
@EqualsAndHashCode(callSuper = false)
public class UserDO extends BaseDO {
    /**
     * id
     */
    private Long uid;

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

    /**
     * 注销时间戳
     */

    private Long deletionTime;
}
