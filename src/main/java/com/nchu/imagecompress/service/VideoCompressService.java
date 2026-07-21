package com.nchu.imagecompress.service;

import com.nchu.imagecompress.model.CompressResult;
import com.nchu.imagecompress.model.VideoCompressConfig;
import com.nchu.imagecompress.model.VideoFileInfo;
import com.nchu.imagecompress.util.FileUtil;
import com.nchu.imagecompress.util.LogUtil;

import java.io.File;
import java.io.IOException;

/**
 * 单视频压缩服务 — 封装完整的单文件视频压缩流程。
 *
 * <p>流程：参数校验 → 生成输出路径 → 执行 FFmpeg 压缩 → 构建结果</p>
 *
 * @author NCHU-Student
 * @version 2.0.0
 * @since 2026-07-11
 */
public class VideoCompressService {

    /**
     * 压缩单个视频文件。
     *
     * @param info   视频文件信息（含元数据）
     * @param config 视频压缩配置
     * @return 压缩结果
     */
    public CompressResult compress(VideoFileInfo info, VideoCompressConfig config) {
        long startTime = System.currentTimeMillis();

        // ① 参数校验
        if (info == null || info.getSourceFile() == null || !info.getSourceFile().exists()) {
            return CompressResult.failure(info, "源文件不存在");
        }

        // ①b 裁剪时间合法性校验（防止 -ss 超过视频总时长导致空输出）
        if (config.getStartTimeSeconds() > 0 && info.getDurationSeconds() > 0
                && config.getStartTimeSeconds() >= info.getDurationSeconds()) {
            return CompressResult.failure(info, "裁剪起始时间（"
                    + String.format("%.1f", config.getStartTimeSeconds())
                    + " 秒）超过视频总时长（"
                    + String.format("%.1f", info.getDurationSeconds()) + " 秒）");
        }

        // ② 生成输出路径
        File outputFile;
        try {
            outputFile = VideoCompressUtil.generateOutputFile(info.getSourceFile(), config);
        } catch (Exception e) {
            return CompressResult.failure(info, "无法生成输出路径: " + e.getMessage());
        }

        // ③ 确保输出目录存在
        File outputDir = outputFile.getParentFile();
        if (outputDir != null && !outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                return CompressResult.failure(info, "无法创建输出目录: " + outputDir.getAbsolutePath());
            }
        }

        // ④ 检查是否覆盖（非覆盖模式下自动编号，与图片压缩行为一致）
        if (outputFile.exists() && !config.isOverwrite()) {
            String uniqueName = FileUtil.generateUniqueFilename(
                    outputDir.getAbsolutePath(), outputFile.getName());
            outputFile = new File(outputDir, uniqueName);
        }

        // ⑤ 计算有效输出时长（考虑裁剪）
        double effectiveDuration = info.getDurationSeconds();
        if (config.getStartTimeSeconds() > 0) {
            effectiveDuration -= config.getStartTimeSeconds();
        }
        if (config.getDurationSeconds() > 0) {
            effectiveDuration = Math.min(effectiveDuration, config.getDurationSeconds());
        }
        effectiveDuration = Math.max(effectiveDuration, 1.0); // 至少 1 秒避免除零

        // ⑤b 若使用大小优先模式，计算目标码率
        if (config.getRateControlMode() == VideoCompressConfig.RateControlMode.TARGET_SIZE
                && config.getTargetSizeMB() > 0) {
            double targetKBitsTotal = config.getTargetSizeMB() * 8.0 * 1024.0; // 总目标 kb
            double audioBitrateKbps = config.getAudioMode() == VideoCompressConfig.AudioMode.REMOVE ? 0 : 128;
            int videoBitrateKbps = (int) ((targetKBitsTotal / effectiveDuration) - audioBitrateKbps);
            config.setTargetBitrate(Math.max(100, videoBitrateKbps)); // 最低 100 kbps
        }

        // ⑥ 执行 FFmpeg 压缩
        try {
            VideoCompressUtil.executeCompress(
                    info.getSourceFile(),
                    outputFile,
                    config,
                    effectiveDuration,
                    null // 单文件不设进度回调
            );

            long elapsed = System.currentTimeMillis() - startTime;
            long outputSize = outputFile.length();

            LogUtil.info("[VideoCompressService] 压缩成功: " + info.getFileName()
                    + " → " + outputFile.getName()
                    + " (" + VideoFileInfo.formatFileSize(outputSize) + ")"
                    + " 耗时 " + elapsed + "ms");

            CompressResult result = CompressResult.success(info, outputFile.getAbsolutePath(),
                    outputSize, elapsed);

            // 压缩后大小守卫：输出不小于原文件时警告
            long inputSize = info.getSourceFile().length();
            if (outputSize >= inputSize) {
                result.addWarning("输出文件 (" + VideoFileInfo.formatFileSize(outputSize)
                        + ") 不小于原文件 (" + VideoFileInfo.formatFileSize(inputSize)
                        + ")，建议降低 CRF 值（下滑画质滑块）后重试。");
                LogUtil.info("[VideoCompressService] 大小守卫触发: " + info.getFileName()
                        + " 输出(" + VideoFileInfo.formatFileSize(outputSize)
                        + ") >= 输入(" + VideoFileInfo.formatFileSize(inputSize) + ")");
            }

            return result;

        } catch (InterruptedException e) {
            LogUtil.info("[VideoCompressService] 压缩被取消: " + info.getFileName());
            return CompressResult.cancelled(info);
        } catch (IOException e) {
            LogUtil.error("[VideoCompressService] 压缩失败: " + info.getFileName()
                    + " — " + e.getMessage());
            return CompressResult.failure(info, e.getMessage());
        } catch (Exception e) {
            LogUtil.error("[VideoCompressService] 压缩异常: " + info.getFileName()
                    + " — " + e.getMessage());
            return CompressResult.failure(info, "压缩异常: " + e.getMessage());
        }
    }
}
