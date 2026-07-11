package com.nchu.imagecompress.model;

import java.io.File;

/**
 * 待压缩视频文件信息实体。
 *
 * <p>封装单个视频文件的元数据（通过 ffprobe 解析获取），
 * 包括时长、分辨率、帧率、比特率、编解码器等。</p>
 *
 * @author NCHU-Student
 * @version 2.0.0
 * @since 2026-07-11
 */
public class VideoFileInfo implements FileInfo {

    // ==================== 基本信息 ====================

    /** 原始文件对象 */
    private File sourceFile;

    /** 文件名（含扩展名） */
    private String fileName;

    /** 原始文件大小（字节） */
    private long originalSize;

    /** 文件格式/扩展名（如 "mp4", "avi"） */
    private String format;

    // ==================== 视频元数据 ====================

    /** 视频时长（秒） */
    private double durationSeconds;

    /** 视频宽度（像素） */
    private int width;

    /** 视频高度（像素） */
    private int height;

    /** 帧率（fps） */
    private double fps;

    /** 视频比特率（bps） */
    private long bitrate;

    /** 视频编解码器名称（如 "h264", "hevc"） */
    private String videoCodec;

    /** 音频编解码器名称（如 "aac", "mp3"，无音频轨道时为 null） */
    private String audioCodec;

    // ==================== 状态信息 ====================

    /** 当前处理状态 */
    private Status status;

    /** 失败时的错误信息 */
    private String errorMessage;

    /** 列表中的索引位置 */
    private int index;

    // ==================== 构造函数 ====================

    /** 无参构造（用于 JSON 反序列化等场景） */
    public VideoFileInfo() {
        this.status = Status.PENDING;
    }

    /**
     * 根据源文件创建视频文件信息对象。
     *
     * @param sourceFile 视频文件
     */
    public VideoFileInfo(File sourceFile) {
        this();
        this.sourceFile = sourceFile;
        this.fileName = sourceFile.getName();
        this.originalSize = sourceFile.length();
        this.format = extractExtension(sourceFile.getName());
    }

    // ==================== 工具方法 ====================

    /**
     * 从文件名中提取扩展名（不含点号）。
     */
    private static String extractExtension(String name) {
        if (name == null || name.isEmpty()) return "";
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == name.length() - 1) return "";
        return name.substring(dotIndex + 1).toLowerCase();
    }

    @Override
    public String getFormattedSize() {
        return formatFileSize(originalSize);
    }

    @Override
    public String getDurationString() {
        if (durationSeconds <= 0) return "";
        long minutes = (long) (durationSeconds / 60);
        long seconds = (long) (durationSeconds % 60);
        return String.format("%d:%02d", minutes, seconds);
    }

    /**
     * 获取完整时长字符串（含小时）。
     */
    public String getFullDurationString() {
        if (durationSeconds <= 0) return "未知";
        long hours = (long) (durationSeconds / 3600);
        long minutes = (long) ((durationSeconds % 3600) / 60);
        long seconds = (long) (durationSeconds % 60);
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%d:%02d", minutes, seconds);
    }

    /**
     * 将字节数格式化为可读字符串。
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 0) return "未知";
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        return String.format("%.2f GB", mb / 1024.0);
    }

    // ==================== FileInfo 接口实现 ====================

    @Override
    public FileType getFileType() {
        return FileType.VIDEO;
    }

    @Override
    public Status getFileInfoStatus() {
        return status;
    }

    @Override
    public void setFileInfoStatus(Status status) {
        this.status = status;
    }

    // ==================== Getter / Setter ====================

    @Override
    public File getSourceFile() { return sourceFile; }
    public void setSourceFile(File sourceFile) { this.sourceFile = sourceFile; }

    @Override
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    @Override
    public long getOriginalSize() { return originalSize; }
    public void setOriginalSize(long originalSize) { this.originalSize = originalSize; }

    @Override
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public double getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(double durationSeconds) { this.durationSeconds = durationSeconds; }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }

    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    public double getFps() { return fps; }
    public void setFps(double fps) { this.fps = fps; }

    public long getBitrate() { return bitrate; }
    public void setBitrate(long bitrate) { this.bitrate = bitrate; }

    public String getVideoCodec() { return videoCodec; }
    public void setVideoCodec(String videoCodec) { this.videoCodec = videoCodec; }

    public String getAudioCodec() { return audioCodec; }
    public void setAudioCodec(String audioCodec) { this.audioCodec = audioCodec; }

    @Override
    public String getErrorMessage() { return errorMessage; }

    @Override
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    @Override
    public int getIndex() { return index; }

    @Override
    public void setIndex(int index) { this.index = index; }

    @Override
    public String toString() {
        return "VideoFileInfo{file=" + fileName + ", size=" + getFormattedSize()
                + ", " + width + "x" + height + ", " + getFullDurationString()
                + ", status=" + status + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VideoFileInfo that = (VideoFileInfo) o;
        if (sourceFile == null || that.sourceFile == null) return false;
        return sourceFile.getAbsolutePath().equals(that.sourceFile.getAbsolutePath());
    }

    @Override
    public int hashCode() {
        return sourceFile != null ? sourceFile.getAbsolutePath().hashCode() : 0;
    }
}
