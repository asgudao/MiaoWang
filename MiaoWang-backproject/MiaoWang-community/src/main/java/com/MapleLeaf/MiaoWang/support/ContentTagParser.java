package com.MapleLeaf.MiaoWang.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 正文话题（{@code #话题} / {@code #话题#}）与提及（{@code @用户名}）解析工具
 * <p>
 * 用途：社区发帖 / 编辑 / 评论落库时，从标题与正文里抽出发帖人显式写出的话题标签与提及对象，
 * 供后续写关系表、做话题聚合和"@我的"通知使用。本类只做纯文本解析：不查库、不依赖 Spring、
 * 也不依赖项目内其它类，因此可以脱离容器单独编译运行自测，也方便将来复用到别的模块。
 * <p>
 * <b>话题语法</b>
 * <ul>
 *   <li>起始符是 {@code #}；{@code #话题} 与 {@code #话题#} 两种写法等价，结尾那个 {@code #} 是可选的闭合符。</li>
 *   <li>名字从 {@code #} 的下一个字符开始取，一直取到下列任一条件为止：空白字符（含全角空格、不间断空格等
 *       Unicode 空白）、另一个 {@code #}、
 *       结束标点（{@code , ， . 。 ! ！ ? ？ 、 : ： ; ； ' " “ ” ( ) （ ） [ ] 【 】 < > 《 》 / \ | ~}）、
 *       或文本结尾。</li>
 *   <li>名字 trim 后长度至少 1 个字符才算话题：{@code #} 后面直接是空白 / 结束标点 / 另一个 {@code #} /
 *       文本结尾时，这个 {@code #} 不算话题。此时只跳过它自己一格，它后面的字符仍可另起话题。</li>
 *   <li>名字超过 20 个字符时<b>截断为前 20 个字符</b>，不整条丢弃：截断优于丢弃，用户写的内容不会凭空消失，
 *       而超长话题名本来也无法用于聚合。长度按 UTF-16 字符数计算，极端情况下可能切断代理对（emoji），
 *       属于已知取舍。</li>
 *   <li>去重按大小写不敏感（{@code #Cat} 与 {@code #cat} 视为同一个话题），保留第一次出现的原始写法；
 *       按出现顺序最多返回 5 个。</li>
 * </ul>
 * <p>
 * <b>提及语法</b>
 * <ul>
 *   <li>起始符是 {@code @}，且 {@code @} 的前一个字符不能是 {@code [A-Za-z0-9_.-]} 之一：这样
 *       {@code foo@bar.com}、{@code a@b} 这类"看起来像邮箱 / 标识符"的写法不会被误判成提及；
 *       文本开头的 {@code @} 一律允许，中文等非 ASCII 字符后面紧跟的 {@code @}（如
 *       {@code 谢谢@MapleLeaf}）也允许。</li>
 *   <li>{@code @} 后面必须紧跟 {@code [A-Za-z0-9_]}，用户名按 {@code [A-Za-z0-9_]{1,20}} 贪婪匹配，
 *       遇到其它字符（中文、标点、空白等）立即停止，因此 {@code @张三} 不会被识别成提及。</li>
 *   <li>去重同样大小写不敏感并保留首次出现的原始写法，按出现顺序最多返回 10 个。</li>
 * </ul>
 * <p>
 * <b>已知取舍（产品上明确接受）</b>
 * <ul>
 *   <li>话题<b>不做"前一个字符"限制</b>：产品要求 {@code 周末#遛狗#} 这种紧贴在汉字后面的写法能被识别，
 *       代价是 {@code 3#楼} 会被解析出话题 {@code 楼}。这是有意为之的取舍，不是缺陷。</li>
 *   <li>提及"前一个字符"的限制只排除 {@code [A-Za-z0-9_.-]}：中文、空白、中文标点后面的 {@code @} 都算提及，
 *       所以 {@code 你好@abc} 会被识别（而 {@code 你好@张三} 因为用户名规则限定 ASCII 而不会被识别）。</li>
 *   <li>名字的结束条件就是上面那份"空白 + {@code #} + 结束标点"清单，清单之外的字符一律算名字内部字符：
 *       例如 {@code #猫@狗} 会得到话题 {@code 猫@狗}（{@code @} 不在话题结束标点集合里）。若产品要求
 *       {@code #猫@狗} 只取 {@code 猫}，需要把 {@code @} 等符号补进结束标点集合。</li>
 *   <li>{@code #话题#} 的闭合 {@code #} 会被消费掉，不再当作下一个话题的起始符：否则
 *       {@code #猫#和#猫#} 会多解析出一个话题 {@code 和}。</li>
 * </ul>
 * <p>
 * <b>其它约定</b>
 * <ul>
 *   <li>两个方法都是纯函数：{@code null} / 空串入参按空串处理，不抛异常；无结果时返回 {@link List#of()}。</li>
 *   <li>入参按 {@code title + "\n" + content} 拼接后统一解析，因此话题 / 提及可以横跨标题与正文；
 *       返回的列表不可变，调用方直接持有即可。</li>
 * </ul>
 */
public final class ContentTagParser {

    /**
     * 话题名长度上限：超出部分截断为前 20 个字符，不丢弃整条话题
     */
    private static final int MAX_TOPIC_LENGTH = 20;

    /**
     * 单条文本最多返回的话题数，按出现顺序取前 5 个
     */
    private static final int MAX_TOPIC_COUNT = 5;

    /**
     * 提及用户名长度上限，超出部分截断（正则语义为最多贪婪匹配 20 个字符）
     */
    private static final int MAX_MENTION_LENGTH = 20;

    /**
     * 单条文本最多返回的提及数，按出现顺序取前 10 个
     */
    private static final int MAX_MENTION_COUNT = 10;

    /**
     * 话题结束标点：中英文半角 / 全角都收进来，避免用户切换输入法时标签与后文粘连。
     * 空白字符、{@code #} 与文本结尾由代码单独判断，不放进这份清单
     */
    private static final String TOPIC_END_PUNCTUATION = ",，.。!！?？、:：;；'\"“”()（）[]【】<>《》/\\|~";

    /**
     * 话题起始 / 闭合符
     */
    private static final char TOPIC_MARK = '#';

    /**
     * 提及起始符
     */
    private static final char MENTION_MARK = '@';

    /**
     * 工具类不允许实例化：全部能力都是无状态静态方法
     */
    private ContentTagParser() {
        throw new AssertionError("ContentTagParser 是工具类，不允许实例化");
    }

    /**
     * 解析文本中的话题标签，同时支持 {@code #话题} 与 {@code #话题#} 两种写法。
     * <p>
     * 规则与边界：
     * <ul>
     *   <li>解析文本为 {@code title + "\n" + content}，{@code null} 一律当空串，不抛异常。</li>
     *   <li>名字取到「空白字符、另一个 {@code #}、结束标点、文本结尾」为止；trim 后为空则该 {@code #} 不算话题。</li>
     *   <li>名字超过 20 个字符截断为前 20 个；去重忽略大小写并保留首次出现的原始写法；最多 5 个。</li>
     *   <li>不做"前一个字符"限制，因此 {@code 3#楼} 会解析出 {@code 楼}（已知取舍，类注释已说明）。</li>
     *   <li>{@code #话题#} 的闭合 {@code #} 会被消费，不会再作为下一个话题的起始符。</li>
     * </ul>
     *
     * @param title   标题，可为 {@code null}
     * @param content 正文，可为 {@code null}
     * @return 不可变的话题列表，按出现顺序，最多 5 个；无结果时返回空列表
     */
    public static List<String> parseTopics(String title, String content) {
        String text = joinText(title, content);
        int length = text.length();
        // LinkedHashMap 只用来做"忽略大小写的去重 + 保留首次原始写法 + 维持出现顺序"三件事：
        // key 是小写名，value 是首次出现的原始写法
        Map<String, String> topics = new LinkedHashMap<>(MAX_TOPIC_COUNT);
        int index = 0;
        while (index < length && topics.size() < MAX_TOPIC_COUNT) {
            if (text.charAt(index) != TOPIC_MARK) {
                index++;
                continue;
            }
            int end = index + 1;
            while (end < length && !isTopicTerminator(text.charAt(end))) {
                end++;
            }
            String name = text.substring(index + 1, end).trim();
            if (name.isEmpty()) {
                // `#` 后面直接是空白 / 结束标点 / 另一个 `#` / 文本结尾：这个 `#` 不是话题。
                // 只跳过它自己一格，让紧随其后的字符仍有机会作为新话题的开头（"##猫" 能取到 "猫"）
                index++;
                continue;
            }
            if (name.length() > MAX_TOPIC_LENGTH) {
                name = name.substring(0, MAX_TOPIC_LENGTH);
            }
            topics.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
            // 名字以 `#` 结尾时，这个 `#` 是 `#话题#` 的闭合符，消费掉它，
            // 否则 "#猫#和#猫#" 会把它当成起始符，多解析出一个话题 "和"
            index = (end < length && text.charAt(end) == TOPIC_MARK) ? end + 1 : end;
        }
        return topics.isEmpty() ? List.of() : List.copyOf(topics.values());
    }

    /**
     * 解析文本中的 {@code @用户名} 提及。
     * <p>
     * 规则与边界：
     * <ul>
     *   <li>解析文本为 {@code title + "\n" + content}，{@code null} 一律当空串，不抛异常。</li>
     *   <li>{@code @} 的前一个字符不能是 {@code [A-Za-z0-9_.-]}（文本开头的 {@code @} 一律允许），
     *       因此 {@code foo@bar.com}、{@code a@b} 不会匹配，而 {@code 谢谢@MapleLeaf} 会匹配。</li>
     *   <li>用户名按 {@code [A-Za-z0-9_]{1,20}} 贪婪匹配，遇到其它字符（含中文）立即停止。</li>
     *   <li>去重忽略大小写并保留首次出现的原始写法，最多 10 个。</li>
     * </ul>
     *
     * @param title   标题，可为 {@code null}
     * @param content 正文，可为 {@code null}
     * @return 不可变的提及列表，按出现顺序，最多 10 个；无结果时返回空列表
     */
    public static List<String> parseMentions(String title, String content) {
        String text = joinText(title, content);
        int length = text.length();
        // key 是小写用户名，value 是首次出现的原始写法，作用同 parseTopics
        Map<String, String> mentions = new LinkedHashMap<>(MAX_MENTION_COUNT);
        int index = 0;
        while (index < length && mentions.size() < MAX_MENTION_COUNT) {
            if (text.charAt(index) != MENTION_MARK) {
                index++;
                continue;
            }
            // 前一个字符是字母 / 数字 / 下划线 / 点 / 连字符时判定为邮箱或标识符的一部分，不算提及
            if (index > 0 && isMentionPrefixChar(text.charAt(index - 1))) {
                index++;
                continue;
            }
            int end = index + 1;
            while (end < length
                    && end - index - 1 < MAX_MENTION_LENGTH
                    && isMentionChar(text.charAt(end))) {
                end++;
            }
            if (end == index + 1) {
                // `@` 后面不是 [A-Za-z0-9_]：不构成提及，跳过这个 `@`
                index++;
                continue;
            }
            String name = text.substring(index + 1, end);
            mentions.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
            index = end;
        }
        return mentions.isEmpty() ? List.of() : List.copyOf(mentions.values());
    }

    /**
     * 拼接解析文本：{@code null} 当空串，标题与正文之间补一个换行，避免首尾字符被粘成一个词
     *
     * @param title   标题，可为 {@code null}
     * @param content 正文，可为 {@code null}
     * @return 拼接后的文本，永远不为 {@code null}
     */
    private static String joinText(String title, String content) {
        String safeTitle = title == null ? "" : title;
        String safeContent = content == null ? "" : content;
        return safeTitle + "\n" + safeContent;
    }

    /**
     * 判断字符是否是话题名的结束符：空白字符、另一个 {@code #}、或结束标点。
     * 空白同时用 {@link Character#isWhitespace(char)} 与 {@link Character#isSpaceChar(char)} 判定：
     * 前者不认不间断空格（U+00A0）等 Unicode 空格分隔符，只认前者会让「# + 不间断空格 + 猫」解析出带隐形
     * 前导空格的话题名，既难聚合又容易被用来伪造近似标签
     *
     * @param c 待判断字符
     * @return {@code true} 表示该字符终止话题名
     */
    private static boolean isTopicTerminator(char c) {
        return Character.isWhitespace(c)
                || Character.isSpaceChar(c)
                || c == TOPIC_MARK
                || TOPIC_END_PUNCTUATION.indexOf(c) >= 0;
    }

    /**
     * 判断字符是否属于提及用户名的合法字符 {@code [A-Za-z0-9_]}。
     * 刻意只认 ASCII：中文昵称无法与正文区分，强行匹配会把整句话吞进用户名
     *
     * @param c 待判断字符
     * @return {@code true} 表示该字符可出现在用户名中
     */
    private static boolean isMentionChar(char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '_';
    }

    /**
     * 判断字符是否会让紧跟其后的 {@code @} 失去提及资格，即 {@code [A-Za-z0-9_.-]}。
     * 覆盖 {@code foo@bar.com}（邮箱）、{@code a@b}、{@code 1.2@x} 这类写法
     *
     * @param c {@code @} 的前一个字符
     * @return {@code true} 表示 {@code @} 属于邮箱 / 标识符的一部分，不算提及
     */
    private static boolean isMentionPrefixChar(char c) {
        return isMentionChar(c) || c == '.' || c == '-';
    }
}