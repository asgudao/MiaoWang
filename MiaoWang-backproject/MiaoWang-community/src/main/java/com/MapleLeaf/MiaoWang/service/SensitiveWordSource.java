package com.MapleLeaf.MiaoWang.service;

import java.util.Collection;

/**
 * 敏感词词表来源
 * <p>
 * 这个接口是**词表来源的替换点**：过滤算法（AC 自动机）只关心"有一份词表"，不关心词表从哪来。
 * 现在默认实现是读 classpath 上内置的演示词表；将来要换成读数据库、读 Nacos 配置、或对接远程词库时，
 * 只需新增一个本接口的实现类（配合 {@code @Primary} 决定用哪个），AC 自动机与所有调用方都不用改一行代码。
 * <p>
 * 实现约定：读不到词表时返回空集合降级，不要把异常抛给启动流程。
 */
public interface SensitiveWordSource {

    /**
     * 加载全量敏感词
     *
     * @return 敏感词集合；实现方读不到词表时应返回空集合而不是 null
     */
    Collection<String> load();
}