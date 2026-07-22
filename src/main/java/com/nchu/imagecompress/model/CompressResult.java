package com.nchu.imagecompress.model;

/**
 * 压缩结果实体。
 *
 * <p>记录单次压缩操作的结果信息，包括输入/输出文件、压缩前后大小、
 * 压缩率、处理状态和耗时。由 Service 层产生，传递给 Controller/View。</p>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-08
 */
public class CompressResult {

    // ==================== 输入信息 ====================

    /** 输入文件信息（图片） */
    private ImageFileInfo inputInfo;

    /** 输入文件信息（视频） */
    private VideoFileInfo videoInputInfo;

    /** 输入文件信息（通用接口） */
    private FileInfo sourceFileInfo;

    /** 使用的压缩配置（图片） */
    private CompressConfig config;

    /** 使用的压缩配置（视频） */
    private VideoCompressConfig videoConfig;

    // ==================== 输出信息 ====================

    /** 输出文件路径 */
    private String outputPath;

    /** 输出文件大小（字节） */
    private long outputSize;

    // ==================== 处理结果 ====================

    /** 是否成功 */
    private boolean success;

    /** 失败时的错误信息 */
    private String errorMessage;

    /** 处理耗时（毫秒） */
    private long elapsedMs;

    /** 变体标签（批量导出模式，如 "720p_30fps_crf23"；单版本模式为 null） */
    private String variantLabel;

    /** 警告信息（成功但存在非致命问题，如"输出比输入大"） */
    private String warning;

    /** v2.3: 封面快照输出路径（视频封面提取功能） */
    private String coverPath;

    // ==================== 构造 ====================

    public CompressResult() {
    }

    /**
     * 创建失败结果（用于异常回传）。
     *
     * @param inputInfo 输入文件信息
     * @param config    压缩配置
     * @param errorMsg  错误信息
     */
    public static CompressResult fail(ImageFileInfo inputInfo, CompressConfig config, String errorMsg) {
        CompressResult result = new CompressResult();
        result.inputInfo = inputInfo;
        result.config = config;
        result.success = false;
        result.errorMessage = errorMsg;
        return result;
    }

    // ==================== 视频工厂方法（v2.0） ====================

    /**
     * 创建视频压缩成功结果。
     */
    public static CompressResult success(VideoFileInfo info, String outputPath,
                                          long outputSize, long elapsedMs) {
        CompressResult result = new CompressResult();
        result.videoInputInfo = info;
        result.sourceFileInfo = info;
        result.outputPath = outputPath;
        result.outputSize = outputSize;
        result.elapsedMs = elapsedMs;
        result.success = true;
        return result;
    }

    /**
     * 创建视频压缩失败结果。
     */
    public static CompressResult failure(VideoFileInfo info, String errorMsg) {
        CompressResult result = new CompressResult();
        result.videoInputInfo = info;
        result.sourceFileInfo = info;
        result.success = false;
        result.errorMessage = errorMsg;
        return result;
    }

    /**
     * 创建视频压缩取消结果。
     */
    public static CompressResult cancelled(VideoFileInfo info) {
        CompressResult result = new CompressResult();
        result.videoInputInfo = info;
        result.sourceFileInfo = info;
        result.success = false;
        result.errorMessage = "已取消";
        return result;
    }

    // ==================== 计算属性 ====================

    /**
     * 获取原始文件大小（兼容图片和视频）。
     */
    private long getOriginalSizeInternal() {
        if (inputInfo != null) return inputInfo.getOriginalSize();
        if (videoInputInfo != null) return videoInputInfo.getOriginalSize();
        return 0;
    }

    /**
     * 获取压缩后节省的字节数。
     * 正数表示节省空间，负数表示输出比输入更大。
     */
    public long getSavedBytes() {
        return getOriginalSizeInternal() - outputSize;
    }

    /**
     * 获取压缩率（百分比）。
     * 例如：原文件 100KB → 压缩后 60KB，压缩率 = 40.0%
     */
    public double getCompressionRatio() {
        long original = getOriginalSizeInternal();
        if (original == 0) return 0;
        return (1.0 - (double) outputSize / original) * 100.0;
    }

    /**
     * 获取压缩率格式化字符串。
     */
    public String getFormattedRatio() {
        double ratio = getCompressionRatio();
        if (ratio < 0) {
            return String.format("+%.1f%% (增大)", -ratio);
        }
        return String.format("%.1f%%", ratio);
    }

    /**
     * 获取耗时格式化字符串。
     */
    public String getFormattedElapsed() {
        if (elapsedMs < 1000) {
            return elapsedMs + "ms";
        }
        return String.format("%.1fs", elapsedMs / 1000.0);
    }

    // ==================== Getter / Setter ====================

    public ImageFileInfo getInputInfo() { return inputInfo; }
    public void setInputInfo(ImageFileInfo inputInfo) { this.inputInfo = inputInfo; }

    public VideoFileInfo getVideoInputInfo() { return videoInputInfo; }
    public void setVideoInputInfo(VideoFileInfo videoInputInfo) { this.videoInputInfo = videoInputInfo; }

    public FileInfo getSourceFileInfo() { return sourceFileInfo; }

    public CompressConfig getConfig() { return config; }
    public void setConfig(CompressConfig config) { this.config = config; }

    public VideoCompressConfig getVideoConfig() { return videoConfig; }
    public void setVideoConfig(VideoCompressConfig videoConfig) { this.videoConfig = videoConfig; }

    public String getOutputPath() { return outputPath; }
    public void setOutputPath(String outputPath) { this.outputPath = outputPath; }

    public long getOutputSize() { return outputSize; }
    public void setOutputSize(long outputSize) { this.outputSize = outputSize; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public long getElapsedMs() { return elapsedMs; }
    public void setElapsedMs(long elapsedMs) { this.elapsedMs = elapsedMs; }

    public String getVariantLabel() { return variantLabel; }
    public void setVariantLabel(String variantLabel) { this.variantLabel = variantLabel; }

    public String getWarning() { return warning; }
    public void setWarning(String warning) { this.warning = warning; }
    public boolean hasWarning() { return warning != null && !warning.isEmpty(); }

    public String getCoverPath() { return coverPath; }
    public void setCoverPath(String coverPath) { this.coverPath = coverPath; }
    public boolean hasCover() { return coverPath != null && !coverPath.isEmpty(); }

    /**
     * 追加警告信息（多条警告用分号分隔）。
     */
    public void addWarning(String msg) {
        if (warning == null || warning.isEmpty()) {
            warning = msg;
        } else {
            warning = warning + "; " + msg;
        }
    }

    @Override
    public String toString() {
        if (!success) {
            return "CompressResult{FAILED: " + errorMessage + "}";
        }
        return "CompressResult{" + getFormattedRatio() + ", " + getFormattedElapsed() + "}";
    }
}
