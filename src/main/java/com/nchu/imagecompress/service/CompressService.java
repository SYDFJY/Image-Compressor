package com.nchu.imagecompress.service;

import com.nchu.imagecompress.model.CompressConfig;
import com.nchu.imagecompress.model.CompressResult;
import com.nchu.imagecompress.model.ImageFileInfo;
import com.nchu.imagecompress.model.OutputFormat;
import com.nchu.imagecompress.util.FileUtil;
import com.nchu.imagecompress.util.LogUtil;
import com.nchu.imagecompress.util.ValidateUtil;

import java.io.File;

/**
 * 单张图片压缩服务。
 *
 * <p>负责单张图片的压缩流程：
 * 文件名生成（按命名规则）→ 校验 → 压缩 → 返回结果。
 * 不涉及批量逻辑、不处理进度回调。</p>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-08
 */
public class CompressService {

    // ==================== 单张压缩 ====================

    /**
     * 执行单张图片压缩。
     *
     * <p>流程：
     * <ol>
     *   <li>校验输入文件合法性</li>
     *   <li>根据命名规则生成输出文件名</li>
     *   <li>确保输出目录存在</li>
     *   <li>调用 ImageCompressUtil 执行压缩</li>
     *   <li>返回 CompressResult</li>
     * </ol>
     *
     * @param inputInfo 输入文件信息
     * @param config    压缩配置
     * @return 压缩结果（成功/失败均有结果）
     */
    public CompressResult compress(ImageFileInfo inputInfo, CompressConfig config) {
        // ① 校验输入文件
        File inputFile = inputInfo.getSourceFile();
        ValidateUtil.ValidationResult validation = ValidateUtil.validateInputFile(inputFile);
        if (!validation.isValid()) {
            return CompressResult.fail(inputInfo, config, validation.getMessage());
        }

        // ② 确定输出目录
        String outputDir = resolveOutputDir(config);
        ValidateUtil.ValidationResult pathCheck = ValidateUtil.validateOutputPath(outputDir);
        if (!pathCheck.isValid()) {
            return CompressResult.fail(inputInfo, config, pathCheck.getMessage());
        }

        // ③ 生成输出文件名
        String outputFileName = buildOutputFileName(inputFile.getName(), config);

        // ④ 去重处理（非覆盖模式下，自动编号）
        if (!config.isOverwrite()) {
            outputFileName = FileUtil.generateUniqueFilename(outputDir, outputFileName);
        }

        // ⑤ 执行压缩（v2: 目标大小模式使用二分搜索）
        File outputFile = new File(outputDir, outputFileName);
        CompressResult result;
        if (config.getTargetSizeKB() > 0) {
            result = compressWithTargetSize(inputFile, config, outputFile);
        } else {
            result = ImageCompressUtil.compress(inputFile, config, outputFile);
        }

        // ⑤b 压缩后大小守卫：如果输出比输入大且非覆盖模式，保留原文件并警告
        if (result.isSuccess() && outputFile.exists()
                && outputFile.length() > inputFile.length()) {
            long inputSize = inputFile.length();
            long outputSizeBefore = outputFile.length();
            double increasePct = (outputSizeBefore - inputSize) * 100.0 / inputSize;
            // 删除更大的输出文件，复制原文件
            outputFile.delete();
            try {
                java.nio.file.Files.copy(inputFile.toPath(), outputFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                result.setOutputSize(inputFile.length());
                result.addWarning("输出比原文件大 " + String.format("%.1f%%", increasePct)
                        + "（原 " + ImageFileInfo.formatFileSize(inputSize)
                        + " → 压缩后 " + ImageFileInfo.formatFileSize(outputSizeBefore)
                        + "），已保留原文件。建议降低压缩质量后重试。");
                LogUtil.info("[CompressService] 大小守卫触发: " + inputFile.getName()
                        + " 输出(" + ImageFileInfo.formatFileSize(outputSizeBefore)
                        + ") > 输入(" + ImageFileInfo.formatFileSize(inputSize) + ")，已保留原文件");
            } catch (java.io.IOException e) {
                LogUtil.warning("[CompressService] 大小守卫复制原文件失败: " + e.getMessage());
            }
        }

        // ⑥ 检查覆盖后是否为同一文件
        if (result.isSuccess() && outputFile.getAbsolutePath()
                .equals(inputFile.getAbsolutePath())) {
            result.setSuccess(false);
            result.setErrorMessage("输出文件与输入文件相同，已跳过（请更改命名规则或输出目录）");
        }

        return result;
    }

    // ==================== 文件命名 ====================

    /**
     * 根据命名规则生成输出文件名。
     *
     * @param originalName 原始文件名（如 "photo.jpg"）
     * @param config       压缩配置
     * @return 生成的文件名
     */
    public String buildOutputFileName(String originalName, CompressConfig config) {
        String nameWithoutExt = FileUtil.getNameWithoutExtension(originalName);
        String originalExt = FileUtil.getExtension(originalName);

        // 确定输出扩展名
        String outputExt;
        OutputFormat format = config.getOutputFormat();
        if (format == OutputFormat.ORIGINAL) {
            outputExt = originalExt.isEmpty() ? "jpg" : originalExt;
        } else {
            outputExt = format.getExtension();
        }

        // 按命名规则组合
        switch (config.getNamingRule()) {
            case ADD_PREFIX:
                return config.getPrefix() + nameWithoutExt + "." + outputExt;

            case KEEP_ORIGINAL:
                return nameWithoutExt + "." + outputExt;

            case CUSTOM:
                String name = config.getCustomName();
                if (name == null || name.trim().isEmpty()) {
                    // 自定义名称为空时回退到后缀模式
                    return nameWithoutExt + config.getSuffix() + "." + outputExt;
                }
                return sanitizeFileName(name.trim()) + "." + outputExt;

            case ADD_SUFFIX:
            default:
                return nameWithoutExt + config.getSuffix() + "." + outputExt;
        }
    }

    /**
     * 清理文件名中的非法字符。
     */
    private static String sanitizeFileName(String name) {
        if (name == null || name.isEmpty()) return "output";
        // Windows 文件系统非法字符
        String sanitized = name.replaceAll("[\\\\/:*?\"<>|]", "");
        return sanitized.isEmpty() ? "output" : sanitized;
    }

    /**
     * 确定输出目录。
     * <p>优先使用配置指定的路径，为空则使用默认目录。</p>
     */
    private String resolveOutputDir(CompressConfig config) {
        String path = config.getOutputPath();
        if (path == null || path.trim().isEmpty()) {
            return FileUtil.getDefaultOutputDir();
        }
        return path.trim();
    }

    // ==================== 目标大小二分搜索（v2） ====================

    /**
     * 使用二分搜索查找最优质量值，使输出文件尽可能接近但不超出目标大小。
     *
     * <p>算法流程：
     * <ol>
     *   <li>设定搜索边界：low=1, high=100</li>
     *   <li>取中点质量值，压缩到临时文件</li>
     *   <li>若输出 ≤ 目标大小 → 记录此质量为最佳，向右搜索更高画质</li>
     *   <li>若输出 > 目标大小 → 向左搜索更低画质</li>
     *   <li>最多 8 轮迭代后取最佳质量值进行最终压缩</li>
     * </ol>
     *
     * @param inputFile  输入文件
     * @param config     压缩配置（含目标大小 KB）
     * @param outputFile 最终输出文件
     * @return 压缩结果
     */
    private CompressResult compressWithTargetSize(File inputFile, CompressConfig config, File outputFile) {
        long targetBytes = config.getTargetSizeKB() * 1024L;
        int bestQuality = config.getQuality(); // 默认：用户设定的质量
        long bestSize = Long.MAX_VALUE;
        boolean found = false;

        int low = 5;
        int high = 100;
        int iterations = 0;
        final int MAX_ITERATIONS = 8;

        // 先试最低质量 → 如果还是太大，放弃
        try {
            File tmpFile = File.createTempFile("nchu_target_", ".tmp");
            tmpFile.deleteOnExit();
            CompressConfig trialConfig = buildTrialConfig(config, low);
            CompressResult trial = ImageCompressUtil.compress(inputFile, trialConfig, tmpFile);
            if (trial.isSuccess()) {
                long size = tmpFile.length();
                if (size > targetBytes) {
                    // 最低质量仍然超限 → 返回最低质量的结果
                    bestQuality = low;
                    bestSize = size;
                    tmpFile.delete();
                } else {
                    bestQuality = low;
                    bestSize = size;
                    found = true;
                    tmpFile.delete();
                }
            }
        } catch (Exception ignored) { /* 试压缩失败，继续 */ }

        // 二分搜索
        while (low <= high && iterations < MAX_ITERATIONS) {
            int mid = (low + high) / 2;
            iterations++;

            try {
                File tmpFile = File.createTempFile("nchu_target_", ".tmp");
                tmpFile.deleteOnExit();
                CompressConfig trialConfig = buildTrialConfig(config, mid);
                CompressResult trial = ImageCompressUtil.compress(inputFile, trialConfig, tmpFile);

                if (trial.isSuccess()) {
                    long size = tmpFile.length();
                    if (size <= targetBytes && size > 0) {
                        // 满足目标 → 记录并尝试更高画质
                        if (mid > bestQuality || (mid == bestQuality && size > bestSize)) {
                            bestQuality = mid;
                            bestSize = size;
                        }
                        found = true;
                        low = mid + 1;
                    } else {
                        high = mid - 1;
                    }
                    tmpFile.delete();
                } else {
                    high = mid - 1;
                }
            } catch (Exception e) {
                LogUtil.warning("[CompressService] 二分搜索迭代 " + iterations + " 失败: " + e.getMessage());
                high = mid - 1;
            }
        }

        // 使用最佳质量执行最终压缩
        if (found) {
            config.setQuality(bestQuality);
            LogUtil.info("[CompressService] 目标大小搜索完成: 最佳质量=" + bestQuality
                    + ", 预估大小=" + bestSize + " bytes, 迭代=" + iterations);
        }
        return ImageCompressUtil.compress(inputFile, config, outputFile);
    }

    /**
     * 构建试压缩配置（仅质量差异，其他参数与主配置一致）。
     */
    private CompressConfig buildTrialConfig(CompressConfig base, int quality) {
        CompressConfig cfg = new CompressConfig();
        cfg.setQuality(quality);
        cfg.setScaleMode(base.getScaleMode());
        cfg.setScalePercent(base.getScalePercent());
        cfg.setMaxWidth(base.getMaxWidth());
        cfg.setMaxHeight(base.getMaxHeight());
        cfg.setOutputFormat(base.getOutputFormat());
        cfg.setPreserveMetadata(base.isPreserveMetadata());
        return cfg;
    }
}
