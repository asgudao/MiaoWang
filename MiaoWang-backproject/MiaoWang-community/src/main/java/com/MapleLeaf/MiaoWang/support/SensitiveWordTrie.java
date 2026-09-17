package com.MapleLeaf.MiaoWang.support;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 敏感词过滤器（Aho-Corasick 自动机）
 * <p>
 * 为什么用 AC 自动机，而不是"拿词表逐个 contains 文本"：
 * 逐词扫描的复杂度是 O(词数 × 文本长度)，而社区的发帖/编辑/评论是全体用户的高频写入口，词表迟早从演示词表
 * 长到几万条，每个请求都把整张词表在文本里过一遍，词表一大就顶不住。AC 自动机先把词表的公共前缀合并成一棵
 * trie（"广告"和"广告词"共用同一条前缀路径），再给每个节点补一条 fail 指针（失配时直接跳到"已匹配部分的最长
 * 可用后缀"所在节点，不回头重扫），因此扫描一遍文本就能同时找出所有词，复杂度是 O(文本长度 + 命中数)，
 * 与词表规模基本无关。
 * <p>
 * 线程安全：构造阶段建完 trie 与 fail 指针后，所有字段都不再写入，对象对外表现为不可变，
 * 所以构建完成后可以被多线程无锁共享（服务里是启动时构建一次、之后只读）。
 * <p>
 * 本类刻意不依赖 Spring / Lombok：过滤算法是纯逻辑，脱离容器单独编译运行就能自测，
 * 也方便将来换词表来源时复用到其它模块。
 */
public class SensitiveWordTrie {

    /**
     * trie 节点：一个节点代表词表里某个词的某个前缀
     */
    private static final class Node {

        /**
         * 孩子表用 Map 而不是 26 字母数组：词表里是中文词、还可能混表情符号，字符集不可枚举，
         * 用数组会浪费大量空间，映射结构才通用
         */
        private final Map<Character, Node> children = new HashMap<>(4);

        /**
         * 失配指针：本节点代表的字符串匹配失败时，跳到"它的最长真后缀"所在节点继续匹配
         */
        private Node fail;

        /**
         * 输出链：fail 链上最近的"成词"节点（不含自身）。扫描时沿这条链收集被当前后缀覆盖的短词，
         * 有几条命中就走几步，不必每走一个字符都翻整条 fail 链
         */
        private Node outputLink;

        /**
         * 以本节点结尾的敏感词，保存词表里的原始写法，返回给调用方时大小写与词表一致
         */
        private String word;

        /**
         * 该词统一转小写后的字符数：起始下标按小写文本算，个别字符转小写后长度会变，所以单独记长度
         */
        private int wordLength;
    }

    private final Node root = new Node();

    /**
     * 词表为空（或全是空白词）时没有可命中的词，直接短路，省掉后续扫描
     */
    private final boolean empty;

    /**
     * @param words 敏感词表；null、空集合、全空白词都按"无词表"处理，不抛异常
     */
    public SensitiveWordTrie(Collection<String> words) {
        boolean hasWord = false;
        if (words != null) {
            for (String word : words) {
                // 空白词/空串没有任何过滤意义，而且会让根节点变成"成词节点"，会污染所有匹配结果，必须忽略
                if (word == null || word.isBlank()) {
                    continue;
                }
                String lower = word.toLowerCase(Locale.ROOT);
                Node node = root;
                for (int i = 0; i < lower.length(); i++) {
                    node = node.children.computeIfAbsent(lower.charAt(i), key -> new Node());
                }
                // 同一个词重复出现时后写覆盖前写，语义不变
                node.word = word;
                node.wordLength = lower.length();
                hasWord = true;
            }
        }
        this.empty = !hasWord;
        if (!empty) {
            buildFailLinks();
        }
    }

