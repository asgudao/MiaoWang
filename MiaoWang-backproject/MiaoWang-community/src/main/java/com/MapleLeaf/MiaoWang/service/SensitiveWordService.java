package com.MapleLeaf.MiaoWang.service;

import java.util.List;

/**
 * 敏感词过滤
 * <p>
 * 用途：发帖、编辑帖子、发表评论之前先做一次内容校验，命中敏感词就拦下来或提示用户改内容，
 * 让违规内容在写入口就被挡住，而不是等审核系统事后清理。
 */
public interface SensitiveWordService {

    /**
     * 找出文本里命中的敏感词：去重，并按每个词在文本中首次出现的位置排序
     *
     * @param text 待检查内容（帖子正文、评论正文等）
     * @return 命中的敏感词；无命中、text 为 null 或纯空白、词表为空时返回空集合，绝不返回 null，也不抛异常
     */
    List<String> matches(String text);

    /**
     * 文本里是否命中至少一个敏感词
     * <p>
     * 调用方只关心"能不能发"时用这个，不必自己判空 matches 的结果。
     *
     * @param text 待检查内容
     * @return true 表示命中
     */
    boolean contains(String text);
}