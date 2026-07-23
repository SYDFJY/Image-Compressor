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

        // --- 码率控制：CRF 画质优先 vs 目标文件大小（单遍快速模式 fallback） ---
        if (config.getRateControlMode() == VideoCompressConfig.RateControlMode.TARGET_SIZE
                && config.getTargetSizeMB() > 0 && effectiveDurationSeconds > 0) {
            // 目标文件大小 — 单遍快速模式（二遍编码入口在 executeCompress 中优先路由）
            boolean hasAudio = config.getAudioMode() != VideoCompressConfig.AudioMode.REMOVE;
            int bitrateKbps = calculateBitrateKbps(config.getTargetSizeMB(),
                    effectiveDurationSeconds, hasAudio);
            cmd.add("-b:v");
            cmd.add(bitrateKbps + "k");
            cmd.add("-maxrate");
            cmd.add((bitrateKbps * 11 / 10) + "k");   // 1.1x（v2.6: 原 1.5x 允许编码器严重超额）
            cmd.add("-bufsize");
            cmd.add((bitrateKbps * 3 / 2) + "k");       // 1.5x
        } else {
            cmd.add("-crf");
            cmd.add(String.valueOf(config.getCrf()));
        }

        // --- 编码预设（v2.6: 从 config 读取，不再硬编码 medium） ---
        cmd.add("-preset");
        cmd.add(config.getEncodePreset() != null && !config.getEncodePreset().isEmpty()
                ? config.getEncodePreset() : DEFAULT_PRESET);

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

        // v2.6: TARGET_SIZE 模式优先使用二遍编码精确命中目标
        if (config.getRateControlMode() == VideoCompressConfig.RateControlMode.TARGET_SIZE
                && config.getTargetSizeMB() > 0) {
            return executeTwoPassCompress(inputFile, outputFile, config,
                    totalDurationSeconds, callback);
        }

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
     * <p>公式：targetSizeMB × 8000 / durationSeconds − audioKbps − overhead</p>
     * <ul>
     *   <li>8000 = 8 (bits/byte) × 1000 (KB/MB)，k=1000 匹配 FFmpeg 的 {@code -b:v} 单位</li>
     *   <li>audioKbps = 有音频 ? 128 : 0（AAC 典型码率）</li>
     *   <li>overhead = 2% 容器开销（MOOV atom + 帧头 + 索引等）</li>
     * </ul>
     *
     * @param targetSizeMB       目标文件大小（MB）
     * @param durationSeconds    视频有效时长（秒），已扣除裁剪
     * @param hasAudio           是否包含音频流
     * @return 视频码率（kbps），最低 50 kbps
     * @since 2.6.0（修复 k=1000 单位匹配 + 音频感知 + 容器开销）
     */
    static int calculateBitrateKbps(double targetSizeMB, double durationSeconds, boolean hasAudio) {
        // FFmpeg 的 -b:v 使用 k=1000（非 1024），统一单位避免系统性偏差
        double targetKbps = targetSizeMB * 8000.0 / durationSeconds;  // 8 bit/byte × 1000
        double audioKbps = hasAudio ? 128 : 0;
        double overheadKbps = targetKbps * 0.02;
        double videoKbps = targetKbps - audioKbps - overheadKbps;
        return Math.max(50, (int) videoKbps);
    }

    // ==================== 二遍编码（Two-Pass） ====================

    /**
     * 使用 FFmpeg 二遍编码精确命中目标文件大小。
     *
     * <p>第一遍分析视频复杂度（无音频、无输出文件），第二遍根据分析
     * 结果精确分配码率。总时间约为单遍编码的 2.0-2.2 倍，但目标大小
     * 精度可达 ±5%。</p>
     *
     * @param inputFile             输入视频文件
     * @param outputFile            输出视频文件
     * @param config                视频压缩配置（含目标大小、preset 等）
     * @param totalDurationSeconds  视频有效时长（秒）
     * @param callback              进度回调（可为 null）
     * @return true 表示压缩成功
     * @throws IOException          如果 ffmpeg 无法启动
     * @throws InterruptedException 如果线程被中断（取消操作）
     * @since 2.6.0
     */
    public static boolean executeTwoPassCompress(File inputFile, File outputFile,
                                                  VideoCompressConfig config,
                                                  double totalDurationSeconds,
                                                  VideoProgressCallback callback)
            throws IOException, InterruptedException {

        boolean hasAudio = config.getAudioMode() != VideoCompressConfig.AudioMode.REMOVE;
        int bitrateKbps = calculateBitrateKbps(config.getTargetSizeMB(),
                totalDurationSeconds, hasAudio);

        LogUtil.info("[VideoCompressUtil] 二遍编码开始: target=" + config.getTargetSizeMB()
                + "MB, duration=" + String.format("%.1f", totalDurationSeconds)
                + "s, bitrate=" + bitrateKbps + "kbps, preset=" + config.getEncodePreset());

        // 输出目录作为 log 文件目录（避免污染用户目录）
        File logDir = outputFile.getParentFile();
        String logBase = new File(logDir, "ffmpeg2pass-" + outputFile.getName()).getAbsolutePath();

        // ==== Pass 1: 分析 ====
        if (callback != null) {
            callback.onProgress(0.0, "分析视频复杂度… (Pass 1/2)");
        }

        List<String> pass1Cmd = buildPassCommand(inputFile, config, bitrateKbps,
                totalDurationSeconds, 1, logBase, null);

        LogUtil.info("[VideoCompressUtil] Pass 1: " + String.join(" ", pass1Cmd));
        int exitCode = runFfmpegProcess(pass1Cmd, logDir, callback, totalDurationSeconds,
                0.0, 0.45, "分析中… (Pass 1/2)");
        if (exitCode != 0) {
            cleanupPassLogs(logBase);
            throw new IOException("FFmpeg Pass 1 失败，退出码: " + exitCode);
        }

        // 检查 log 文件是否生成
        File passLog = new File(logBase + "-0.log");
        if (!passLog.exists() || passLog.length() < 100) {
            cleanupPassLogs(logBase);
            throw new IOException("FFmpeg Pass 1 未生成有效的分析日志，"
                    + "请检查视频文件是否可解码");
        }

        // ==== Pass 2: 编码 ====
        if (callback != null) {
            callback.onProgress(0.45, "编码中… (Pass 2/2)");
        }

        List<String> pass2Cmd = buildPassCommand(inputFile, config, bitrateKbps,
                totalDurationSeconds, 2, logBase, outputFile);

        LogUtil.info("[VideoCompressUtil] Pass 2: " + String.join(" ", pass2Cmd));
        exitCode = runFfmpegProcess(pass2Cmd, logDir, callback, totalDurationSeconds,
                0.45, 0.99, "编码中… (Pass 2/2)");
        if (exitCode != 0) {
            cleanupPassLogs(logBase);
            throw new IOException("FFmpeg Pass 2 失败，退出码: " + exitCode);
        }

        // 清理临时文件
        cleanupPassLogs(logBase);

        // 验证输出
        if (!outputFile.exists() || outputFile.length() < 1024) {
            throw new IOException("输出文件无效（无编码内容）: " + outputFile.getName());
        }

        if (callback != null) {
            callback.onProgress(1.0, "压缩完成");
        }

        LogUtil.info("[VideoCompressUtil] 二遍编码完成: output="
                + VideoFileInfo.formatFileSize(outputFile.length()));
        return true;
    }

    /**
     * 构建单遍 FFmpeg 命令（Pass 1 或 Pass 2 通用）。
     *
     * @param outputFile Pass 2 的输出文件，Pass 1 时传 null
     * @param pass       1 或 2
     * @param logBase    Pass log 文件的基础路径（不含 -0.log 后缀）
     */
    private static List<String> buildPassCommand(File inputFile, VideoCompressConfig config,
                                                  int bitrateKbps, double durationSeconds,
                                                  int pass, String logBase, File outputFile) {
        List<String> cmd = new ArrayList<>();
        cmd.add(VideoUtil.getFfmpegPath());

        // 覆盖输出（-y 在 pass 1 也有效）
        cmd.add("-y");

        // 裁剪参数（两遍必须相同）
        if (config.getStartTimeSeconds() > 0) {
            cmd.add("-ss");
            cmd.add(formatTimeArg(config.getStartTimeSeconds()));
        }
        if (config.getDurationSeconds() > 0) {
            cmd.add("-t");
            cmd.add(formatTimeArg(config.getDurationSeconds()));
        }

        cmd.add("-i");
        cmd.add(inputFile.getAbsolutePath());

        // 视频编码器
        cmd.add("-c:v");
        cmd.add(resolveVideoCodec(config));

        // 码率控制
        cmd.add("-b:v");
        cmd.add(bitrateKbps + "k");

        // Pass 标志
        cmd.add("-pass");
        cmd.add(String.valueOf(pass));
        cmd.add("-passlogfile");
        cmd.add(logBase);

        // 编码预设
        cmd.add("-preset");
        cmd.add(config.getEncodePreset());

        // 分辨率缩放
        if (config.getResolutionMode() != VideoCompressConfig.ResolutionMode.ORIGINAL) {
            int targetWidth = config.getResolutionMode().getMaxWidth();
            cmd.add("-vf");
            cmd.add("scale=" + targetWidth + ":-2");
        }

        // 帧率
        if (config.getFpsMode() != VideoCompressConfig.FpsMode.ORIGINAL) {
            cmd.add("-r");
            cmd.add(String.valueOf(config.getFpsMode().getFps()));
        }

        // 音频（Pass 1 跳过音频以加速分析）
        if (pass == 1) {
            cmd.add("-an");
        } else if (config.getAudioMode() == VideoCompressConfig.AudioMode.REMOVE) {
            cmd.add("-an");
        } else {
            cmd.add("-c:a");
            cmd.add(resolveAudioCodec(config));
        }

        // 输出
        if (pass == 1) {
            // Pass 1: 不写输出文件
            cmd.add("-f");
            cmd.add("mp4");
            cmd.add(isWindows() ? "NUL" : "/dev/null");
        } else {
            cmd.add(outputFile.getAbsolutePath());
        }

        return cmd;
    }

    /** 执行 FFmpeg 进程并返回退出码，同时解析进度。 */
    private static int runFfmpegProcess(List<String> command, File workingDir,
                                         VideoProgressCallback callback,
                                         double totalDuration, double progressMin,
                                         double progressMax, String statusPrefix)
            throws IOException, InterruptedException {

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDir);

        Process process = pb.start();

        // 排空 stdout
        final Process finalProcess = process;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] buf = new byte[4096];
                    while (finalProcess.getInputStream().read(buf) != -1) { /* drain */ }
                } catch (IOException ignored) { }
            }
        }, "FFmpeg-Stdout-Drainer").start();

        double lastProgress = 0;
        final String[] lastErrorLines = new String[5];
        final int[] errorIdx = {0};

        try (BufferedReader errorReader = new BufferedReader(
                new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = errorReader.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    process.destroyForcibly();
                    throw new InterruptedException("压缩任务被取消");
                }

                if (callback != null && totalDuration > 0) {
                    double currentTime = parseTime(line);
                    if (currentTime > 0) {
                        double frac = Math.min(currentTime / totalDuration, 1.0);
                        double progress = progressMin + frac * (progressMax - progressMin);
                        if (progress - lastProgress >= 0.01) {
                            lastProgress = progress;
                            callback.onProgress(Math.min(progress, 0.99),
                                    statusPrefix + " " + formatTime(currentTime));
                        }
                        continue;
                    }
                }

                if (line != null && !line.isEmpty()
                        && !line.startsWith("frame=") && !line.startsWith("size=")) {
                    lastErrorLines[errorIdx[0] % 5] = line;
                    errorIdx[0]++;
                }
            }
        }

        boolean finished = process.waitFor(7200, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("FFmpeg 执行超时（2小时）");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            StringBuilder errInfo = new StringBuilder();
            for (String err : lastErrorLines) {
                if (err != null) errInfo.append(err).append(" | ");
            }
            if (errInfo.length() > 0) {
                LogUtil.error("[VideoCompressUtil] FFmpeg stderr: " + errInfo.toString().trim());
            }
        }

        return exitCode;
    }

    /** 清理二遍编码生成的临时日志文件。 */
    private static void cleanupPassLogs(String logBase) {
        String[] suffixes = {"-0.log", "-0.log.mbtree"};
        for (String suffix : suffixes) {
            File f = new File(logBase + suffix);
            if (f.exists()) {
                if (!f.delete()) {
                    f.deleteOnExit();
                }
            }
        }
    }

    /** 检测当前运行平台是否为 Windows。 */
    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
