package com.nchu.imagecompress.model;

/**
 * 压缩参数配置实体。
 *
 * <p>封装一次压缩操作的全部参数，贯穿 Service → Controller → View 层传递。
 * 所有字段均提供合理默认值，避免空指针。</p>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-08
 */
public class CompressConfig {

    /** 缩放模式枚举 */
    public enum ScaleMode {
        /** 不缩放，保持原始尺寸 */
        NONE("不缩放"),
        /** 按百分比缩放 */
        BY_PERCENT("按百分比"),
        /** 按最大宽度/高度限制等比缩放 */
        BY_MAX_SIZE("按最大尺寸");

        private final String displayName;
        ScaleMode(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
        @Override
        public String toString() { return displayName; }
    }

    /** 文件命名规则枚举 */
    public enum NamingRule {
        /** 原文件名 + 后缀（默认） */
        ADD_SUFFIX("添加后缀"),
        /** 原文件名 + 前缀 */
        ADD_PREFIX("添加前缀"),
        /** 保持原文件名（仅当输出与输入不同目录时安全） */
        KEEP_ORIGINAL("保持原名"),
        /** 用户自定义文件名 */
        CUSTOM("自定义文件名");

        private final String displayName;
        NamingRule(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
        @Override
        public String toString() { return displayName; }
    }

    // ==================== 压缩质量 ====================

    /** 压缩质量 (0-100)，默认 80。数值越大质量越高、文件越大 */
    private int quality = 80;

    /** 目标输出文件大小（KB），0 表示不使用目标大小模式（v2 — 二分逼近压缩） */
    private int targetSizeKB = 0;

    // ==================== 缩放设置 ====================

    /** 缩放模式，默认不缩放 */
    private ScaleMode scaleMode = ScaleMode.NONE;

    /** 缩放百分比 (1-100)，仅在 BY_PERCENT 模式下生效 */
    private int scalePercent = 100;

    /** 最大宽度（像素），仅在 BY_MAX_SIZE 模式下生效 */
    private int maxWidth = 1920;

    /** 最大高度（像素），仅在 BY_MAX_SIZE 模式下生效 */
    private int maxHeight = 1080;

    // ==================== 格式设置 ====================

    /** 输出格式，默认保持原格式 */
    private OutputFormat outputFormat = OutputFormat.ORIGINAL;

    // ==================== 输出设置 ====================

    /** 输出目录路径 */
    private String outputPath = "";

    /** 文件命名规则，默认添加后缀 "_compressed" */
    private NamingRule namingRule = NamingRule.ADD_SUFFIX;

    /** 命名后缀（NamingRule 为 ADD_SUFFIX 时使用） */
    private String suffix = "_compressed";

    /** 命名前缀（NamingRule 为 ADD_PREFIX 时使用） */
    private String prefix = "compressed_";

    /** 自定义文件名（NamingRule 为 CUSTOM 时使用，不含扩展名） */
    private String customName = "";

    /** 是否允许覆盖同名文件 */
    private boolean overwrite = false;

    /** 是否保留 EXIF/IPTC 元数据（默认移除以减小体积） */
    private boolean preserveMetadata = false;

    // ==================== GIF 专属 ====================

    /** GIF 最大颜色数 (2-256)，默认 256（不限制），0 表示不限制 */
    private int gifMaxColors = 256;

    // ==================== 智能推荐 ====================

    /** 是否启用智能质量推荐 */
    private boolean smartRecommend = false;

    /** 推荐的图片类型（照片/截图/图标） */
    private String imageTypeHint = "";

    // ==================== 默认实例 ====================

    /** 获取默认压缩配置（质量 80，不缩放，保持格式） */
    public static CompressConfig getDefault() {
        return new CompressConfig();
    }

    // ==================== 便捷方法 ====================

    /**
     * 判断是否需要进行压缩操作。
     * 质量 = 100 且不缩放且不转换格式时，压缩无意义。
     */
    public boolean isEffectivelyNoOp() {
        return quality == 100
                && (scaleMode == ScaleMode.NONE || scaleMode == ScaleMode.BY_PERCENT && scalePercent == 100)
                && outputFormat == OutputFormat.ORIGINAL;
    }

    // ==================== Getter / Setter ====================

    public int getQuality() { return quality; }
    public void setQuality(int quality) {
        this.quality = Math.max(0, Math.min(100, quality));
    }

    /** 目标大小（KB），0 = 禁用 */
    public int getTargetSizeKB() { return targetSizeKB; }
    public void setTargetSizeKB(int kb) { this.targetSizeKB = Math.max(0, kb); }

    public ScaleMode getScaleMode() { return scaleMode; }
    public void setScaleMode(ScaleMode scaleMode) { this.scaleMode = scaleMode; }

    public int getScalePercent() { return scalePercent; }
    public void setScalePercent(int scalePercent) {
        this.scalePercent = Math.max(1, Math.min(100, scalePercent));
    }

    public int getMaxWidth() { return maxWidth; }
    public void setMaxWidth(int maxWidth) { this.maxWidth = Math.max(1, maxWidth); }

    public int getMaxHeight() { return maxHeight; }
    public void setMaxHeight(int maxHeight) { this.maxHeight = Math.max(1, maxHeight); }

    public OutputFormat getOutputFormat() { return outputFormat; }
    public void setOutputFormat(OutputFormat outputFormat) { this.outputFormat = outputFormat; }

    public String getOutputPath() { return outputPath; }
    public void setOutputPath(String outputPath) { this.outputPath = outputPath; }

    public NamingRule getNamingRule() { return namingRule; }
    public void setNamingRule(NamingRule namingRule) { this.namingRule = namingRule; }

    public String getSuffix() { return suffix; }
    public void setSuffix(String suffix) { this.suffix = suffix; }

    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }

    public String getCustomName() { return customName; }
    public void setCustomName(String customName) { this.customName = customName; }

    public boolean isOverwrite() { return overwrite; }
    public void setOverwrite(boolean overwrite) { this.overwrite = overwrite; }

    public boolean isPreserveMetadata() { return preserveMetadata; }
    public void setPreserveMetadata(boolean preserveMetadata) { this.preserveMetadata = preserveMetadata; }

    /** GIF 最大颜色数 (2-256)，0 表示不限制 */
    public int getGifMaxColors() { return gifMaxColors; }
    public void setGifMaxColors(int n) { this.gifMaxColors = Math.max(0, Math.min(256, n)); }

    public boolean isSmartRecommend() { return smartRecommend; }
    public void setSmartRecommend(boolean smartRecommend) { this.smartRecommend = smartRecommend; }

    public String getImageTypeHint() { return imageTypeHint; }
    public void setImageTypeHint(String imageTypeHint) { this.imageTypeHint = imageTypeHint; }

    @Override
    public String toString() {
        return "CompressConfig{quality=" + quality
                + ", scale=" + scaleMode + "(" + scalePercent + "%)"
                + ", format=" + outputFormat
                + ", output=" + outputPath + "}";
    }
}
