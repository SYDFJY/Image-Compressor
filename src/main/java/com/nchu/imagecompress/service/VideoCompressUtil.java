package com.nchu.imagecompress.service;

import com.nchu.imagecompress.model.VideoCompressConfig;
import com.nchu.imagecompress.model.VideoFileInfo;
import com.nchu.imagecompress.util.LogUtil;
import com.nchu.imagecompress.util.VideoUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 视频压缩核心工具 — FFmpeg 命令构建、执行与进度解析。
 *
 * <p>通过 {@link ProcessBuilder} 调用外部 ffmpeg CLI 实现视频压缩。
 * 所有方法为静态工具方法。</p>
 *
 * <h3>FFmpeg 命令结构</h3>
 * <pre>
 * ffmpeg -i input.mp4 -c:v libx264 -crf 23 -preset medium
 *        -vf scale=1280:720 -r 30 -c:a aac -y output.mp4
 * </pre>
 *
 * @author NCHU-Student
 * @version 2.0.0
 * @since 2026-07-11
 */
public final class VideoCompressUtil {

    private VideoCompressUtil() { /* 工具类禁止实例化 */ }

    // ==================== 常量 ====================

    /** 解析 ffmpeg stderr 中 time= 的正则 */
    private static final Pattern TIME_PATTERN = Pattern.compile("time=(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{2})");

    /** 默认 FFmpeg preset（编码速度与压缩率的平衡） */
    private static final String DEFAULT_PRESET = "medium";

    // ==================== 命令构建 ====================

    /**
     * 根据配置构建完整的 FFmpeg 命令行。
     *
     * @param inputFile              输入视频文件
     * @param outputFile             输出视频文件
     * @param config                 视频压缩配置
     * @param effectiveDurationSeconds 有效视频时长（秒），用于目标大小→码率换算（已扣除裁剪）
     * @return FFmpeg 命令行参数列表
     */
    public static List<String> buildFfmpegCommand(File inputFile, File outputFile,
                                                   VideoCompressConfig config,
                                                   double effectiveDurationSeconds) {
        List<String> cmd = new ArrayList<>();
        cmd.add(VideoUtil.getFfmpegPath());

        // --- v2: 裁剪参数（输入 seek，放在 -i 之前，更健壮且更快） ---
        if (config.getStartTimeSeconds() > 0) {
            cmd.add("-ss");
            cmd.add(formatTimeArg(config.getStartTimeSeconds()));
        }
        if (config.getDurationSeconds() > 0) {
            cmd.add("-t");
            cmd.add(formatTimeArg(config.getDurationSeconds()));
        }

        // --- 输入文件 ---
        cmd.add("-i");
        cmd.add(inputFile.getAbsolutePath());

        // --- 视频编码器 ---
        String videoCodec = resolveVideoCodec(config);
        cmd.add("-c:v");
        cmd.add(videoCodec);

        // --- 码率控制：CRF 画质优先 vs 目标文件大小 vs 目标码率模式 ---
        if (config.getTargetSizeMB() > 0 && effectiveDurationSeconds > 0) {
            // 目标文件大小模式：根据目标大小自动计算码率
            int bitrateKbps = calculateBitrateKbps(config.getTargetSizeMB(),
                    effectiveDurationSeconds);
            cmd.add("-b:v");
            cmd.add(bitrateKbps + "k");
            cmd.add("-maxrate");
            cmd.add((bitrateKbps * 3 / 2) + "k");
            cmd.add("-bufsize");
            cmd.add((bitrateKbps * 2) + "k");
        } else if (config.getRateControlMode() == VideoCompressConfig.RateControlMode.TARGET_SIZE
                && config.getTargetBitrate() > 0) {
            cmd.add("-b:v");
            cmd.add(config.getTargetBitrate() + "k");
            cmd.add("-maxrate");
            cmd.add((config.getTargetBitrate() * 3 / 2) + "k");
            cmd.add("-bufsize");
            cmd.add((config.getTargetBitrate() * 2) + "k");
        } else {
            cmd.add("-crf");
            cmd.add(String.valueOf(config.getCrf()));
        }

        // --- 编码预设 ---
        cmd.add("-preset");
        cmd.add(DEFAULT_PRESET);

        // --- 分辨率缩放 ---
        // 使用 -2 占位符：自动计算高度、保持宽高比、保证被 2 整除
        // （H.264/VP9 yuv420p 要求宽高均为偶数，force_original_aspect_ratio
        //  可能导致竖屏视频缩放后宽度为奇数进而编码失败）
        if (config.getResolutionMode() != VideoCompressConfig.ResolutionMode.ORIGINAL) {
            int targetWidth = config.getResolutionMode().getMaxWidth();
            cmd.add("-vf");
            cmd.add("scale=" + targetWidth + ":-2");
        }

        // --- 帧率 ---
        if (config.getFpsMode() != VideoCompressConfig.FpsMode.ORIGINAL) {
            cmd.add("-r");
            cmd.add(String.valueOf(config.getFpsMode().getFps()));
        }

        // --- 音频处理 ---
        if (config.getAudioMode() == VideoCompressConfig.AudioMode.REMOVE) {
            cmd.add("-an");
        } else {
            cmd.add("-c:a");
            cmd.add(resolveAudioCodec(config));
        }

        // --- 覆盖输出（根据配置决定） ---
        if (config.isOverwrite()) {
            cmd.add("-y");
        }

        // --- 输出文件 ---
        cmd.add(outputFile.getAbsolutePath());

        return cmd;
    }

