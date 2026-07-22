package com.nchu.imagecompress.model;

/**
 * 逐文件压缩参数覆盖（v2.5）。
 *
 * <p>所有字段为可空包装类型 — null 表示"使用全局设置"。
 * 当所有字段均为 null 时，完全使用全局 CompressConfig。</p>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-22
 */
public class PerFileCompressConfig {

    /** 覆盖质量（null = 使用全局），范围 0-100 */
    private Integer quality;

    /** 覆盖输出格式（null = 使用全局） */
    private OutputFormat outputFormat;

    /**
     * 检查是否有任何覆盖项已设置。
     */
    public boolean hasAnyOverride() {
        return quality != null || outputFormat != null;
    }

    /**
     * 清除所有覆盖项，恢复使用全局设置。
     */
    public void clear() {
        quality = null;
        outputFormat = null;
    }

    // ==================== Getters / Setters ====================

    public Integer getQuality() { return quality; }
    public void setQuality(Integer quality) { this.quality = quality; }

    public OutputFormat getOutputFormat() { return outputFormat; }
    public void setOutputFormat(OutputFormat outputFormat) { this.outputFormat = outputFormat; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("PerFileConfig{");
        if (quality != null) sb.append("quality=").append(quality).append(", ");
        if (outputFormat != null) sb.append("format=").append(outputFormat.getExtension()).append(", ");
        if (sb.length() > 16) sb.setLength(sb.length() - 2);
        sb.append("}");
        return sb.toString();
    }
}
