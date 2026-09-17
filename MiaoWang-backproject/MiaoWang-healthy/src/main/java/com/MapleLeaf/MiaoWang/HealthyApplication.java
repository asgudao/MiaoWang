package com.MapleLeaf.MiaoWang;


import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 宠物健康档案服务
 *
 * <p>依赖两个远程服务：
 * <ul>
 *   <li>miaowang-pet：校验宠物归属（用「查我的宠物」接口，需透传登录头）</li>
 *   <li>miaowang-vip：取健康档案相关权益（记录条数上限、能否导出、能否 AI 解读）</li>
 *   <li>miaowang-knowledge：取其「健康自查」条目作为症状选项</li>
 * </ul>
 */
@EnableFeignClients
@EnableDiscoveryClient
@SpringBootApplication
@MapperScan(basePackages = "com.MapleLeaf.MiaoWang.dao")
public class HealthyApplication {
    public static void main(String[] args) {
        SpringApplication.run(HealthyApplication.class, args);
    }
}