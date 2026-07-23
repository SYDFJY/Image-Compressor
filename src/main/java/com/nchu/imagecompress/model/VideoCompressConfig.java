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

    // ==================== 编码模式枚举 ====================

    /** 码率控制模式 */
    public enum RateControlMode {
        /** 画质优先：使用 CRF 恒定质量编码 */
        CRF("画质优先"),
        /** 大小优先：使用 bitrate 模式逼近目标文件大小 */
        TARGET_SIZE("限制大小");

        private final String displayName;
        RateControlMode(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
        @Override
        public String toString() { return displayName; }
    }

    // ==================== 参数字段 ====================

    /** 码率控制模式，默认画质优先 (CRF) */
    private RateControlMode rateControlMode = RateControlMode.CRF;

    /** 目标文件大小 (MB)，仅在 TARGET_SIZE 模式下有效 */
    private int targetSizeMB = 0;

    /** 计算出的目标视频码率 (kbps)，仅在 TARGET_SIZE 模式下由 VideoCompressService 设置 */
    private int targetBitrate = 0;

    /**
     * FFmpeg 编码速度预设（影响同码率下的画质与编码耗时）。
     *
     * <p>可选值：ultrafast, superfast, veryfast, faster, fast,
     * medium（默认）, slow, slower, veryslow。</p>
     *
     * @since 2.6.0
     */
    private String encodePreset = "medium";

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

    /** 自定义输出文件名（不含扩展名，为空时使用 suffix 自动生成） */
    private String customName = "";

    /** 是否允许覆盖同名文件（默认 false，避免意外覆盖） */
    private boolean overwrite = false;

    /** v2: 视频裁剪起始时间（秒），0 = 从头开始 */
    private double startTimeSeconds = 0;

    /** v2: 视频裁剪时长（秒），0 = 到结尾 */
    private double durationSeconds = 0;

    /** 额外 FFmpeg 参数（高级用户） */
    private String extraArgs = "";

    /** v2.3: 是否在压缩后提取封面快照 */
    private boolean extractCover = false;

    /** v2.3: 封面截取时间点（秒），默认第 5 秒 */
    private double coverSeekSeconds = 5.0;

    /** v2.3: 封面输出格式（"jpg" 或 "png"） */
    private String coverFormat = "jpg";

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
     * 根据当前压缩参数生成描述性文件名后缀。
     *
     * <p>规则与 {@link VariantPreset#buildSuffix()} 一致，确保不同参数产生不同文件名。</p>
     * <p>示例：{@code _original_origfps_crf23}、{@code _720p_30fps_crf28_noaudio_mp4}</p>
     *
     * @return 后缀字符串（不含扩展名）
     */
    public String buildDynamicSuffix() {
        StringBuilder sb = new StringBuilder();
        if (resolutionMode != ResolutionMode.ORIGINAL)
            sb.append("_").append(resolutionMode.getDisplayName());
        else sb.append("_original");
        if (fpsMode != FpsMode.ORIGINAL)
            sb.append("_").append(fpsMode.getFps()).append("fps");
        else sb.append("_origfps");
        sb.append("_crf").append(crf);
        if (audioMode == AudioMode.REMOVE) sb.append("_noaudio");
        if (outputFormat != VideoFormat.ORIGINAL && !outputFormat.getExtension().isEmpty())
            sb.append("_").append(outputFormat.getExtension());
        return sb.toString();
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

    public RateControlMode getRateControlMode() { return rateControlMode; }
    public void setRateControlMode(RateControlMode mode) { this.rateControlMode = mode; }

    public int getTargetSizeMB() { return targetSizeMB; }
    public void setTargetSizeMB(int targetSizeMB) { this.targetSizeMB = Math.max(0, targetSizeMB); }

    public int getTargetBitrate() { return targetBitrate; }
    public void setTargetBitrate(int targetBitrate) { this.targetBitrate = targetBitrate; }

    public String getEncodePreset() { return encodePreset; }
    public void setEncodePreset(String encodePreset) { this.encodePreset = encodePreset != null ? encodePreset : "medium"; }

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

    public String getCustomName() { return customName; }
    public void setCustomName(String customName) { this.customName = customName; }

    public boolean isOverwrite() { return overwrite; }

    public double getStartTimeSeconds() { return startTimeSeconds; }
    public void setStartTimeSeconds(double s) { this.startTimeSeconds = Math.max(0, s); }
    public double getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(double s) { this.durationSeconds = Math.max(0, s); }
    public void setOverwrite(boolean overwrite) { this.overwrite = overwrite; }

    public String getExtraArgs() { return extraArgs; }
    public void setExtraArgs(String extraArgs) { this.extraArgs = extraArgs; }

    public boolean isExtractCover() { return extractCover; }
    public void setExtractCover(boolean extractCover) { this.extractCover = extractCover; }

    public double getCoverSeekSeconds() { return coverSeekSeconds; }
    public void setCoverSeekSeconds(double coverSeekSeconds) { this.coverSeekSeconds = coverSeekSeconds; }

    public String getCoverFormat() { return coverFormat; }
    public void setCoverFormat(String coverFormat) { this.coverFormat = coverFormat; }

    @Override
    public String toString() {
        return "VideoCompressConfig{crf=" + crf
                + ", resolution=" + resolutionMode
                + ", fps=" + fpsMode
                + ", audio=" + audioMode
                + ", format=" + outputFormat + "}";
    }

    // ==================== 多变体批量导出 ====================

    /**
     * 批量导出变体预设 — 封装单个输出变体的差异化设置。
     *
     * <p>每个变体覆盖 resolution / fps / crf / audio / format 五个维度。</p>
     *
     * @since 2026-07-13
     */
    public static class VariantPreset {

        private ResolutionMode resolutionMode = ResolutionMode.ORIGINAL;
        private FpsMode fpsMode = FpsMode.ORIGINAL;
        private int crf = 23;
        private AudioMode audioMode = AudioMode.KEEP;
        private VideoFormat outputFormat = VideoFormat.ORIGINAL;

        /** 自定义输出文件名（不含扩展名），为空时继承 base */
        private String customName = "";

        /** 裁剪起始时间（秒），-1=继承 base */
        private double startTimeSeconds = -1;

        /** 裁剪时长（秒），-1=继承 base。注意：UI 输入"结束时间"，buildPreset 内部转为时长 */
        private double durationSeconds = -1;

        /** 目标输出文件大小（MB），-1=继承 base，>0=码率模式 */
        private double targetSizeMB = -1;

        /** 编码速度预设，null=继承 base */
        private String encodePreset = null;

        public VariantPreset() {}

        public VariantPreset(ResolutionMode resolutionMode, FpsMode fpsMode, int crf,
                            AudioMode audioMode, VideoFormat outputFormat) {
            this.resolutionMode = resolutionMode;
            this.fpsMode = fpsMode;
            this.crf = crf;
            this.audioMode = audioMode;
            this.outputFormat = outputFormat;
        }

        // ==================== Getter / Setter ====================

        public ResolutionMode getResolutionMode() { return resolutionMode; }
        public void setResolutionMode(ResolutionMode resolutionMode) { this.resolutionMode = resolutionMode; }

        public FpsMode getFpsMode() { return fpsMode; }
        public void setFpsMode(FpsMode fpsMode) { this.fpsMode = fpsMode; }

        public int getCrf() { return crf; }
        public void setCrf(int crf) { this.crf = Math.max(0, Math.min(51, crf)); }

        public AudioMode getAudioMode() { return audioMode; }
        public void setAudioMode(AudioMode audioMode) { this.audioMode = audioMode; }

        public VideoFormat getOutputFormat() { return outputFormat; }
        public void setOutputFormat(VideoFormat outputFormat) { this.outputFormat = outputFormat; }

        public String getCustomName() { return customName; }
        public void setCustomName(String customName) { this.customName = customName != null ? customName : ""; }

        public double getStartTimeSeconds() { return startTimeSeconds; }
        public void setStartTimeSeconds(double s) { this.startTimeSeconds = s; }

        public double getDurationSeconds() { return durationSeconds; }
        public void setDurationSeconds(double s) { this.durationSeconds = s; }

        public double getTargetSizeMB() { return targetSizeMB; }
        public void setTargetSizeMB(double s) { this.targetSizeMB = s; }

        public String getEncodePreset() { return encodePreset; }
        public void setEncodePreset(String p) { this.encodePreset = p; }

        // ==================== 工具方法 ====================

        /**
         * 生成描述性文件后缀。
         *
         * <p>示例：{@code _480p_24fps_crf30_noaudio_mp4}、
         * {@code _original_origfps_crf23}.mp4</p>
         *
         * @return 后缀字符串（不含扩展名）
         */
        public String buildSuffix() {
            StringBuilder sb = new StringBuilder();
            // 分辨率
            if (resolutionMode != ResolutionMode.ORIGINAL) {
                sb.append("_").append(resolutionMode.getDisplayName());
            } else {
                sb.append("_original");
            }
            // 帧率
            if (fpsMode != FpsMode.ORIGINAL) {
                sb.append("_").append(fpsMode.getFps()).append("fps");
            } else {
                sb.append("_origfps");
            }
            // 质量
            sb.append("_crf").append(crf);
            // 音频（仅在移除时标注）
            if (audioMode == AudioMode.REMOVE) {
                sb.append("_noaudio");
            }
            // 格式（仅在非原始时标注）
            if (outputFormat != VideoFormat.ORIGINAL && !outputFormat.getExtension().isEmpty()) {
                sb.append("_").append(outputFormat.getExtension());
            }
            return sb.toString();
        }

        /**
         * 以全局配置为基础，用本变体覆盖全部维度。
         *
         * @param base 全局基础配置（仅输出路径从 base 取）
         * @return 合并后的配置，suffix 自动设置为变体描述
         */
        public VideoCompressConfig mergeWith(VideoCompressConfig base) {
            VideoCompressConfig merged = new VideoCompressConfig();
            merged.setCrf(this.crf);
            merged.setResolutionMode(this.resolutionMode);
            merged.setFpsMode(this.fpsMode);
            merged.setAudioMode(this.audioMode);
            merged.setOutputFormat(this.outputFormat);
            merged.setOutputPath(base.getOutputPath());
            merged.setSuffix(buildSuffix());
            merged.setOverwrite(base.isOverwrite());

            // 裁剪：变体值优先（≥0 表示用户显式设置），否则继承 base
            merged.setStartTimeSeconds(this.startTimeSeconds >= 0
                    ? this.startTimeSeconds : base.getStartTimeSeconds());
            merged.setDurationSeconds(this.durationSeconds >= 0
                    ? this.durationSeconds : base.getDurationSeconds());

            // 自定义文件名：变体值优先，否则继承 base
            merged.setCustomName(this.customName != null && !this.customName.trim().isEmpty()
                    ? this.customName.trim() : base.getCustomName());

            // 目标文件大小：变体值优先（≥0 表示用户显式设置），否则继承 base
            merged.setTargetSizeMB(this.targetSizeMB >= 0
                    ? (int) this.targetSizeMB : base.getTargetSizeMB());

            // 编码预设：变体值优先，否则继承 base
            merged.setEncodePreset(this.encodePreset != null && !this.encodePreset.isEmpty()
                    ? this.encodePreset : base.getEncodePreset());

            return merged;
        }

        @Override
        public String toString() {
            return "VariantPreset{" + buildSuffix().replaceFirst("^_", "") + "}";
        }
    }
}
