package com.nchu.imagecompress.util;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * ffmpeg 管道帧渲染工具类 — 通过 ffmpeg 子进程输出 raw RGB24 帧到 stdout，
 * Java 端逐帧读取并转换为 {@link BufferedImage}。
 *
 * <h3>工作原理</h3>
 * <pre>
 *   ffmpeg -i input.mp4 -f rawvideo -pix_fmt rgb24 -r 15 -vf scale=640:360 -
 *          │                                              │
 *          └── 输入文件                                    └── stdout 管道（原始 RGB 像素流）
 * </pre>
 *
 * <p>每帧大小 = {@code width × height × 3} 字节（R-G-B 顺序，无填充）。
 * Java 端按固定大小读取每帧，转换为 TYPE_INT_RGB 的 BufferedImage。</p>
 *
 * <p>所有方法为静态工具方法，无状态，可跨线程安全调用。</p>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-13
 */
public final class FfmpegRenderUtil {

    private FfmpegRenderUtil() { /* 工具类禁止实例化 */ }

    // ==================== 常量 ====================

    /** 预览目标宽度（像素），高度按比例缩放保持宽高比 */
    private static final int PREVIEW_WIDTH = 640;

    // ==================== 帧流启动 ====================

    /**
     * 启动 ffmpeg 子进程，持续输出 raw RGB24 帧到 stdout。
     *
     * <p>ffmpeg 命令等效于：
     * {@code ffmpeg -re -loglevel error -i <file> -f rawvideo -pix_fmt rgb24
     *   -r <fps> -vf scale=<w>:<h> -}</p>
     *
     * <p>{@code -re} 标志确保以原生帧率读取输入，输出节奏与实时播放一致。</p>
     *
     * <p>调用方负责：
     * <ul>
     *   <li>从返回的 Process 的 {@code getInputStream()} 读取帧数据</li>
     *   <li>读取完毕后调用 {@code process.destroy()} 终止子进程</li>
     * </ul>
     *
     * @param videoFile   视频文件
     * @param targetWidth 目标宽度（像素），高度按比例计算
     * @param fps         目标帧率（fps），传入原始视频帧率以保持播放节奏
     * @return 已启动的 ffmpeg Process 对象
     * @throws IOException 如果 ffmpeg 无法启动
     */
    public static Process startFrameStream(File videoFile, int targetWidth, double fps)
            throws IOException {
        return startFrameStream(videoFile, targetWidth, -1, 0, fps);
    }

