package com.MapleLeaf.MiaoWang.support;

/**
 * LIKE 关键词转义
 * <p>
 * MySQL 的 LIKE 通配符是 % 和 _，默认转义字符是反斜杠：用户搜 "100%" 时如果把关键字
 * 直接拼进 LIKE，% 会被当通配符，一条关键词就能把整张表捞出来（还能拿去试探数据量）。
 * 这里把 \ % _ 三个字符转义掉，配合 MyBatis-Plus 的 like / likeRight 使用
 * （它只负责在两侧补 %，不会转义我们传进去的值）。
 */
public final class SqlLikeUtil {

    private SqlLikeUtil() {
    }

    /**
     * 转义 LIKE 关键字里的通配符；null 原样返回
     */
    public static String escape(String keyword) {
        if (keyword == null) {
            return null;
        }
        return keyword.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}