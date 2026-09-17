package com.MapleLeaf.MiaoWang;


import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableDiscoveryClient
@EnableFeignClients
@SpringBootApplication
@MapperScan(basePackages = "com.MapleLeaf.MiaoWang.dao")
public class VipApplication {
    public static void main(String[] args) {
        SpringApplication.run(VipApplication.class, args);
    }
}
