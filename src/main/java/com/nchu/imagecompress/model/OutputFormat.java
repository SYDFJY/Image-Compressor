package com.nchu.imagecompress.model;

/**
 * 输出图片格式枚举。
 *
 * <p>定义压缩后支持的图片格式及其对应的文件扩展名。
 * 其中 {@link #ORIGINAL} 表示保持原图格式不变。</p>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-08
 */
public enum OutputFormat {

    /** 保持原图格式，不转换 */
    ORIGINAL("保持原格式", ""),

    /** JPEG 格式（有损压缩，文件小） */
    JPEG("JPEG", "jpg"),

    /** PNG 格式（无损压缩，支持透明） */
    PNG("PNG", "png"),

    /** BMP 格式（无压缩，文件大） */
    BMP("BMP", "bmp"),

    /** WebP 格式（有损/无损压缩，文件小，Google 现代格式） */
    WEBP("WebP", "webp");

    /** 显示名称 */
    private final String displayName;

    /** 文件扩展名（不含点） */
    private final String extension;

    OutputFormat(String displayName, String extension) {
        this.displayName = displayName;
        this.extension = extension;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getExtension() {
        return extension;
    }

    /**
     * 根据扩展名（如 "jpg"、"png"）查找对应的输出格式枚举。
     *
     * @param extension 文件扩展名（不区分大小写）
     * @return 匹配的 OutputFormat，未匹配时返回 ORIGINAL
     */
    public static OutputFormat fromExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return ORIGINAL;
        }
        String ext = extension.toLowerCase().replace(".", "");
        for (OutputFormat format : values()) {
            if (format.extension.equalsIgnoreCase(ext)) {
                return format;
            }
        }
        // 特殊处理常见别名
        if ("jpeg".equals(ext)) {
            return JPEG;
        }
        return ORIGINAL;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
