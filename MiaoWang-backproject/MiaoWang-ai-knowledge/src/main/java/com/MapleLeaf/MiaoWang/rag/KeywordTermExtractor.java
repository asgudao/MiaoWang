package com.MapleLeaf.MiaoWang.rag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 关键词抽取工具（朴素实现，面向中文问答）。
 * <p>
 * 思路：先按空白切词；无空白时对中文做滑窗二元组（bigram），
 * 保证 "猫该打什么疫苗" 能抽出 "疫苗" 这类可精确命中知识库标题/正文的词。
 * 后续可用 IK/Jieba 分词或 Milvus 稀疏向量 BM25 替换（升级点）。
 */
public final class KeywordTermExtractor {

    /** 中文标点 + 空白 */
    private static final String SPLIT = "[，。！？；：、（）《》〈〉「」『』“”‘’…—·,.:;!?()\\s]+";

    private KeywordTermExtractor() {
    }

    /**
     * 抽取检索关键词
     *
     * @param question 用户问题
     * @param maxTerms 最多保留几个词
     */
    public static List<String> extract(String question, int maxTerms) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        String trimmed = question.trim();
        Set<String> terms = new LinkedHashSet<>();

        // 1) 空白切词（英文/数字/带空格的输入）
        for (String token : trimmed.split(SPLIT)) {
            if (token.length() >= 2) {
                terms.add(token);
            }
        }
        // 2) 中文连续串做滑窗二元组
        String compact = trimmed.replaceAll(SPLIT, "");
        if (compact.length() >= 2) {
            boolean cjkOnly = compact.codePoints().allMatch(cp -> isCjk(cp));
            if (cjkOnly || terms.isEmpty()) {
                for (int i = 0; i + 2 <= compact.length(); i++) {
                    terms.add(compact.substring(i, i + 2));
                }
                // 整句很短的场景直接整句作为一个词
                if (compact.length() <= 4) {
                    terms.add(compact);
                }
            }
        }
        // 3) 兜底：整句（截断）
        if (terms.isEmpty()) {
            terms.add(trimmed.length() > 30 ? trimmed.substring(0, 30) : trimmed);
        }
        List<String> result = new ArrayList<>(terms);
        return result.size() > maxTerms ? result.subList(0, maxTerms) : result;
    }

    private static boolean isCjk(int cp) {
        return (cp >= 0x4E00 && cp <= 0x9FFF)
                || (cp >= 0x3400 && cp <= 0x4DBF)
                || (cp >= 0xF900 && cp <= 0xFAFF);
    }
}