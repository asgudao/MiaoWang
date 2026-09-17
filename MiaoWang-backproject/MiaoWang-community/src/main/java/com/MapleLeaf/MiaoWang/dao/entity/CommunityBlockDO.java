package com.MapleLeaf.MiaoWang.dao.entity;

import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 社区拉黑关系
 * <p>
 * 只存单向 (uid, block_uid)：拉黑是单方面动作。
 * 判断"互相不可见"时两个方向都查（我拉黑的人 + 拉黑我的人），
 * 而不是落两行双向数据 —— 双向行得两条一起写成功，中途失败就会留下"只有一半"的脏数据。
 */
@Data
@TableName("miaowang_community_block")
@EqualsAndHashCode(callSuper = false)
public class CommunityBlockDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long blockId;

    /**
     * 拉黑发起人
     */
    private Long uid;

    /**
     * 被拉黑人
     */
    private Long blockUid;
}