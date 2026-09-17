package com.MapleLeaf.MiaoWang.service.impl;

import com.MapleLeaf.MiaoWang.service.SensitiveWordSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 从 classpath 读内置演示词表的词表来源
 * <p>
 * 设计要点：
 * 1. 标注 {@code @Primary}：本项目约定同类型接口只有一个主实现，将来新增"读数据库/读 Nacos"的实现类时，
 * 不会因为两个候选 Bean 撞在一起导致注入失败，要把主实现换掉只改这个注解的位置；
 * 2. 用 Spring 的 {@link ClassPathResource} 而不是相对文件路径：服务是打成 jar 运行的，jar 内资源没有可用的
 * 文件路径，只有 classpath 输入流才读得到；本地 IDE 运行与线上部署的行为也一致；
 * 3. 读不到/读失败一律 log.warn 并返回空集合：词表属于可降级的配置数据，缺了最多是不做过滤，
 * 绝不能因为一个文件问题让整个社区服务起不来。
 */
@Slf4j
@Component
@Primary
public class ClasspathSensitiveWordSource implements SensitiveWordSource {

    /**
     * 演示词表位置。正式词库直接替换这个文件，或者换成本接口的其它实现，业务代码都不用动
     */
    private static final String WORDS_FILE = "sensitive-words-demo.txt";

    /**
     * 词表里的注释前缀，用于在文件里写说明
     */
    private static final String COMMENT_PREFIX = "#";

    @Override
    public Collection<String> load() {
        ClassPathResource resource = new ClassPathResource(WORDS_FILE);
        if (!resource.exists()) {
            log.warn("敏感词词表 {} 在 classpath 上不存在，本次按「无词表」降级，过滤逻辑不生效", WORDS_FILE);
            return new ArrayList<>();
        }
        List<String> words = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // 注释行与空行都跳过；trim 后再判空，避免行尾空格/缩进被当成敏感词
                String word = line.trim();
                if (word.isEmpty() || word.startsWith(COMMENT_PREFIX)) {
                    continue;
                }
                words.add(word);
            }
        } catch (IOException e) {
            // 读到一半失败时已读到的词可能是残缺词表，宁可整体降级为"无词表"也不要用半份词表上线
            log.warn("敏感词词表 {} 读取失败，本次按「无词表」降级，过滤逻辑不生效", WORDS_FILE, e);
            return new ArrayList<>();
        }
        return words;
    }
}