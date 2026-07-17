package com.nchu.imagecompress.model;

import java.io.File;

/**
 * 待压缩图片文件信息实体。
 *
 * <p>封装单个图片文件的元数据，包括原始大小、尺寸、格式和当前处理状态。
 * Model 层纯粹的数据对象，不包含任何业务逻辑或界面代码。</p>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-08
 */
public class ImageFileInfo implements FileInfo {

    // Status enum 移至 FileInfo 接口统一管理，保留别名以兼容旧代码
    /** @deprecated 请使用 {@link FileInfo.Status} */
    @Deprecated
    public enum Status {
        PENDING, PROCESSING, SUCCESS, FAILED
    }

    // ==================== 基本信息 ====================

    /** 原始文件对象 */
    private File sourceFile;

    /** 文件名（含扩展名） */
    private String fileName;

    /** 原始文件大小（字节） */
    private long originalSize;

    /** 图片宽度（像素） */
    private int width;

    /** 图片高度（像素） */
    private int height;

    /** 图片格式/扩展名（如 "jpg", "png"） */
    private String format;

    // ==================== 状态信息 ====================

    /** 当前处理状态 */
    private Status status;

    /** 失败时的错误信息 */
    private String errorMessage;

    /** 列表中的索引位置 */
    private int index;

    /** EXIF 元数据（懒加载，初始为 null） */
    private com.nchu.imagecompress.util.ImageExifUtil.ExifData exifData;

    // ==================== 构造函数 ====================

    /** 无参构造（用于 JSON 反序列化等场景） */
    public ImageFileInfo() {
        this.status = Status.PENDING;
    }

    /**
     * 根据源文件创建文件信息对象。
     *
     * @param sourceFile 图片文件
     */
    public ImageFileInfo(File sourceFile) {
        this();
        this.sourceFile = sourceFile;
        this.fileName = sourceFile.getName();
        this.originalSize = sourceFile.length();
        this.format = extractExtension(sourceFile.getName());
    }

    // ==================== 工具方法 ====================

    /**
     * 从文件名中提取扩展名（不含点号）。
     *
     * @param name 文件名
     * @return 小写扩展名，无扩展名时返回空字符串
     */
    private static String extractExtension(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == name.length() - 1) {
            return "";
        }
        return name.substring(dotIndex + 1).toLowerCase();
    }

    /**
     * 获取格式化的文件大小字符串（如 "1.5 MB"）。
     *
     * @return 可读的文件大小
     */
    public String getFormattedSize() {
        return formatFileSize(originalSize);
    }

    /**
     * 将字节数格式化为可读字符串。
     *
     * @param bytes 字节数
     * @return 格式化字符串
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 0) {
            return "未知";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.1f MB", mb);
        }
        return String.format("%.2f GB", mb / 1024.0);
    }

    // ==================== Getter / Setter ====================

    public File getSourceFile() { return sourceFile; }
    public void setSourceFile(File sourceFile) { this.sourceFile = sourceFile; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public long getOriginalSize() { return originalSize; }
    public void setOriginalSize(long originalSize) { this.originalSize = originalSize; }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }

    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }

    public com.nchu.imagecompress.util.ImageExifUtil.ExifData getExifData() { return exifData; }
    public void setExifData(com.nchu.imagecompress.util.ImageExifUtil.ExifData exifData) { this.exifData = exifData; }
    public boolean hasExifData() { return exifData != null && exifData.hasAnyData(); }

    @Override
    public String toString() {
        return "ImageFileInfo{file=" + fileName + ", size=" + getFormattedSize()
                + ", " + width + "x" + height + ", status=" + status + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ImageFileInfo that = (ImageFileInfo) o;
        if (sourceFile == null || that.sourceFile == null) return false;
        return sourceFile.getAbsolutePath().equals(that.sourceFile.getAbsolutePath());
    }

    @Override
    public int hashCode() {
        return sourceFile != null ? sourceFile.getAbsolutePath().hashCode() : 0;
    }

    // ==================== FileInfo 接口实现 ====================

    @Override
    public FileInfo.FileType getFileType() {
        return FileInfo.FileType.IMAGE;
    }

    @Override
    public FileInfo.Status getFileInfoStatus() {
        switch (status) {
            case PROCESSING: return FileInfo.Status.PROCESSING;
            case SUCCESS:    return FileInfo.Status.SUCCESS;
            case FAILED:     return FileInfo.Status.FAILED;
            default:         return FileInfo.Status.PENDING;
        }
    }

    @Override
    public void setFileInfoStatus(FileInfo.Status s) {
        switch (s) {
            case PROCESSING: this.status = Status.PROCESSING; break;
            case SUCCESS:    this.status = Status.SUCCESS; break;
            case FAILED:     this.status = Status.FAILED; break;
            default:         this.status = Status.PENDING; break;
        }
    }

    @Override
    public String getDurationString() {
        return ""; // 图片无时长
    }
}
