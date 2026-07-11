package com.nchu.imagecompress.util;

import com.nchu.imagecompress.model.OutputFormat;

import java.io.File;

/**
 * 参数校验工具类。
 *
 * <p>对用户输入的压缩参数进行合法性校验，返回校验结果。
 * 所有方法均为静态、无状态方法，不依赖 UI 层。</p>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-08
 */
public final class ValidateUtil {

    private ValidateUtil() {
        // 工具类禁止实例化
    }

    /** 质量最小值 */
    public static final int QUALITY_MIN = 0;
    /** 质量最大值 */
    public static final int QUALITY_MAX = 100;
    /** 缩放百分比最小值 */
    public static final int SCALE_MIN = 1;
    /** 缩放百分比最大值 */
    public static final int SCALE_MAX = 100;
    /** 最大宽度/高度下限 */
    public static final int DIMENSION_MIN = 1;
    /** 最大宽度/高度上限（防止内存溢出） */
    public static final int DIMENSION_MAX = 99999;
    /** 输出路径最大长度 */
    public static final int PATH_MAX_LENGTH = 260;

    // ==================== 校验结果 ====================

    /**
     * 校验结果封装。
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public static ValidationResult ok() {
            return new ValidationResult(true, "OK");
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
    }

    // ==================== 质量校验 ====================

    /**
     * 校验压缩质量值是否在合法范围 [0, 100] 内。
     *
     * @param quality 质量值
     * @return 校验结果
     */
    public static ValidationResult validateQuality(int quality) {
        if (quality < QUALITY_MIN || quality > QUALITY_MAX) {
            return ValidationResult.error(
                    "质量值必须在 " + QUALITY_MIN + " 到 " + QUALITY_MAX + " 之间，当前值: " + quality);
        }
        return ValidationResult.ok();
    }

    /**
     * 校验压缩质量值（double 版本，0.0 - 1.0）。
     */
    public static ValidationResult validateQuality(double quality) {
        if (quality < 0.0 || quality > 1.0) {
            return ValidationResult.error(
                    "质量值必须在 0.0 到 1.0 之间，当前值: " + String.format("%.2f", quality));
        }
        return ValidationResult.ok();
    }

    // ==================== 缩放校验 ====================

    /**
     * 校验缩放百分比。
     *
     * @param percent 缩放百分比 (1-100)
     * @return 校验结果
     */
    public static ValidationResult validateScalePercent(int percent) {
        if (percent < SCALE_MIN || percent > SCALE_MAX) {
            return ValidationResult.error(
                    "缩放百分比必须在 " + SCALE_MIN + " 到 " + SCALE_MAX + " 之间，当前值: " + percent);
        }
        return ValidationResult.ok();
    }

    /**
     * 校验最大宽度/高度。
     */
    public static ValidationResult validateDimension(int dimension) {
        if (dimension < DIMENSION_MIN || dimension > DIMENSION_MAX) {
            return ValidationResult.error(
                    "尺寸必须在 " + DIMENSION_MIN + " 到 " + DIMENSION_MAX + " 之间，当前值: " + dimension);
        }
        return ValidationResult.ok();
    }

    // ==================== 输出路径校验 ====================

    /**
     * 校验输出路径是否合法。
     *
     * @param outputPath 输出路径
     * @return 校验结果
     */
    public static ValidationResult validateOutputPath(String outputPath) {
        if (outputPath == null || outputPath.trim().isEmpty()) {
            return ValidationResult.error("输出路径不能为空");
        }

        String trimmed = outputPath.trim();

        // 路径长度检查
        if (trimmed.length() > PATH_MAX_LENGTH) {
            return ValidationResult.error("输出路径过长（最大 " + PATH_MAX_LENGTH + " 字符）");
        }

        // 尝试创建目录
        File dir = new File(trimmed);
        try {
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                if (!created) {
                    return ValidationResult.error("无法创建输出目录: " + trimmed);
                }
            }
            if (!dir.isDirectory()) {
                return ValidationResult.error("输出路径不是有效的目录: " + trimmed);
            }
            if (!dir.canWrite()) {
                return ValidationResult.error("输出目录没有写入权限: " + trimmed);
            }
        } catch (SecurityException e) {
            return ValidationResult.error("无权限访问输出路径: " + trimmed + " (" + e.getMessage() + ")");
        }

        return ValidationResult.ok();
    }

    // ==================== 输入文件校验 ====================

    /**
     * 校验输入文件是否合法。
     *
     * @param file 输入文件
     * @return 校验结果
     */
    public static ValidationResult validateInputFile(File file) {
        if (file == null) {
            return ValidationResult.error("输入文件不能为空");
        }
        if (!file.exists()) {
            return ValidationResult.error("文件不存在: " + file.getAbsolutePath());
        }
        if (!file.isFile()) {
            return ValidationResult.error("不是有效的文件: " + file.getAbsolutePath());
        }
        if (!file.canRead()) {
            return ValidationResult.error("文件无读取权限: " + file.getAbsolutePath());
        }
        if (file.length() == 0) {
            return ValidationResult.error("文件大小为 0，无法处理: " + file.getName());
        }
        // 检查文件大小上限（500MB，防止 OOM）
        long maxSize = 500L * 1024 * 1024;
        if (file.length() > maxSize) {
            return ValidationResult.error("文件过大（最大支持 500MB）: " + file.getName()
                    + " (" + FileUtil.formatFileSize(file.length()) + ")");
        }
        return ValidationResult.ok();
    }

    // ==================== 综合校验 ====================

    /**
     * 综合校验压缩参数。
     *
     * @param quality    压缩质量
     * @param scalePct   缩放百分比
     * @param maxWidth   最大宽度
     * @param maxHeight  最大高度
     * @param outputPath 输出路径
     * @param format     输出格式
     * @return 校验结果（第一个失败项的信息）
     */
    public static ValidationResult validateCompressParams(
            int quality, int scalePct, int maxWidth, int maxHeight,
            String outputPath, OutputFormat format) {

        ValidationResult r;

        r = validateQuality(quality);
        if (!r.isValid()) return r;

        r = validateScalePercent(scalePct);
        if (!r.isValid()) return r;

        r = validateDimension(maxWidth);
        if (!r.isValid()) return r;

        r = validateDimension(maxHeight);
        if (!r.isValid()) return r;

        r = validateOutputPath(outputPath);
        if (!r.isValid()) return r;

        if (format == null) {
            return ValidationResult.error("输出格式不能为空");
        }

        return ValidationResult.ok();
    }
}
