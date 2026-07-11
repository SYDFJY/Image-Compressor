package com.nchu.imagecompress.service;

import com.nchu.imagecompress.model.CompressResult;
import com.nchu.imagecompress.model.VideoCompressConfig;
import com.nchu.imagecompress.model.VideoFileInfo;
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

        // ④ 检查是否覆盖
        if (outputFile.exists() && !config.isOverwrite()) {
            return CompressResult.failure(info,
                    "输出文件已存在: " + outputFile.getName() + "（请启用覆盖或修改命名规则）");
        }

        // ⑤ 执行 FFmpeg 压缩
        try {
            VideoCompressUtil.executeCompress(
                    info.getSourceFile(),
                    outputFile,
                    config,
                    info.getDurationSeconds(),
                    null // 单文件不设进度回调
            );

            long elapsed = System.currentTimeMillis() - startTime;
            long outputSize = outputFile.length();

            LogUtil.info("[VideoCompressService] 压缩成功: " + info.getFileName()
                    + " → " + outputFile.getName()
                    + " (" + VideoFileInfo.formatFileSize(outputSize) + ")"
                    + " 耗时 " + elapsed + "ms");

            return CompressResult.success(info, outputFile.getAbsolutePath(),
                    outputSize, elapsed);

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
