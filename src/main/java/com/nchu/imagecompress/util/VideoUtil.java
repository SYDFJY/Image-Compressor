package com.nchu.imagecompress.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nchu.imagecompress.model.VideoFileInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 视频工具类 — FFmpeg 检测、ffprobe 元数据解析、视频格式支持。
 *
 * <p>所有方法均为静态工具方法，无状态，可跨线程安全调用。</p>
 *
 * <h3>依赖</h3>
 * <p>需要系统 PATH 中存在 {@code ffmpeg} 和 {@code ffprobe} 命令行工具（FFmpeg 4.0+）。
 * 启动时通过 {@link #checkFfmpegAvailable()} 检测可用性。</p>
 *
 * @author NCHU-Student
 * @version 2.0.0
 * @since 2026-07-11
 */
public final class VideoUtil {

    private VideoUtil() { /* 工具类禁止实例化 */ }

    // ==================== 常量 ====================

    /** 支持的视频文件扩展名 */
    private static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mp4", "avi", "mov", "mkv", "webm", "flv", "wmv", "m4v", "ts", "3gp", "ogv", "rmvb"
    ));

    /** Gson 实例（复用） */
    private static final Gson GSON = new Gson();

    /** 缓存的 FFmpeg 可用性检测结果 */
    private static Boolean ffmpegAvailable = null;

    /** FFmpeg 二进制目录（通过系统属性 ffmpeg.bin.path 配置，用于测试） */
    private static final String FFMPEG_BIN_PATH = System.getProperty("ffmpeg.bin.path", "");

    /** 自动发现的 FFmpeg 二进制目录（首次调用 resolveCommand 时扫描并缓存） */
    private static String autoDiscoveredFfmpegPath = null;
    private static boolean autoDiscoveryDone = false;

    // ==================== 环境检测 ====================

    /**
     * 获取 FFmpeg 可执行文件路径（含完整目录），供压缩/渲染服务使用。
     *
     * <p>查找优先级与 {@link #resolveCommand(String)} 一致：
     * 系统属性 → 自动发现 → 裸命令名。</p>
     *
     * @return ffmpeg 可执行文件路径，未找到则返回 {@code "ffmpeg"}
     */
    public static String getFfmpegPath() {
        return resolveCommand("ffmpeg");
    }

    /**
     * 获取 ffprobe 可执行文件路径（含完整目录），供元数据解析使用。
     *
     * @return ffprobe 可执行文件路径，未找到则返回 {@code "ffprobe"}
     */
    public static String getFfprobePath() {
        return resolveCommand("ffprobe");
    }

    /**
     * 检测 FFmpeg 是否在系统 PATH 中可用。
     *
     * <p>结果会被缓存，首次调用后不再重复检测。
     * 可通过系统属性 {@code ffmpeg.bin.path} 指定 FFmpeg 二进制目录。</p>
     *
     * @return true 表示 ffmpeg 和 ffprobe 均可正常调用
     */
    public static boolean checkFfmpegAvailable() {
        if (ffmpegAvailable != null) {
            return ffmpegAvailable;
        }
        String ffmpegCmd = resolveCommand("ffmpeg");
        String ffprobeCmd = resolveCommand("ffprobe");
        ffmpegAvailable = checkCommand(ffmpegCmd + " -version")
                && checkCommand(ffprobeCmd + " -version");
        LogUtil.info("[VideoUtil] FFmpeg 可用性: " + ffmpegAvailable);
        return ffmpegAvailable;
    }

    /**
     * 解析命令路径：按优先级查找 ffmpeg/ffprobe 可执行文件。
     *
     * <p>优先级：系统属性 ffmpeg.bin.path → 自动发现的目录 → 纯命令名（依赖 PATH）。</p>
     */
    private static String resolveCommand(String command) {
        if (!FFMPEG_BIN_PATH.isEmpty()) {
            return FFMPEG_BIN_PATH + File.separator + command;
        }
        if (!autoDiscoveryDone) {
            autoDiscoveredFfmpegPath = autoDiscoverFfmpegPath();
            autoDiscoveryDone = true;
        }
        if (autoDiscoveredFfmpegPath != null) {
            return autoDiscoveredFfmpegPath + File.separator + command;
        }
        return command;
    }

    /**
     * 自动扫描常见 Windows 安装目录，定位 ffprobe.exe 所在目录。
     *
     * <p>搜索顺序：
     * <ol>
     *   <li>{@code FFMPEG_HOME} 环境变量</li>
     *   <li>{@code %USERPROFILE%\.ffmpeg\bin\}</li>
     *   <li>{@code %LOCALAPPDATA%\ffmpeg\bin\}</li>
     *   <li>winget 安装目录（Gyan/BtbN 等常见发行版）</li>
     *   <li>{@code C:\ffmpeg\bin\}</li>
     *   <li>{@code C:\Program Files\ffmpeg\bin\}</li>
     *   <li>Chocolatey: {@code C:\ProgramData\chocolatey\bin\}</li>
     *   <li>Scoop: {@code %USERPROFILE%\scoop\shims\}</li>
     * </ol>
     *
     * @return 包含 ffprobe.exe 的目录绝对路径，未找到则返回 null
     */
    private static String autoDiscoverFfmpegPath() {
        // 1. FFMPEG_HOME 环境变量
        String ffmpegHome = System.getenv("FFMPEG_HOME");
        if (ffmpegHome != null && !ffmpegHome.isEmpty()) {
            String binDir = ffmpegHome.endsWith("bin") ? ffmpegHome : ffmpegHome + File.separator + "bin";
            if (new File(binDir, "ffprobe.exe").exists() || new File(binDir, "ffprobe").exists()) {
                LogUtil.info("[VideoUtil] 自动发现 FFmpeg (FFMPEG_HOME): " + binDir);
                return binDir;
            }
        }

        // 2. 常见 Windows 安装目录
        String userHome = System.getProperty("user.home");
        String localAppData = System.getenv("LOCALAPPDATA");

        String[][] candidateDirs = {
            {userHome, ".ffmpeg", "bin"},
            {localAppData, "ffmpeg", "bin"},
            {localAppData, "Microsoft", "WinGet", "Packages"},
            {"C:", "ffmpeg", "bin"},
            {"C:", "Program Files", "ffmpeg", "bin"},
            {"C:", "Program Files (x86)", "ffmpeg", "bin"},
            {"C:", "ProgramData", "chocolatey", "bin"},
            {userHome, "scoop", "shims"},
        };

        for (String[] parts : candidateDirs) {
            String dir = String.join(File.separator, parts);
            if (new File(dir, "ffprobe.exe").exists() || new File(dir, "ffprobe").exists()) {
                LogUtil.info("[VideoUtil] 自动发现 FFmpeg: " + dir);
                return dir;
            }
        }

        // 3. 扫描 winget 子目录（Gyan、BtbN 等发行版安装在不同子目录下）
        String wingetRoot = localAppData + File.separator + "Microsoft"
                + File.separator + "WinGet" + File.separator + "Packages";
        File wingetDir = new File(wingetRoot);
        if (wingetDir.isDirectory()) {
            File[] children = wingetDir.listFiles(File::isDirectory);
            if (children != null) {
                for (File child : children) {
                    File ffprobeExe = new File(child, "ffprobe.exe");
                    if (ffprobeExe.exists()) {
                        LogUtil.info("[VideoUtil] 自动发现 FFmpeg (winget): " + child.getAbsolutePath());
                        return child.getAbsolutePath();
                    }
                    // 有些发行版把文件放在 bin 子目录
                    File binSubDir = new File(child, "bin");
                    ffprobeExe = new File(binSubDir, "ffprobe.exe");
                    if (ffprobeExe.exists()) {
                        LogUtil.info("[VideoUtil] 自动发现 FFmpeg (winget/bin): "
                                + binSubDir.getAbsolutePath());
                        return binSubDir.getAbsolutePath();
                    }
                }
            }
        }

        LogUtil.warning("[VideoUtil] 未找到 FFmpeg/FFprobe。请运行: winget install ffmpeg");
        return null;
    }

    /**
     * 强制重新检测 FFmpeg 可用性（清除缓存）。
     */
    public static void resetFfmpegCheck() {
        ffmpegAvailable = null;
    }

    /**
     * 检测单个命令是否可执行。
     */
    private static boolean checkCommand(String command) {
        try {
            ProcessBuilder pb = buildProcess(command.split(" "));
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取 FFmpeg 版本字符串。
     *
     * @return 版本字符串，不可用时返回 "不可用"
     */
    public static String getFfmpegVersion() {
        try {
            ProcessBuilder pb = buildProcess(resolveCommand("ffmpeg"), "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                process.waitFor(5, TimeUnit.SECONDS);
                return line != null ? line : "未知版本";
            }
        } catch (Exception e) {
            return "不可用";
        }
    }

    // ==================== ffplay 播放支持 ====================

    /** 缓存的 ffplay 可用性检测结果 */
    private static Boolean ffplayAvailable = null;

    /**
     * 检测 ffplay 是否可用（复用 checkCommand 模式）。
     * 结果缓存，首次调用后不再重复检测。
     *
     * @return true 表示 ffplay 可正常调用
     */
    public static boolean checkFfplayAvailable() {
        if (ffplayAvailable != null) {
            return ffplayAvailable;
        }
        String ffplayCmd = resolveCommand("ffplay");
        ffplayAvailable = checkCommand(ffplayCmd + " -version");
        LogUtil.info("[VideoUtil] FFplay 可用性: " + ffplayAvailable);
        return ffplayAvailable;
    }

    /**
     * 强制重新检测 ffplay 可用性（清除缓存）。
     */
    public static void resetFfplayCheck() {
        ffplayAvailable = null;
    }

    /**
     * 启动 ffplay 播放指定视频文件。
     * 打开原生 SDL 窗口，内置播放/暂停/进度/音量控制。
     * 返回 Process 引用，调用方负责生命周期管理。
     *
     * @param videoFile 要播放的视频文件
     * @return 启动的 Process 对象
     * @throws IOException 如果 ffplay 无法启动
     */
    public static Process playVideo(File videoFile) throws IOException {
        return playVideo(videoFile, -1, -1);
    }

    /**
     * 启动 ffplay 播放视频，可指定窗口尺寸。
     *
     * @param videoFile 视频文件
     * @param width     窗口宽度（-1 表示默认）
     * @param height    窗口高度（-1 表示默认）
     * @return 启动的 Process 对象
     * @throws IOException 如果 ffplay 启动失败
     */
    public static Process playVideo(File videoFile, int width, int height) throws IOException {
        String ffplayCmd = resolveCommand("ffplay");
        String title = "NCHU Compressor - " + videoFile.getName();

        List<String> args = new ArrayList<>();
        args.add(ffplayCmd);
        args.add("-window_title");
        args.add(title);
        args.add("-autoexit");  // 播放结束后自动关闭窗口
        if (width > 0) {
            args.add("-x");
            args.add(String.valueOf(width));
        }
        if (height > 0) {
            args.add("-y");
            args.add(String.valueOf(height));
        }
        args.add(videoFile.getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectErrorStream(true);
        LogUtil.info("[VideoUtil] 启动 ffplay: " + String.join(" ", args));

        Process process = pb.start();

        // 排空 stdout（防止管道缓冲区满导致 ffplay 阻塞 → waitFor 死锁）
        // 与 VideoCompressUtil.executeCompress() 中的 stdout drainer 原理相同
        // JDK 8 无 Redirect.DISCARD，必须用守护线程手动排空
        final Process finalProcess = process;
        Thread drainer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] buf = new byte[4096];
                    java.io.InputStream is = finalProcess.getInputStream();
                    while (is.read(buf) != -1) { /* drain silently */ }
                } catch (IOException ignored) { /* process terminated */ }
            }
        }, "FFplay-Stdout-Drainer");
        drainer.setDaemon(true);
        drainer.start();

        return process;
    }

    // ==================== 格式支持检测 ====================

    /**
     * 判断文件是否为支持的视频格式。
     *
     * @param file 待检测文件
     * @return true 表示扩展名在支持列表中
     */
    public static boolean isSupportedVideo(File file) {
        if (file == null || !file.isFile()) return false;
        String name = file.getName().toLowerCase();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        return SUPPORTED_EXTENSIONS.contains(name.substring(dot + 1));
    }

    /**
     * 获取所有支持的视频扩展名。
     */
    public static Set<String> getSupportedExtensions() {
        return new HashSet<>(SUPPORTED_EXTENSIONS);
    }

    /**
     * 获取文件选择器用的过滤器字符串。
     */
    public static String getFileFilterExtensions() {
        return "mp4, avi, mov, mkv, webm, flv, wmv, m4v, ts, 3gp";
    }

    // ==================== 元数据解析 ====================

    /**
     * 通过 ffprobe 解析视频文件的元数据并填充到 VideoFileInfo。
     *
     * <p>调用示例：{@code ffprobe -v quiet -print_format json -show_format -show_streams input.mp4}</p>
     *
     * @param info 待填充的视频文件信息对象
     * @return true 表示解析成功
     */
    public static boolean parseMetadata(VideoFileInfo info) {
        if (info == null || info.getSourceFile() == null) return false;

        try {
            String json = executeFfprobe(info.getSourceFile());
            if (json == null || json.isEmpty()) {
                info.setErrorMessage("ffprobe 未返回数据");
                return false;
            }
            return parseFfprobeJson(json, info);
        } catch (Exception e) {
            LogUtil.error("[VideoUtil] 元数据解析失败: " + e.getMessage());
            info.setErrorMessage("元数据解析失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 执行 ffprobe 并返回 JSON 字符串。
     */
    private static String executeFfprobe(File videoFile) throws IOException, InterruptedException {
        String ffprobeCmd = resolveCommand("ffprobe");
        ProcessBuilder pb = buildProcess(
                ffprobeCmd, "-v", "quiet",
                "-print_format", "json",
                "-show_format",
                "-show_streams",
                videoFile.getAbsolutePath());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
        }

        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("ffprobe 执行超时（30秒）");
        }

        if (process.exitValue() != 0) {
            throw new IOException("ffprobe 返回非零退出码: " + process.exitValue());
        }

        return output.toString();
    }

    /**
     * 解析 ffprobe JSON 输出并填充 VideoFileInfo。
     *
     * <p>JSON 结构示例：
     * <pre>{@code
     * {
     *   "streams": [
     *     { "codec_type": "video", "width": 1920, "height": 1080,
     *       "r_frame_rate": "30/1", "codec_name": "h264", "bit_rate": "5000000" },
     *     { "codec_type": "audio", "codec_name": "aac" }
     *   ],
     *   "format": { "duration": "120.5", "bit_rate": "5200000" }
     * }
     * }</pre>
     */
    static boolean parseFfprobeJson(String json, VideoFileInfo info) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            // 解析 streams
            JsonArray streams = root.getAsJsonArray("streams");
            if (streams == null || streams.size() == 0) {
                info.setErrorMessage("未找到视频/音频流");
                return false;
            }

            boolean hasVideoStream = false;
            for (JsonElement element : streams) {
                JsonObject stream = element.getAsJsonObject();
                String codecType = getJsonString(stream, "codec_type");

                if ("video".equals(codecType)) {
                    hasVideoStream = true;
                    info.setWidth(getJsonInt(stream, "width"));
                    info.setHeight(getJsonInt(stream, "height"));

                    // 帧率：r_frame_rate 格式如 "30/1" 或 "30000/1001"
                    String fpsStr = getJsonString(stream, "r_frame_rate");
                    info.setFps(parseFraction(fpsStr));

                    info.setVideoCodec(getJsonString(stream, "codec_name"));

                    // 视频流比特率
                    String bitrateStr = getJsonString(stream, "bit_rate");
                    if (!bitrateStr.isEmpty()) {
                        info.setBitrate(Long.parseLong(bitrateStr));
                    }
                } else if ("audio".equals(codecType)) {
                    info.setAudioCodec(getJsonString(stream, "codec_name"));
                }
            }

            if (!hasVideoStream) {
                info.setErrorMessage("文件中未找到视频流");
                return false;
            }

            // 解析 format
            JsonObject format = root.getAsJsonObject("format");
            if (format != null) {
                String durationStr = getJsonString(format, "duration");
                if (!durationStr.isEmpty()) {
                    info.setDurationSeconds(Double.parseDouble(durationStr));
                }

                // 如果视频流没有比特率，使用容器比特率
                if (info.getBitrate() <= 0) {
                    String bitrateStr = getJsonString(format, "bit_rate");
                    if (!bitrateStr.isEmpty()) {
                        info.setBitrate(Long.parseLong(bitrateStr));
                    }
                }
            }

            return true;

        } catch (Exception e) {
            LogUtil.error("[VideoUtil] JSON 解析失败: " + e.getMessage());
            info.setErrorMessage("元数据解析失败: " + e.getMessage());
            return false;
        }
    }

    // ==================== JSON 辅助方法 ====================

    private static String getJsonString(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) return "";
        return element.getAsString();
    }

    private static int getJsonInt(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) return 0;
        return element.getAsInt();
    }

    /**
     * 解析分数形式的帧率字符串（如 "30/1" → 30.0, "30000/1001" → 29.97）。
     */
    static double parseFraction(String fraction) {
        if (fraction == null || fraction.isEmpty()) return 0;
        String[] parts = fraction.split("/");
        if (parts.length == 2) {
            try {
                double numerator = Double.parseDouble(parts[0]);
                double denominator = Double.parseDouble(parts[1]);
                return denominator != 0 ? numerator / denominator : 0;
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        try {
            return Double.parseDouble(fraction);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ==================== 批量元数据加载 ====================

    /**
     * 批量解析多个视频文件的元数据。
     *
     * @param videoFiles 视频文件列表
     * @return 成功解析的文件数量
     */
    public static int parseMetadataBatch(List<VideoFileInfo> videoFiles) {
        int successCount = 0;
        for (VideoFileInfo info : videoFiles) {
            if (parseMetadata(info)) {
                successCount++;
            }
        }
        return successCount;
    }

    // ==================== ProcessBuilder 工厂 ====================

    /**
     * 创建 ProcessBuilder 实例。
     *
     * <p>注意：Windows 上 ffmpeg.exe 需要通过 cmd.exe 或直接调用。</p>
     */
    static ProcessBuilder buildProcess(String... command) {
        return new ProcessBuilder(command).redirectErrorStream(false);
    }
}
