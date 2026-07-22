package com.nchu.imagecompress.model;

/**
 * 全局应用配置实体（可 JSON 序列化）。
 *
 * <p>持久化存储于 {@code ~/.NCHUImageCompressor/config.json}，
 * 包含主题、窗口状态、上次压缩参数、最近使用目录等信息。
 * 应用启动时由 ConfigService 加载，关闭时保存。</p>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-08
 */
public class AppConfig {

    // ==================== 主题设置 ====================

    /** 当前主题 */
    private Theme theme = Theme.BLUE_RHYME;

    // ==================== 窗口状态 ====================

    /** 窗口左上角 X 坐标（-1 表示使用默认位置） */
    private int windowX = -1;

    /** 窗口左上角 Y 坐标 */
    private int windowY = -1;

    /** 窗口宽度 */
    private int windowWidth = 1000;

    /** 窗口高度 */
    private int windowHeight = 680;

    /** 是否最大化 */
    private boolean maximized = false;

    /** 左侧文件列表面板宽度 */
    private int splitPaneLeftWidth = 360;

    /** 预览/参数面板高度分界 */
    private int splitPaneRightDivider = 380;

    // ==================== 上次压缩参数 ====================

    /** 上次使用的质量值 */
    private int lastQuality = 80;

    /** 上次使用的输出格式名称 */
    private String lastOutputFormat = "ORIGINAL";

    /** 上次使用的输出目录 */
    private String lastOutputPath = "";

    /** 上次使用的缩放模式 */
    private String lastScaleMode = "NONE";

    /** 上次使用的缩放百分比 */
    private int lastScalePercent = 100;

    /** 上次使用的命名规则 */
    private String lastNamingRule = "ADD_SUFFIX";

    /** 上次使用的后缀 */
    private String lastSuffix = "_compressed";

    /** 上次使用的自定义文件名 */
    private String lastCustomName = "";

    /** 上次使用的目标大小（KB），0=未启用 */
    private int lastTargetSizeKB = 0;

    /** 是否启用智能推荐 */
    private boolean smartRecommendEnabled = false;

    // ==================== 压缩模式 ====================

    /** 当前压缩模式：IMAGE 或 VIDEO */
    private String compressMode = "IMAGE";

    // ==================== 视频压缩参数记忆 ====================

    /** 上次使用的视频 CRF 值 */
    private int lastVideoCrf = 23;

    /** 上次使用的视频输出格式名称 */
    private String lastVideoFormat = "ORIGINAL";

    /** 上次使用的分辨率模式 */
    private String lastVideoResolution = "ORIGINAL";

    /** 上次使用的帧率模式 */
    private String lastVideoFps = "ORIGINAL";

    /** 上次使用的音频模式 */
    private String lastVideoAudio = "KEEP";

    /** 上次使用的视频输出目录 */
    private String lastVideoOutputPath = "";

    // ==================== 最近目录 ====================

    /** 最近导入文件的目录 */
    private String recentImportDir = "";

    /** 最近视频导入目录 */
    private String recentVideoImportDir = "";

    /** 最近输出目录 */
    private String recentOutputDir = "";

    // ==================== 默认实例 ====================

    public static AppConfig getDefault() {
        return new AppConfig();
    }

    // ==================== Getter / Setter ====================

    public Theme getTheme() { return theme != null ? theme : Theme.BLUE_CLASSIC; }
    public void setTheme(Theme theme) { this.theme = theme; }

    public int getWindowX() { return windowX; }
    public void setWindowX(int windowX) { this.windowX = windowX; }

    public int getWindowY() { return windowY; }
    public void setWindowY(int windowY) { this.windowY = windowY; }

    public int getWindowWidth() { return windowWidth; }
    public void setWindowWidth(int windowWidth) { this.windowWidth = windowWidth; }

    public int getWindowHeight() { return windowHeight; }
    public void setWindowHeight(int windowHeight) { this.windowHeight = windowHeight; }

    public boolean isMaximized() { return maximized; }
    public void setMaximized(boolean maximized) { this.maximized = maximized; }

    public int getSplitPaneLeftWidth() { return splitPaneLeftWidth; }
    public void setSplitPaneLeftWidth(int splitPaneLeftWidth) { this.splitPaneLeftWidth = splitPaneLeftWidth; }

