package com.MapleLeaf.MiaoWang.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置：注册分页插件
 * <p>
 * 不注册 PaginationInnerInterceptor 时，selectPage 不会真正拼 LIMIT，
 * 会把整表查出来再在内存里分页（数据量大时直接拖垮服务）。
 * 公共模块里没有这个配置，各服务按需自己注册（只作用于本服务）。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}