package com.MapleLeaf.MiaoWang.dao.entity;


import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 用户会员记录
 * 同一档位只有一行（续费延长 expire_time），不同档位可并行持有
 */
@Data
@TableName("miaowang_user_vip")
@EqualsAndHashCode(callSuper = false)
public class UserVipDO extends BaseDO {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long uid;

    /**
     * 用户名（冗余，便于排查）
     */
    private String username;

    /**
     * 档位编码
     */
    private String planCode;

    /**
     * 本次开通时间
     */
    private Date startTime;

    /**
     * 到期时间（判断是否生效只看这个字段）
     */
    private Date expireTime;

    /**
     * 状态：1=生效 0=失效
     */
    private Integer status;
}