    /**
     * 建 fail 指针（BFS 逐层递推）
     * <p>
     * 递推依据：节点 u 的 fail 指向"u 的最长真后缀"。父节点的 fail 已经算好，所以
     * {@code u.fail = go(父.fail, 当前字符)}——即从父节点的失配处继续试着接上同一个字符，
     * 一层层推下去不需要回溯重扫，整体是 O(词表总字符数)。
     */
    private void buildFailLinks() {
        Deque<Node> queue = new ArrayDeque<>();
        // 根的直接孩子：只有一个字符，没有更短的真后缀，失配只能回根；fail 链上没有别的成词节点，输出链为空
        for (Node child : root.children.values()) {
            child.fail = root;
            queue.addLast(child);
        }
        while (!queue.isEmpty()) {
            Node current = queue.pollFirst();
            for (Map.Entry<Character, Node> entry : current.children.entrySet()) {
                char c = entry.getKey();
                Node child = entry.getValue();
                // 从父节点的失配处出发，沿 fail 链找一个同样有 c 孩子的节点，找不到就落到根
                Node fallback = current.fail;
                while (fallback != root && !fallback.children.containsKey(c)) {
                    fallback = fallback.fail;
                }
                Node target = fallback.children.get(c);
                child.fail = (target == null) ? root : target;
                // 输出链只串"成词"节点：这一步把收集命中的代价压到 O(命中数)
                child.outputLink = (child.fail.word != null) ? child.fail : child.fail.outputLink;
                queue.addLast(child);
            }
        }
    }

    /**
     * 找出文本里命中的敏感词：去重，并按每个词在文本中首次出现的位置排序
     * <p>
     * 只扫描一遍文本，靠 fail 指针回退、靠输出链收词，不做"逐词 contains"。
     *
     * @param text 待检查文本
     * @return 命中的敏感词（保留词表里的原始写法）；无命中、text 为 null 或纯空白、词表为空时返回空集合，绝不返回 null，也不抛异常
     */
    public List<String> matches(String text) {
        if (empty || text == null || text.isBlank()) {
            return List.of();
        }
        // 文本与词表统一转小写，实现英文大小写不敏感；中文转小写后不变，不受影响。
        // 用 Locale.ROOT 而不是默认区域：土耳其语区域会把 I 转成无点的 ı，导致同一份代码在不同机器上过滤结果不一致
        String lower = text.toLowerCase(Locale.ROOT);
        // 只记每个词"第一次命中时的起始下标"：从左到右扫描，第一次遇到必然就是它最早出现的那次，天然完成去重
        Map<String, Integer> firstIndex = new LinkedHashMap<>();
        Node node = root;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            // 失配就沿 fail 指针回退，直到能接上 c（最多回到根）；这是 AC 自动机不需要回扫文本的关键
            while (node != root && !node.children.containsKey(c)) {
                node = node.fail;
            }
            Node next = node.children.get(c);
            node = (next == null) ? root : next;
            // 当前节点本身是某个词的结尾
            if (node.word != null) {
                firstIndex.putIfAbsent(node.word, i - node.wordLength + 1);
            }
            // 再沿输出链收集被当前后缀覆盖的短词：词表同时有"广告"和"广告词"时，文本"广告词"两个都算命中
            for (Node hit = node.outputLink; hit != null; hit = hit.outputLink) {
                firstIndex.putIfAbsent(hit.word, i - hit.wordLength + 1);
            }
        }
        if (firstIndex.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(firstIndex.keySet());
        // 同一起始下标可能有多个词（如"广告"与"广告词"都从同一下标开始），再按词长、字典序兜底，保证顺序稳定可预期
        result.sort(Comparator.comparingInt((String word) -> firstIndex.get(word))
                .thenComparingInt(String::length)
                .thenComparing(Comparator.<String>naturalOrder()));
        return result;
    }

    /**
     * 文本里是否命中至少一个敏感词
     * <p>
     * 复用 {@link #matches(String)}，保证两条入口的判定语义永远一致，不会因为两套逻辑各自演化而走偏。
     *
     * @param text 待检查文本
     * @return true 表示命中；text 为 null 或纯空白、词表为空时返回 false，不抛异常
     */
    public boolean contains(String text) {
        return !matches(text).isEmpty();
    }
}