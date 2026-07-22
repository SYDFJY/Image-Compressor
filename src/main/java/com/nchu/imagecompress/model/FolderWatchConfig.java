package com.nchu.imagecompress.model;

/**
 * 文件夹监控配置 — 懒人自动压缩模式参数。
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-22
 */
public class FolderWatchConfig {

    /** 监控源文件夹路径 */
    private String watchFolderPath = "";

    /** 压缩输出文件夹路径 */
    private String outputFolderPath = "";

    /** 图片压缩质量（1-100） */
    private int imageQuality = 80;

    /** 视频 CRF 值（0-51） */
    private int videoCrf = 23;

    /** 输出格式（"ORIGINAL" / "JPEG" / "PNG" 等） */
    private String outputFormat = "ORIGINAL";

    /** 是否自动压缩新图片文件 */
    private boolean compressImages = true;

    /** 是否自动压缩新视频文件 */
    private boolean compressVideos = true;

    /** 压缩后是否删除原文件 */
    private boolean deleteOriginal = false;

    /** 监控总开关 */
    private boolean enabled = false;

    // ==================== Getter / Setter ====================

    public String getWatchFolderPath() { return watchFolderPath; }
    public void setWatchFolderPath(String watchFolderPath) { this.watchFolderPath = watchFolderPath; }

    public String getOutputFolderPath() { return outputFolderPath; }
    public void setOutputFolderPath(String outputFolderPath) { this.outputFolderPath = outputFolderPath; }

    public int getImageQuality() { return imageQuality; }
    public void setImageQuality(int imageQuality) { this.imageQuality = imageQuality; }

    public int getVideoCrf() { return videoCrf; }
    public void setVideoCrf(int videoCrf) { this.videoCrf = videoCrf; }

    public String getOutputFormat() { return outputFormat; }
    public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }

    public boolean isCompressImages() { return compressImages; }
    public void setCompressImages(boolean compressImages) { this.compressImages = compressImages; }

    public boolean isCompressVideos() { return compressVideos; }
    public void setCompressVideos(boolean compressVideos) { this.compressVideos = compressVideos; }

    public boolean isDeleteOriginal() { return deleteOriginal; }
    public void setDeleteOriginal(boolean deleteOriginal) { this.deleteOriginal = deleteOriginal; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