    public int getSplitPaneRightDivider() { return splitPaneRightDivider; }
    public void setSplitPaneRightDivider(int splitPaneRightDivider) { this.splitPaneRightDivider = splitPaneRightDivider; }

    public int getLastQuality() { return lastQuality; }
    public void setLastQuality(int lastQuality) { this.lastQuality = lastQuality; }

    public String getLastOutputFormat() { return lastOutputFormat; }
    public void setLastOutputFormat(String lastOutputFormat) { this.lastOutputFormat = lastOutputFormat; }

    public String getLastOutputPath() { return lastOutputPath; }
    public void setLastOutputPath(String lastOutputPath) { this.lastOutputPath = lastOutputPath; }

    public String getLastScaleMode() { return lastScaleMode; }
    public void setLastScaleMode(String lastScaleMode) { this.lastScaleMode = lastScaleMode; }

    public int getLastScalePercent() { return lastScalePercent; }
    public void setLastScalePercent(int lastScalePercent) { this.lastScalePercent = lastScalePercent; }

    public String getLastNamingRule() { return lastNamingRule; }
    public void setLastNamingRule(String lastNamingRule) { this.lastNamingRule = lastNamingRule; }

    public String getLastSuffix() { return lastSuffix; }
    public void setLastSuffix(String lastSuffix) { this.lastSuffix = lastSuffix; }

    public String getLastCustomName() { return lastCustomName; }
    public void setLastCustomName(String lastCustomName) { this.lastCustomName = lastCustomName; }

    public int getLastTargetSizeKB() { return lastTargetSizeKB; }
    public void setLastTargetSizeKB(int kb) { this.lastTargetSizeKB = Math.max(0, kb); }

    public boolean isSmartRecommendEnabled() { return smartRecommendEnabled; }
    public void setSmartRecommendEnabled(boolean smartRecommendEnabled) { this.smartRecommendEnabled = smartRecommendEnabled; }

    public String getRecentImportDir() { return recentImportDir; }
    public void setRecentImportDir(String recentImportDir) { this.recentImportDir = recentImportDir; }

    public String getRecentVideoImportDir() { return recentVideoImportDir; }
    public void setRecentVideoImportDir(String recentVideoImportDir) { this.recentVideoImportDir = recentVideoImportDir; }

    public String getRecentOutputDir() { return recentOutputDir; }
    public void setRecentOutputDir(String recentOutputDir) { this.recentOutputDir = recentOutputDir; }

    public String getCompressMode() { return compressMode != null ? compressMode : "IMAGE"; }
    public void setCompressMode(String compressMode) { this.compressMode = compressMode; }

    public int getLastVideoCrf() { return lastVideoCrf; }
    public void setLastVideoCrf(int lastVideoCrf) { this.lastVideoCrf = lastVideoCrf; }

    public String getLastVideoFormat() { return lastVideoFormat; }
    public void setLastVideoFormat(String lastVideoFormat) { this.lastVideoFormat = lastVideoFormat; }

    public String getLastVideoResolution() { return lastVideoResolution; }
    public void setLastVideoResolution(String lastVideoResolution) { this.lastVideoResolution = lastVideoResolution; }

    public String getLastVideoFps() { return lastVideoFps; }
    public void setLastVideoFps(String lastVideoFps) { this.lastVideoFps = lastVideoFps; }

    public String getLastVideoAudio() { return lastVideoAudio; }
    public void setLastVideoAudio(String lastVideoAudio) { this.lastVideoAudio = lastVideoAudio; }

    public String getLastVideoOutputPath() { return lastVideoOutputPath; }
    public void setLastVideoOutputPath(String lastVideoOutputPath) { this.lastVideoOutputPath = lastVideoOutputPath; }

    // ==================== 自动定位 ====================

    /** 压缩完成后是否自动在资源管理器中定位输出文件 */
    private boolean autoRevealOutput = true;

    public boolean isAutoRevealOutput() { return autoRevealOutput; }
    public void setAutoRevealOutput(boolean autoRevealOutput) { this.autoRevealOutput = autoRevealOutput; }

    @Override
    public String toString() {
        return "AppConfig{theme=" + theme + ", window=" + windowWidth + "x" + windowHeight
                + ", lastQuality=" + lastQuality + "}";
    }
}
