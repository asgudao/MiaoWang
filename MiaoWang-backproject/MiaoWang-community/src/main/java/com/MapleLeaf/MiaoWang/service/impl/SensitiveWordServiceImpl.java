package com.MapleLeaf.MiaoWang.service.impl;

import com.MapleLeaf.MiaoWang.service.SensitiveWordService;
import com.MapleLeaf.MiaoWang.service.SensitiveWordSource;
import com.MapleLeaf.MiaoWang.support.SensitiveWordTrie;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * 敏感词过滤实现
 * <p>
 * 设计要点：
 * 1. 启动时用词表把 AC 自动机建好，之后每次校验只做只读扫描：trie 的构建是 O(词表总字符数)，
 * 放在请求里现建会把写接口拖慢，而词表在运行期几乎不变，建一次复用最划算；
 * 2. 字段用 volatile：Bean 初始化在启动线程，读请求在 Tomcat 工作线程，volatile 保证 trie 引用对其它线程
 * 立即可见（不会读到"半初始化"的对象），也因此不需要给 matches 加锁；
 * 3. 词表来源只依赖 {@link SensitiveWordSource} 接口，将来换数据库/配置中心词库时本类不用改。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveWordServiceImpl implements SensitiveWordService {

    private final SensitiveWordSource sensitiveWordSource;

    /**
     * 兜底给一个空词表的实例，保证 @PostConstruct 之前的任何调用路径都不出现 null（防御性初始化）
     */
    private volatile SensitiveWordTrie trie = new SensitiveWordTrie(List.of());

    @PostConstruct
    public void init() {
        Collection<String> words = sensitiveWordSource.load();
        if (words == null) {
            words = List.of();
        }
        this.trie = new SensitiveWordTrie(words);
        log.info("敏感词组件初始化完成，加载敏感词 {} 个", words.size());
    }

    @Override
    public List<String> matches(String text) {
        return trie.matches(text);
    }

    @Override
    public boolean contains(String text) {
        return trie.contains(text);
    }
}