package com.MapleLeaf.MiaoWang.support;

import lombok.extern.slf4j.Slf4j;
import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

/**
 * 视频抽首帧（纯 Java，jcodec）
 * <p>
 * <b>职责边界</b>：只做"一个视频文件 → 一帧 JPEG 字节"这一件事。
 * 不碰 MinIO、不碰数据库、不读配置 —— 所以它既能被上传路径调用，
 * 也能被 {@code dev-tools/video-cover-backfill} 那个独立工具直接调用（不启动 Spring 也能跑）。
 * <p>
 * <b>失败契约（最重要的一条）</b>：任何异常/不支持的编码，一律返回 {@code null}，
 * <b>从不抛异常</b>。封面是装饰，抽不出来就回退成前端的中性占位，
 * 绝不能让"抽帧失败"把一次正常的视频上传或一次回填变成报错。
 * <p>
 * <b>为什么是 jcodec 而不是 JavaCV / 外部 ffmpeg</b>：
 * <ul>
 *     <li>本机没有 ffmpeg（外部进程方案要先装系统软件）；</li>
 *     <li>{@code javacv-platform} 会把各平台 ffmpeg 原生包全拉下来（数百 MB ~ 1GB），
 *         而这里只需要"解一帧 H.264 存 JPEG"；jcodec 纯 Java、约 2.5MB、零原生依赖；</li>
 *     <li>实测本仓库 4 个素材 mp4（720×720 / 10.08s / H.264 avc1 + AAC / faststart）
 *         全部能抽帧成功（见 .dsh-tmp/video-cover-extract-20260922/ 下的探针输出）。</li>
 * </ul>
 * <b>代价（如实写清）</b>：jcodec 的 H.264 解码器覆盖面不如 ffmpeg ——
 * 不认 HEVC/H.265、不认部分高阶 H.264 profile（B 帧密集/CABAC 边界情况）。
 * 这类视频走的就是上面那条"返回 null"的降级路径（无封面，不是报错）。
 * 将来若发现素材普遍抽不出来，替代方案是装一个 ffmpeg 可执行文件，
 * 把本类改成"调外部进程"即可 —— <b>调用方接口（返回 byte[] 或 null）不用改</b>。
 * <p>
 * ⚠️ 依赖 AWT（{@code AWTUtil.toBufferedImage} + {@code ImageIO}）。
 * 服务端必须跑在 headless 模式：Spring Boot 启动时会自己设 {@code java.awt.headless=true}，
 * 所以不需要额外配置；但如果有谁用 {@code java -jar} 手工起或写单测，注意这一点。
 */
@Slf4j
public final class VideoCoverExtractor {

    /**
     * JPEG 输出的初始缓冲大小（实测 720×720 的帧约 30~45KB，取 64KB 避免反复扩容）
     */
    private static final int BUFFER_SIZE = 64 * 1024;

    private VideoCoverExtractor() {
    }

    /**
     * 抽一帧存成 JPEG
     *
     * @param videoFile  磁盘上的视频文件（jcodec 要求可随机寻址，所以必须是文件而不是任意流）
     * @param seekSecond 定位到第几秒取帧；{@code <= 0} 表示直接取第 0 帧。
     *                   默认建议 0.1s：第 0 帧在部分素材上是纯黑/片头黑场，往后挪一点更保险
     *                   （实测本仓库 4 个素材的 0.0s 与 0.1s 都是正常画面，没有黑帧）。
     * @return JPEG 字节；抽不出来一律返回 {@code null}（调用方按"无封面"处理）
     */
    public static byte[] extractFirstFrameJpeg(Path videoFile, double seekSecond) {
        if (videoFile == null) {
            return null;
        }
        double at = Math.max(seekSecond, 0d);
        try (SeekableByteChannel channel = NIOUtils.readableChannel(videoFile.toFile())) {
            FrameGrab frameGrab = FrameGrab.createFrameGrab(channel);
            if (at > 0) {
                frameGrab.seekToSecondPrecise(at);
            }
            Picture picture = frameGrab.getNativeFrame();
            if (picture == null) {
                log.warn("视频抽帧拿不到画面（编解码不支持或视频损坏），降级为无封面, file={}, seekSecond={}", videoFile, at);
                return null;
            }
            BufferedImage image = AWTUtil.toBufferedImage(picture);
            ByteArrayOutputStream output = new ByteArrayOutputStream(BUFFER_SIZE);
            if (!ImageIO.write(image, "jpg", output)) {
                log.warn("视频抽帧后 JPEG 编码失败（运行环境缺 JPEG writer），降级为无封面, file={}", videoFile);
                return null;
            }
            return output.toByteArray();
        } catch (Throwable t) {
            // 这里**故意**接 Throwable 而不是 Exception：
            // jcodec 在遇到不认识的 NAL/不支持的编码时有机会抛 Error 族的异常，
            // 而"封面抽不出来"这件事的重要性远低于"视频上传必须成功"。
            log.warn("视频抽帧失败（降级为无封面，不影响上传/回填主线）, file={}, seekSecond={}, reason={}",
                    videoFile, at, t.toString());
            log.debug("视频抽帧失败详情", t);
            return null;
        }
    }
}