    /**
     * 根据输出格式选择合适的视频编码器。
     */
    private static String resolveVideoCodec(VideoCompressConfig config) {
        // 用户指定了编码器
        if (config.getVideoCodec() != null && !config.getVideoCodec().isEmpty()) {
            return config.getVideoCodec();
        }

        // 根据输出格式自动选择
        switch (config.getOutputFormat()) {
            case WEBM:
                return "libvpx-vp9";
            case MP4:
            case MOV:
            case AVI:
            case MKV:
            default:
                if (config.getCrf() <= 0) {
                    // 无损模式：libx264 支持 -crf 0，但更推荐 libx264rgb
                    return "libx264";
                }
                return "libx264";
        }
    }

    /**
     * 选择合适的音频编码器。
     */
    private static String resolveAudioCodec(VideoCompressConfig config) {
        if (config.getAudioCodec() != null && !config.getAudioCodec().isEmpty()) {
            return config.getAudioCodec();
        }
        switch (config.getOutputFormat()) {
            case WEBM: return "libopus";
            default:   return "aac";
        }
    }

    // ==================== 压缩执行 ====================

    /**
     * 进度回调接口（简化版，用于视频压缩进度报告）。
     */
    public interface VideoProgressCallback {
        void onProgress(double progress, String status);
    }

