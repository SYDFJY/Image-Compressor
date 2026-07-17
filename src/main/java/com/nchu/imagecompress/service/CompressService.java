package com.nchu.imagecompress.service;

import com.nchu.imagecompress.model.CompressConfig;
import com.nchu.imagecompress.model.CompressResult;
import com.nchu.imagecompress.model.ImageFileInfo;
import com.nchu.imagecompress.model.OutputFormat;
import com.nchu.imagecompress.util.FileUtil;
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

        // ⑤ 执行压缩
        File outputFile = new File(outputDir, outputFileName);
        CompressResult result = ImageCompressUtil.compress(inputFile, config, outputFile);

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
}