    /**
     * 启动 ffmpeg 子进程，可指定宽高、跳帧起始时间和输出帧率。
     *
     * @param videoFile   视频文件
     * @param targetWidth 目标宽度（像素），-1 表示使用原始尺寸
     * @param targetHeight 目标高度（像素），-1 表示按宽度比例计算
     * @param seekSeconds 跳转到指定秒数开始输出（0 表示从头开始）
     * @param fps         输出帧率（fps），传入原始视频帧率以保持播放节奏
     * @return 已启动的 ffmpeg Process 对象
     * @throws IOException 如果 ffmpeg 无法启动
     */
    public static Process startFrameStream(File videoFile, int targetWidth,
                                           int targetHeight, double seekSeconds, double fps)
            throws IOException {
        if (videoFile == null || !videoFile.exists()) {
            throw new IOException("视频文件不存在: " + videoFile);
        }

        List<String> args = new ArrayList<>();
        args.add(resolveFfmpeg());

        // 跳帧（非零时）
        if (seekSeconds > 0.01) {
            args.add("-ss");
            args.add(String.format("%.3f", seekSeconds));
        }

        // -re: 以原生帧率读取输入，保证输出节奏 = 实际播放速度
        args.add("-re");
        args.add("-loglevel");
        args.add("error");
        args.add("-i");
        args.add(videoFile.getAbsolutePath());
        args.add("-f");
        args.add("rawvideo");
        args.add("-pix_fmt");
        args.add("rgb24");

        // 帧率：优先使用传入的 fps，否则用默认 24
        double outputFps = (fps > 0) ? fps : 24.0;
        args.add("-r");
        args.add(String.format("%.2f", outputFps));

        // 缩放
        if (targetWidth > 0 || targetHeight > 0) {
            args.add("-vf");
            if (targetWidth > 0 && targetHeight > 0) {
                args.add(String.format("scale=%d:%d", targetWidth, targetHeight));
            } else if (targetWidth > 0) {
                // 宽度固定，高度按比例：scale=640:-2（-2 保证偶数高度）
                args.add(String.format("scale=%d:-2", targetWidth));
            } else {
                args.add(String.format("scale=-2:%d", targetHeight));
            }
        }

        args.add("-");

        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectErrorStream(false);

        LogUtil.info("[FfmpegRenderUtil] 启动帧流: " + String.join(" ", args));

        Process process = pb.start();

        // 排空 stderr（防止管道缓冲区满导致 ffmpeg 阻塞）
        // JDK 8 无 Redirect.DISCARD，必须用守护线程手动排空
        final InputStream stderr = process.getErrorStream();
        Thread drainer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] buf = new byte[4096];
                    while (stderr.read(buf) != -1) { /* drain silently */ }
                } catch (IOException ignored) { /* process terminated */ }
            }
        }, "Ffmpeg-Stderr-Drainer");
        drainer.setDaemon(true);
        drainer.start();

        return process;
    }

    /**
     * 启动 ffmpeg 只输出一帧（用于提取封面/首帧，默认从第 0 秒截取）。
     *
     * @param videoFile   视频文件
     * @param targetWidth 目标宽度
     * @return 已启动的 ffmpeg Process（需调用方读取这唯一一帧后关闭）
     * @throws IOException 如果 ffmpeg 无法启动
     */
    public static Process startSingleFrame(File videoFile, int targetWidth) throws IOException {
        return startSingleFrame(videoFile, targetWidth, 0);
    }

    /**
     * 启动 ffmpeg 只输出一帧（用于提取封面/首帧/指定时间点快照）。
     *
     * @param videoFile   视频文件
     * @param targetWidth 目标宽度
     * @param seekSeconds 截取时间点（秒），0 表示第一帧
     * @return 已启动的 ffmpeg Process（需调用方读取这唯一一帧后关闭）
     * @throws IOException 如果 ffmpeg 无法启动
     */
    public static Process startSingleFrame(File videoFile, int targetWidth,
                                           double seekSeconds) throws IOException {
        if (videoFile == null || !videoFile.exists()) {
            throw new IOException("视频文件不存在: " + videoFile);
        }

        List<String> args = new ArrayList<>();
        args.add(resolveFfmpeg());
        args.add("-ss");
        args.add(String.format("%.3f", seekSeconds));
        args.add("-loglevel");
        args.add("error");
        args.add("-i");
        args.add(videoFile.getAbsolutePath());
        args.add("-vframes");
        args.add("1");
        args.add("-f");
        args.add("rawvideo");
        args.add("-pix_fmt");
        args.add("rgb24");

        if (targetWidth > 0) {
            args.add("-vf");
            args.add(String.format("scale=%d:-2", targetWidth));
        }

        args.add("-");

        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectErrorStream(false);

        LogUtil.info("[FfmpegRenderUtil] 提取首帧: " + String.join(" ", args));

        Process process = pb.start();

        // 排空 stderr
        final InputStream stderr = process.getErrorStream();
        Thread drainer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] buf = new byte[4096];
                    while (stderr.read(buf) != -1) { /* drain */ }
                } catch (IOException ignored) { }
            }
        }, "Ffmpeg-Frame-Stderr-Drainer");
        drainer.setDaemon(true);
        drainer.start();

        return process;
    }

    // ==================== 帧读取 ====================

    /**
     * 从 ffmpeg stdout 流中读取一帧 RGB24 原始数据。
     *
     * <p>帧大小 = {@code width × height × 3} 字节。
     * 如果流中剩余数据不足一帧，返回 null（视频流结束）。</p>
     *
     * @param in     ffmpeg 的 stdout 输入流
     * @param width  帧宽度（像素）
     * @param height 帧高度（像素）
     * @return RGB24 字节数组，流结束时返回 null
     * @throws IOException 读取失败时抛出
     */
    public static byte[] readFrame(InputStream in, int width, int height) throws IOException {
        int frameSize = width * height * 3;
        byte[] frame = new byte[frameSize];
        int offset = 0;
        while (offset < frameSize) {
            int bytesRead = in.read(frame, offset, frameSize - offset);
            if (bytesRead == -1) {
                // 流结束
                if (offset == 0) {
                    return null; // 刚好在帧边界结束
                }
                // 部分帧数据 — ffmpeg 异常退出
                LogUtil.info("[FfmpegRenderUtil] 帧数据不完整 (已读 " + offset
                        + " / " + frameSize + " bytes)");
                return null;
            }
            offset += bytesRead;
        }
        return frame;
    }

    // ==================== 帧转换 ====================

    /**
     * 将 RGB24 字节数组转换为 {@link BufferedImage}（TYPE_INT_RGB）。
     *
     * <p>使用 {@link DataBufferInt} 直接写像素数组，
     * 比逐像素 {@code setRGB()} 快约 10 倍。</p>
     *
     * @param rgb    RGB24 字节数组（R-G-B 顺序，每像素 3 字节）
     * @param width  图像宽度
     * @param height 图像高度
     * @return BufferedImage，如果输入无效返回一张纯黑占位图
     */
    public static BufferedImage rgbToImage(byte[] rgb, int width, int height) {
        if (rgb == null || width <= 0 || height <= 0) {
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

        // RGB24 (3 bytes/pixel) → TYPE_INT_RGB (packed int)
        int pixelIndex = 0;
        int byteIndex = 0;
        int expectedSize = width * height * 3;
        int maxByteIndex = Math.min(rgb.length, expectedSize);

        while (byteIndex + 2 < maxByteIndex && pixelIndex < pixels.length) {
            // R, G, B 各 1 字节，组合成 int: 0x00RRGGBB
            int r = rgb[byteIndex] & 0xFF;
            int g = rgb[byteIndex + 1] & 0xFF;
            int b = rgb[byteIndex + 2] & 0xFF;
            pixels[pixelIndex] = (r << 16) | (g << 8) | b;
            pixelIndex++;
            byteIndex += 3;
        }

        return image;
    }

    // ==================== 尺寸计算 ====================

    /**
     * 计算保持宽高比的预览尺寸。
     *
     * @param origW       原始宽度
     * @param origH       原始高度
     * @param targetWidth 目标宽度
     * @return 缩放后的尺寸（宽, 高），宽高均为偶数（编解码器要求）
     */
    public static Dimension calculatePreviewSize(int origW, int origH, int targetWidth) {
        if (origW <= 0 || origH <= 0) {
            return new Dimension(targetWidth, (int) (targetWidth * 0.5625)); // 16:9 fallback
        }
        int w = targetWidth;
        int h = (int) ((double) origH / origW * targetWidth);
        // 确保偶数（某些编解码器要求）
        if (w % 2 != 0) w--;
        if (h % 2 != 0) h--;
        return new Dimension(w, h);
    }

    /**
     * 获取默认预览宽度。
     */
    public static int getPreviewWidth() {
        return PREVIEW_WIDTH;
    }

    /**
     * 获取默认预览帧率（当无法获取原始帧率时的兜底值）。
     */
    public static int getDefaultFps() {
        return 24; // 电影标准帧率，通用兜底
    }

    /**
     * 根据帧率计算帧间隔（毫秒），用于 Timer 周期。
     *
     * @param fps 目标帧率
     * @return 帧间隔（毫秒），fps ≤ 0 时返回默认 42ms（≈24fps）
     */
    public static int getFrameIntervalMs(double fps) {
        if (fps <= 0) return 42; // 兜底 ~24fps
        return (int) (1000.0 / fps);
    }

    // ==================== 内部工具 ====================

    /**
     * 解析 ffmpeg 命令路径（委托 VideoUtil 统一自动发现）。
     */
    private static String resolveFfmpeg() {
        return VideoUtil.getFfmpegPath();
    }
}
