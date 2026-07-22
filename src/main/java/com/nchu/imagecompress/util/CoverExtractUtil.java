package com.nchu.imagecompress.util;

import com.nchu.imagecompress.model.VideoFileInfo;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * 视频封面快照提取工具 — 从视频指定时间点截取一帧保存为图片。
 *
 * <p>复用 {@link FfmpegRenderUtil#startSingleFrame(File, int, double)} 启动 FFmpeg 子进程，
 * 读取 raw RGB24 帧数据，转换为 {@link BufferedImage} 后通过 {@link ImageIO} 写入磁盘。</p>
 *
 * <p>封面提取失败不抛异常，返回 null 并记录日志——封面为非必需功能。</p>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-22
 */
public final class CoverExtractUtil {

    private CoverExtractUtil() { /* 工具类禁止实例化 */ }

    /**
     * 从视频中提取封面快照并保存到输出目录。
     *
     * @param videoFile   源视频文件
     * @param outputDir   输出目录
     * @param baseName    输出文件基础名（不含扩展名）
     * @param seekSeconds 截取时间点（秒）
     * @param origWidth   原始视频宽度
     * @param origHeight  原始视频高度
     * @param format      输出格式（"jpg" 或 "png"）
     * @return 封面文件绝对路径，失败返回 null
     */
    public static String extractCover(File videoFile, String outputDir,
                                       String baseName, double seekSeconds,
                                       int origWidth, int origHeight,
                                       String format) {
        if (videoFile == null || !videoFile.exists()) return null;

        // 计算封面输出尺寸（保持宽高比，最大宽度 1920px）
        int targetWidth = Math.min(origWidth, 1920);
        Dimension size = FfmpegRenderUtil.calculatePreviewSize(
                origWidth, origHeight, targetWidth);

        Process process = null;
        try {
            process = FfmpegRenderUtil.startSingleFrame(
                    videoFile, size.width, seekSeconds);

            byte[] rgb = FfmpegRenderUtil.readFrame(
                    process.getInputStream(), size.width, size.height);

            if (rgb == null) {
                LogUtil.info("[CoverExtractUtil] 封面帧读取失败（可能 seek 超出视频时长）");
                return null;
            }

            BufferedImage image = FfmpegRenderUtil.rgbToImage(
                    rgb, size.width, size.height);

            // 输出文件名：baseName + _cover + .format
            String fmt = (format != null && format.equalsIgnoreCase("png")) ? "png" : "jpg";
            String fileName = baseName + "_cover." + fmt;
            File outputFile = new File(outputDir, fileName);

            // 去重（避免覆盖已有封面）
            outputFile = resolveUnique(outputFile);

            if (ImageIO.write(image, fmt, outputFile)) {
                LogUtil.info("[CoverExtractUtil] 封面已保存: " + outputFile.getAbsolutePath());
                return outputFile.getAbsolutePath();
            } else {
                LogUtil.info("[CoverExtractUtil] 封面写入失败（无合适的 ImageWriter）");
                return null;
            }

        } catch (IOException e) {
            LogUtil.info("[CoverExtractUtil] 封面提取失败: " + e.getMessage());
            return null;
        } catch (Exception e) {
            LogUtil.info("[CoverExtractUtil] 封面提取异常: " + e.getMessage());
            return null;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    /**
     * 确保输出文件不覆盖已存在的文件。
     * 若 target 已存在，追加 "_1", "_2" ... 直到找到唯一名称。
     */
    private static File resolveUnique(File target) {
        if (!target.exists()) return target;
        String parent = target.getParent();
        String name = target.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        int counter = 1;
        File candidate;
        do {
            candidate = new File(parent, base + "_" + counter + ext);
            counter++;
        } while (candidate.exists() && counter < 10000);
        return candidate;
    }
}
