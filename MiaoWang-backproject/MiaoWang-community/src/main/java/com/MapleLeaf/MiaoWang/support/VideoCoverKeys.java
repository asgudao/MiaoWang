package com.MapleLeaf.MiaoWang.support;

/**
 * 视频封面对象 key 的<b>唯一推导点</b>
 * <p>
 * 封面对象 key 不是随机 UUID，而是**由视频对象 key 确定性推导**出来的：
 * 同一段视频永远算出同一个封面 key。这条约定换来三件事：
 * <ol>
 *     <li><b>写入幂等</b>：上传路径与回填工具算出的 key 必然相同，重复回填是"覆盖同一个对象"，
 *         不会在桶里堆出一串孤儿封面；</li>
 *     <li><b>可反查</b>：只拿到视频 objectKey 就能知道"它的封面该在哪"，回填/对账不需要额外的映射表；</li>
 *     <li><b>可并存</b>：推导规则是"去掉扩展名 + {@value #COVER_SUFFIX}"，
 *         所以 {@code a/b/&lt;uuid&gt;.mp4} 与 {@code a/b/&lt;uuid&gt;.mov} 不会算出同一个 key
 *         （上传路径的 key 里已经带 UUID，重名只可能来自同一次上传的同一份字节）。</li>
 * </ol>
 * <p>
 * ⚠️ 推导只做字符串变换，<b>不访问对象存储</b>：调用方要自己判断"这个封面对象到底存不存在"
 * （上传路径刚写过所以一定在；回填工具用 statObject 探一次）。
 * 这也是为什么 {@code PostServiceImpl} 不在这里"顺手推一个封面 URL 出来" ——
 * 推得出 key 不等于对象存在，直接下发会让前端拿到 404 的图（比中性占位更糟）。
 */
public final class VideoCoverKeys {

    /**
     * 封面文件后缀，拼在视频对象 key 去掉扩展名之后
     */
    public static final String COVER_SUFFIX = "_cover.jpg";

    /**
     * 封面对象的内容类型（固定 JPEG：抽帧只输出这一种格式）
     */
    public static final String COVER_CONTENT_TYPE = "image/jpeg";

    private VideoCoverKeys() {
    }

    /**
     * 由视频对象 key 推导封面对象 key；入参为空、或为空串时返回 {@code null}
     * <p>
     * 只剥**最后一段路径**里的扩展名：{@code community/video/20260922/ab.mp4}
     * → {@code community/video/20260922/ab_cover.jpg}。
     * 目录里带点号（例如 {@code .../v1.2/ab}）不会被误当成扩展名。
     */
    public static String coverObjectKeyOf(String videoObjectKey) {
        if (videoObjectKey == null) {
            return null;
        }
        String key = videoObjectKey.trim();
        if (key.isEmpty()) {
            return null;
        }
        int lastSlash = key.lastIndexOf('/');
        int lastDot = key.lastIndexOf('.');
        // 点号必须在最后一段路径里，才算"扩展名"
        String base = lastDot > lastSlash ? key.substring(0, lastDot) : key;
        return base + COVER_SUFFIX;
    }
}