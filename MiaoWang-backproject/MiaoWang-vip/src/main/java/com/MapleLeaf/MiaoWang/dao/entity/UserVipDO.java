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
 *
 * <p>{@code pid} 把这张表拆成两个维度：{@code 0}
 * （{@link com.MapleLeaf.MiaoWang.constant.VipScopeConstant#USER_LEVEL_PID}）是
 * <b>用户级</b>会员（权益算进用户总权益，参与合并）；大于 0 是 <b>宠物级</b>（per-pet）会员，
 * 只对那一只宠物生效，<b>不参与用户级权益合并</b> —— 否则给猫买的包会把狗的额度也顶上去。
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
     * 宠物ID：0=用户级（不绑定宠物）>0=宠物级（per-pet，只对该宠物生效）
     */
    private Long pid;

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