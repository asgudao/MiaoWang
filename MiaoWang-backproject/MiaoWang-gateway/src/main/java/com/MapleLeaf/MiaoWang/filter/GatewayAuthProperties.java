package com.MapleLeaf.MiaoWang.filter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "gateway.auth")
public class GatewayAuthProperties {

    /**
     * 免校验 Token 的白名单路径
     */
    private List<String> whiteList;

    /**
     * 内部接口黑名单路径：这些接口只给服务间调用（Feign 走 Nacos 直连，不经过网关），
     * 从网关进来的外部请求一律拒绝
     */
    private List<String> denyList;

    public List<String> getWhiteList() {
        return whiteList;
    }

    public void setWhiteList(List<String> whiteList) {
        this.whiteList = whiteList;
    }

    public List<String> getDenyList() {
        return denyList;
    }

    public void setDenyList(List<String> denyList) {
        this.denyList = denyList;
    }
}