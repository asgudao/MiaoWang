package com.MapleLeaf.MiaoWang.dto.req;

import lombok.Data;

/**
 * 服务间扣减/回退额度请求（本模块调用 vip 的 {@code /vip/internal/quota/*} 用）
 *
 * <p>刻意在本模块自定义一份，而不是依赖 vip 模块的 DTO：
 * 服务之间只约定 JSON 结构、不依赖对方代码，vip 改内部实现不会编译期牵连到这里。
 * （与 ai-knowledge 的同名类内容一致，属项目既定的"跨模块 DTO 镜像"模式。）
 */
@Data
public class VipQuotaConsumeReqDTO {

    /**
     * 用户ID
     */
    private Long uid;

    /**
     * 业务类型：本模块固定 "chat"
     */
    private String biz;

    public static VipQuotaConsumeReqDTO of(Long uid, String biz) {
        VipQuotaConsumeReqDTO requestParam = new VipQuotaConsumeReqDTO();
        requestParam.setUid(uid);
        requestParam.setBiz(biz);
        return requestParam;
    }
}