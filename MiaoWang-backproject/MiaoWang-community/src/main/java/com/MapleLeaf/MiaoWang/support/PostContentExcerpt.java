package com.MapleLeaf.MiaoWang.support;

import cn.hutool.core.util.StrUtil;

/**
 * 列表卡片正文摘要（把帖子正文按**码点**截断成一小段，供卡片"折叠 + 展开"用）
 * <p>
 * 为什么需要它：详情接口给全文（可能很长），列表一页最多 50 帖，全文下发会把响应撑大；
 * 但卡片又需要一段"能折起来"的正文 —— 只给标题是不够的，
 * 真实标题只有 14~19 字（见库现状），永远折不到，"展开"按钮会永远点不出东西。
 * <p>
 * 三条硬规则：
 * <ol>
 *     <li><b>纯前缀</b>：返回值一定是入参文本的前缀，逐字符相同 ——
 *         不 trim、不折叠空白、不转义 HTML、不加省略号。
 *         这样"折叠时的文字"与"展开后的文字"接得上（展开后第一屏就是折叠时看到的那段），
 *         而且换行/表情/标签原样保留。省略号与"展开"按钮是**前端**的表现层决定，服务端不加。</li>
 *     <li><b>按码点（code point）计数，不按 UTF-16 char</b>：emoji、生僻字是代理对（两个 char），
 *         {@code String.substring} 按 char 切会把一个字符切成"半个"（单独的 high surrogate），
 *         前端渲染出来是乱码方块。所以长度用 {@link String#codePointCount(int, int)}，
 *         切点用 {@link String#offsetByCodePoints(int, int)}。</li>
 *     <li><b>没内容 = 空串 + 未截断</b>：不做 null 下发，前端可以无脑 {@code excerpt.length}；
 *         "没有内容"的判据与写侧"内容不能同时为空"用的是同一个 {@link StrUtil#isBlank}，
 *         所以"只有空白的正文"在两边含义一致（都算没有正文）。</li>
 * </ol>
 * 纯函数、无 IO、无状态：可以在循环里对每条帖子直接调用（不产生任何数据库访问）。
 */
public final class PostContentExcerpt {

    private PostContentExcerpt() {
    }

    /**
     * 截断结果
     *
     * @param text      摘要文本；没有正文时是空串（**不是 null**）
     * @param truncated 是否被截断（= 原正文的码点数超过了上限）；前端据此决定要不要显示"展开"
     */
    public record Excerpt(String text, boolean truncated) {
    }

    /**
     * 取正文摘要
     *
     * @param content       帖子正文，可为 null
     * @param maxCodePoints 摘要最多保留多少个**码点**（必须为正）
     */
    public static Excerpt of(String content, int maxCodePoints) {
        if (maxCodePoints <= 0) {
            throw new IllegalArgumentException("摘要长度上限必须为正数：" + maxCodePoints);
        }
        if (StrUtil.isBlank(content)) {
            return new Excerpt("", false);
        }
        if (content.codePointCount(0, content.length()) <= maxCodePoints) {
            // 没超上限就是原文本身：这里刻意返回同一个字符串（不是新构造的副本），
            // 前端拿到的摘要与详情拿到的正文逐字符相同，"没被截断"这件事在数据上也成立
            return new Excerpt(content, false);
        }
        int end = content.offsetByCodePoints(0, maxCodePoints);
        return new Excerpt(content.substring(0, end), true);
    }
}