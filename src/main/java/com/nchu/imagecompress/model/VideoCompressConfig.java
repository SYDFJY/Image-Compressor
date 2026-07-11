package com.nchu.imagecompress.model;

/**
 * 视频压缩参数配置实体。
 *
 * <p>封装一次视频压缩操作的全部参数，通过 FFmpeg CLI 执行。
 * 所有字段均提供合理默认值。</p>
 *
 * <h3>关键参数说明</h3>
 * <ul>
 *   <li><b>CRF</b>（Constant Rate Factor）：0-51，默认 23。数值越小质量越高、文件越大。
 *       17-18 视觉无损，23 默认平衡，28+ 小文件</li>
 *   <li><b>分辨率模式</b>：原始 / 480p / 720p / 1080p / 4K</li>
 *   <li><b>帧率</b>：保持原始 / 24fps / 30fps / 60fps</li>
 *   <li><b>音频</b>：保留原始 / 移除音轨</li>
 * </ul>
 *
 * @author NCHU-Student
 * @version 2.0.0
 * @since 2026-07-11
 */
public class VideoCompressConfig {

    // ==================== 分辨率模式枚举 ====================

    /** 输出分辨率模式 */
    public enum ResolutionMode {
        /** 保持原始分辨率 */
        ORIGINAL("原始", -1, -1),
        /** 480p (854×480) */
        R480P("480p", 854, 480),
        /** 720p (1280×720) */
        R720P("720p", 1280, 720),
        /** 1080p (1920×1080) */
        R1080P("1080p", 1920, 1080),
        /** 4K (3840×2160) */
        R4K("4K", 3840, 2160);

        private final String displayName;
        private final int maxWidth;
        private final int maxHeight;

        ResolutionMode(String displayName, int maxWidth, int maxHeight) {
            this.displayName = displayName;
            this.maxWidth = maxWidth;
            this.maxHeight = maxHeight;
        }

        public String getDisplayName() { return displayName; }
        public int getMaxWidth() { return maxWidth; }
        public int getMaxHeight() { return maxHeight; }

        @Override
        public String toString() { return displayName; }
    }

    // ==================== 帧率模式枚举 ====================

    /** 输出帧率模式 */
    public enum FpsMode {
        ORIGINAL("保持原始", -1),
        FPS_24("24 fps", 24),
        FPS_30("30 fps", 30),
        FPS_60("60 fps", 60);

        private final String displayName;
        private final int fps;

        FpsMode(String displayName, int fps) {
            this.displayName = displayName;
            this.fps = fps;
        }

        public String getDisplayName() { return displayName; }
        public int getFps() { return fps; }

        @Override
        public String toString() { return displayName; }
    }

    // ==================== 音频模式枚举 ====================

    /** 音频处理模式 */
    public enum AudioMode {
        KEEP("保留音频"),
        REMOVE("移除音频");

        private final String displayName;

        AudioMode(String displayName) { this.displayName = displayName; }

        public String getDisplayName() { return displayName; }

        @Override
        public String toString() { return displayName; }
    }

    // ==================== 视频输出格式枚举 ====================

    /** 视频输出格式 */
    public enum VideoFormat {
        ORIGINAL("保持原格式", ""),
        MP4("MP4 (H.264)", "mp4"),
        WEBM("WebM (VP9)", "webm"),
        AVI("AVI", "avi"),
        MOV("MOV", "mov"),
        MKV("MKV", "mkv");

        private final String displayName;
        private final String extension;

        VideoFormat(String displayName, String extension) {
            this.displayName = displayName;
            this.extension = extension;
        }

        public String getDisplayName() { return displayName; }
        public String getExtension() { return extension; }

        /**
         * 根据扩展名查找对应的视频格式枚举。
         */
        public static VideoFormat fromExtension(String extension) {
            if (extension == null || extension.isEmpty()) return ORIGINAL;
            String ext = extension.toLowerCase().replace(".", "");
            for (VideoFormat format : values()) {
                if (format.extension.equalsIgnoreCase(ext)) return format;
            }
            return ORIGINAL;
        }

        @Override
        public String toString() { return displayName; }
    }

    // ==================== 预设枚举 ====================

    /** 快捷预设 */
    public enum Preset {
        /** 微信小视频：小文件、低分辨率 */
        WECHAT("微信小视频", 30, ResolutionMode.R480P, FpsMode.ORIGINAL, AudioMode.KEEP),
        /** B站投稿：高质量、1080p */
        BILIBILI("B站投稿", 20, ResolutionMode.R1080P, FpsMode.FPS_30, AudioMode.KEEP),
        /** 存档原画：无损画质 */
        ARCHIVE("存档原画", 15, ResolutionMode.ORIGINAL, FpsMode.ORIGINAL, AudioMode.KEEP);