    /**
     * 执行 FFmpeg 压缩并报告进度。
     *
     * @param inputFile  输入文件
     * @param outputFile 输出文件
     * @param config     压缩配置
     * @param totalDurationSeconds 视频总时长（用于计算进度百分比）
     * @param callback   进度回调（可为 null）
     * @return true 表示压缩成功
     * @throws IOException 如果 ffmpeg 无法启动
     * @throws InterruptedException 如果线程被中断（取消操作）
     */
    public static boolean executeCompress(File inputFile, File outputFile,
                                           VideoCompressConfig config,
                                           double totalDurationSeconds,
                                           VideoProgressCallback callback)
            throws IOException, InterruptedException {

        List<String> command = buildFfmpegCommand(inputFile, outputFile, config,
                totalDurationSeconds);
        LogUtil.info("[VideoCompressUtil] 执行命令: " + String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false); // 分别处理 stdout 和 stderr

        Process process = pb.start();

        // 排空 stdout（防止管道满导致进程挂起）
        final Process finalProcess = process;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] buf = new byte[4096];
                    while (finalProcess.getInputStream().read(buf) != -1) { /* drain */ }
                } catch (IOException ignored) { /* stdout drained */ }
            }
        }, "FFmpeg-Stdout-Drainer").start();

        double lastReportedProgress = 0;

        // 读取 stderr（ffmpeg 输出：进度信息 + 错误信息）
        // 使用 ring buffer 保留最后 5 行非进度输出用于错误诊断
        final String[] lastErrorLines = new String[5];
        final int[] errorIdx = {0};

        try (BufferedReader errorReader = new BufferedReader(
                new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = errorReader.readLine()) != null) {
                // 检查是否被中断（取消操作）
                if (Thread.currentThread().isInterrupted()) {
                    process.destroyForcibly();
                    throw new InterruptedException("压缩任务被取消");
                }

                // 解析进度
                if (callback != null && totalDurationSeconds > 0) {
                    double currentTime = parseTime(line);
                    if (currentTime > 0) {
                        double progress = Math.min(currentTime / totalDurationSeconds, 0.99);
                        if (progress - lastReportedProgress >= 0.01) {
                            lastReportedProgress = progress;
                            callback.onProgress(progress, "压缩中... " + formatTime(currentTime));
                        }
                        continue;
                    }
                }

                // 保留非进度行用于错误诊断
                if (line != null && !line.isEmpty()
                        && !line.startsWith("frame=") && !line.startsWith("size=")) {
                    lastErrorLines[errorIdx[0] % 5] = line;
                    errorIdx[0]++;
                }
            }
        }

        // 等待进程完成（最多等待 1 小时）
        boolean finished = process.waitFor(3600, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("FFmpeg 执行超时（1小时）");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            // 拼接最后几行错误输出用于诊断
            StringBuilder errInfo = new StringBuilder();
            for (String err : lastErrorLines) {
                if (err != null) errInfo.append(err).append(" | ");
            }
            String detail = errInfo.length() > 0
                    ? " — " + errInfo.toString().trim()
                    : "";
            throw new IOException("FFmpeg 返回非零退出码: " + exitCode + detail);
        }

        // 验证输出文件存在且非空（< 1KB 视为无效，通常是因为裁剪参数超出视频范围导致无帧编码）
        if (!outputFile.exists() || outputFile.length() < 1024) {
            String hint = (config.getStartTimeSeconds() > 0 || config.getDurationSeconds() > 0)
                    ? "（裁剪参数可能超出视频时长范围，请检查起始时间和时长设置）" : "";
            throw new IOException("输出文件无效（无编码内容）: " + outputFile.getName() + " " + hint);
        }

        if (callback != null) {
            callback.onProgress(1.0, "压缩完成");
        }

        return true;
    }

    // ==================== 进度解析 ====================

    /**
     * 从 ffmpeg stderr 行中解析当前处理时间。
     *
     * <p>ffmpeg 输出格式：{@code frame=  123 fps= 30 q=28.0 size=    1024kB time=00:00:05.12 bitrate=1638.4kbits/s speed=1.5x}</p>
     *
     * @param line ffmpeg stderr 输出的一行
     * @return 当前时间（秒），未匹配时返回 -1
     */
    static double parseTime(String line) {
        if (line == null) return -1;
        Matcher matcher = TIME_PATTERN.matcher(line);
        if (matcher.find()) {
            try {
                int hours = Integer.parseInt(matcher.group(1));
                int minutes = Integer.parseInt(matcher.group(2));
                int seconds = Integer.parseInt(matcher.group(3));
                int centiseconds = Integer.parseInt(matcher.group(4));
                return hours * 3600.0 + minutes * 60.0 + seconds + centiseconds / 100.0;
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    // ==================== 工具方法 ====================

    /**
     * 根据输出配置生成输出文件路径。
     *
     * @param inputFile 输入文件
     * @param config    压缩配置
     * @return 输出文件对象
     */
    public static File generateOutputFile(File inputFile, VideoCompressConfig config) {
        String outputDir = config.getOutputPath();
        if (outputDir == null || outputDir.isEmpty()) {
            outputDir = inputFile.getParent();
        }

        String baseName = inputFile.getName();
        int dot = baseName.lastIndexOf('.');
        String nameWithoutExt = dot > 0 ? baseName.substring(0, dot) : baseName;

        // 确定输出扩展名
        String outputExt;
        if (config.getOutputFormat() == VideoCompressConfig.VideoFormat.ORIGINAL) {
            outputExt = dot > 0 ? baseName.substring(dot) : ".mp4";
        } else {
            outputExt = "." + config.getOutputFormat().getExtension();
        }

        // 自定义文件名优先
        String customName = config.getCustomName();
        String outputName;
        if (customName != null && !customName.trim().isEmpty()) {
            // 过滤非法字符
            String safe = customName.trim().replaceAll("[\\\\/:*?\"<>|]", "");
            outputName = (safe.isEmpty() ? nameWithoutExt + config.getSuffix() : safe) + outputExt;
        } else {
            outputName = nameWithoutExt + config.getSuffix() + outputExt;
        }
        return new File(outputDir, outputName);
    }

    /**
     * 格式化时间（秒）为 HH:MM:SS 字符串。
     */
    private static String formatTime(double totalSeconds) {
        int hours = (int) (totalSeconds / 3600);
        int minutes = (int) ((totalSeconds % 3600) / 60);
        int seconds = (int) (totalSeconds % 60);
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%d:%02d", minutes, seconds);
    }

    /**
     * 格式化时间为 FFmpeg 接受的参数格式。
     *
     * <p>整数秒 → 裸数字（如 "30"），小数秒 → 保留三位小数（如 "3.500"）。
     * 全部使用秒数格式，兼容所有 FFmpeg 版本，避免 MM:SS.ms 格式的解析歧义。</p>
     */
    private static String formatTimeArg(double seconds) {
        if (Math.abs(seconds - Math.round(seconds)) < 0.001) {
            return String.valueOf((int) Math.round(seconds));
        }
        return String.format("%.3f", seconds);
    }

    // ==================== 码率计算 ====================

    /**
     * 根据目标文件大小计算视频码率（kbps）。
     *
     * <p>公式：targetSizeMB × 8192 / durationSeconds × 0.95</p>
     * <ul>
     *   <li>8192 = 8 (bits/byte) × 1024 (KB/MB)</li>
     *   <li>0.95 = 预留 5% 给音频流和容器开销</li>
     * </ul>
     *
     * @param targetSizeMB       目标文件大小（MB）
     * @param durationSeconds    视频有效时长（秒），已扣除裁剪
     * @return 视频码率（kbps），最低 100 kbps
     */
    static int calculateBitrateKbps(double targetSizeMB, double durationSeconds) {
        double targetBits = targetSizeMB * 8.0 * 1024.0 * 1024.0;
        double videoBits = targetBits * 0.95;
        int bitrate = (int) (videoBits / durationSeconds / 1024.0);
        return Math.max(100, bitrate);
    }
}