        private final String displayName;
        private final int crf;
        private final ResolutionMode resolution;
        private final FpsMode fps;
        private final AudioMode audio;

        Preset(String displayName, int crf, ResolutionMode resolution, FpsMode fps, AudioMode audio) {
            this.displayName = displayName;
            this.crf = crf;
            this.resolution = resolution;
            this.fps = fps;
            this.audio = audio;
        }

        public String getDisplayName() { return displayName; }
        public int getCrf() { return crf; }
        public ResolutionMode getResolution() { return resolution; }
        public FpsMode getFps() { return fps; }
        public AudioMode getAudio() { return audio; }

        @Override
        public String toString() { return displayName; }
    }

    // ==================== 参数字段 ====================

    /** CRF 质量值 (0-51)，默认 23 */
    private int crf = 23;

    /** 分辨率模式，默认保持原始 */
    private ResolutionMode resolutionMode = ResolutionMode.ORIGINAL;

    /** 帧率模式，默认保持原始 */
    private FpsMode fpsMode = FpsMode.ORIGINAL;

    /** 音频模式，默认保留 */
    private AudioMode audioMode = AudioMode.KEEP;

    /** 输出视频格式，默认保持原格式 */
    private VideoFormat outputFormat = VideoFormat.ORIGINAL;

    /** 视频编码器（为空时自动选择：MP4→libx264, WebM→libvpx-vp9） */
    private String videoCodec = "";

    /** 音频编码器（为空时自动选择） */
    private String audioCodec = "";

    /** 输出目录路径 */
    private String outputPath = "";

    /** 文件命名后缀 */
    private String suffix = "_compressed";

    /** 是否允许覆盖同名文件（默认 true，方便重复压缩调试） */
    private boolean overwrite = true;

    /** 额外 FFmpeg 参数（高级用户） */
    private String extraArgs = "";

    // ==================== 默认实例 ====================

    /** 获取默认视频压缩配置 */
    public static VideoCompressConfig getDefault() {
        return new VideoCompressConfig();
    }

    // ==================== 便捷方法 ====================

    /**
     * 应用预设参数。
     */
    public void applyPreset(Preset preset) {
        this.crf = preset.getCrf();
        this.resolutionMode = preset.getResolution();
        this.fpsMode = preset.getFps();
        this.audioMode = preset.getAudio();
    }

    /**
     * 判断是否为无效压缩。
     */
    public boolean isEffectivelyNoOp() {
        return crf <= 0
                && resolutionMode == ResolutionMode.ORIGINAL
                && fpsMode == FpsMode.ORIGINAL
                && audioMode == AudioMode.KEEP
                && outputFormat == VideoFormat.ORIGINAL;
    }

    // ==================== Getter / Setter ====================

    public int getCrf() { return crf; }
    public void setCrf(int crf) { this.crf = Math.max(0, Math.min(51, crf)); }

    public ResolutionMode getResolutionMode() { return resolutionMode; }
    public void setResolutionMode(ResolutionMode resolutionMode) { this.resolutionMode = resolutionMode; }

    public FpsMode getFpsMode() { return fpsMode; }
    public void setFpsMode(FpsMode fpsMode) { this.fpsMode = fpsMode; }

    public AudioMode getAudioMode() { return audioMode; }
    public void setAudioMode(AudioMode audioMode) { this.audioMode = audioMode; }

    public VideoFormat getOutputFormat() { return outputFormat; }
    public void setOutputFormat(VideoFormat outputFormat) { this.outputFormat = outputFormat; }

    public String getVideoCodec() { return videoCodec; }
    public void setVideoCodec(String videoCodec) { this.videoCodec = videoCodec; }

    public String getAudioCodec() { return audioCodec; }
    public void setAudioCodec(String audioCodec) { this.audioCodec = audioCodec; }

    public String getOutputPath() { return outputPath; }
    public void setOutputPath(String outputPath) { this.outputPath = outputPath; }

    public String getSuffix() { return suffix; }
    public void setSuffix(String suffix) { this.suffix = suffix; }

    public boolean isOverwrite() { return overwrite; }
    public void setOverwrite(boolean overwrite) { this.overwrite = overwrite; }

    public String getExtraArgs() { return extraArgs; }
    public void setExtraArgs(String extraArgs) { this.extraArgs = extraArgs; }

    @Override
    public String toString() {
        return "VideoCompressConfig{crf=" + crf
                + ", resolution=" + resolutionMode
                + ", fps=" + fpsMode
                + ", audio=" + audioMode
                + ", format=" + outputFormat + "}";
    }
}
