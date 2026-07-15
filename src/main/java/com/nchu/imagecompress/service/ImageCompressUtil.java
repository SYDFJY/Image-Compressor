package com.nchu.imagecompress.service;

import com.nchu.imagecompress.model.CompressConfig;
import com.nchu.imagecompress.model.CompressResult;
import com.nchu.imagecompress.model.ImageFileInfo;
import com.nchu.imagecompress.model.OutputFormat;
import net.coobird.thumbnailator.Thumbnails;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

/**
 * 图片压缩核心工具类 — 封装 Thumbnailator API。
 *
 * <p>提供单张图片压缩和预览生成功能，是 CompressService 的底层引擎。
 * 所有方法均为静态方法，不持有状态。</p>
 *
 * <h3>关键行为</h3>
 * <ul>
 *   <li>质量压缩：{@code outputQuality(0.0-1.0)}</li>
 *   <li>尺寸缩放：{@code size(w,h)} 等比缩放</li>
 *   <li>格式转换：{@code outputFormat("jpg"/"png")}</li>
 *   <li>PNG→JPG：自动以白色填充透明背景，防止出现黑色背景</li>
 * </ul>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-08
 */
public final class ImageCompressUtil {

    private ImageCompressUtil() {
        // 工具类禁止实例化
    }

    /** 最大处理像素数（防止 OOM） */
    private static final long MAX_PIXELS = 100_000_000L; // 10000×10000

    // ==================== 单张压缩 ====================

    /**
     * 压缩单张图片并返回结果。
     *
     * <p>根据 CompressConfig 配置执行质量压缩、尺寸缩放和格式转换。
     * 输出文件路径由调用方指定。</p>
     *
     * @param inputFile  输入图片文件
     * @param config     压缩配置
     * @param outputFile 输出目标文件
     * @return 压缩结果（含成功/失败、大小对比、耗时）
     */
    public static CompressResult compress(File inputFile, CompressConfig config, File outputFile) {
        long startTime = System.currentTimeMillis();

        // 构建输入信息
        ImageFileInfo inputInfo = new ImageFileInfo(inputFile);
        int[] dims = readDimensions(inputFile);
        inputInfo.setWidth(dims[0]);
        inputInfo.setHeight(dims[1]);

        CompressResult result = new CompressResult();
        result.setInputInfo(inputInfo);
        result.setConfig(config);

        // 格式可读性检查
        if (dims[0] == 0 || dims[1] == 0) {
            String ext = getExtension(inputFile);
            result.setSuccess(false);
            result.setErrorMessage("无法解析图片尺寸 — 格式可能不受支持 ("
                    + ext.toUpperCase() + ")。\n"
                    + "常见原因：CMYK 色彩空间 JPEG、损坏的图片文件、或 HEIC 等 JDK 8 不支持的格式。\n"
                    + "建议：用画图/Photoshop 另存为标准 RGB 格式的 JPEG/PNG 后重试。");
            result.setElapsedMs(System.currentTimeMillis() - startTime);
            return result;
        }

        // 像素数安全检查
        long totalPixels = (long) dims[0] * dims[1];
        if (totalPixels > MAX_PIXELS) {
            result.setSuccess(false);
            result.setErrorMessage("图片分辨率过大 (" + dims[0] + "×" + dims[1]
                    + ")，最大支持 " + MAX_PIXELS + " 像素");
            result.setElapsedMs(System.currentTimeMillis() - startTime);
            return result;
        }

        try {
            // ③ 格式处理：需要填充透明背景的场景，走 BufferedImage 管线
            if (needsAlphaBackgroundFill(inputFile, config)) {
                double quality = config.getQuality() / 100.0;
                compressWithAlphaFill(inputFile, config, outputFile, quality);
            } else if (config.isPreserveMetadata() && isJpegOutput(config)) {
                // EXIF 保留模式：读原始元数据 → Thumbnailator 处理 → ImageIO 写回
                compressWithMetadata(inputFile, config, outputFile);
            } else {
                // 默认模式：Thumbnailator 直接写入（自动剥离 EXIF）
                Thumbnails.Builder<? extends File> builder = Thumbnails.of(inputFile);
                applyScale(builder, config);
                builder.outputQuality(config.getQuality() / 100.0);
                builder.outputFormat(resolveOutputExtension(inputFile, config));
                builder.toFile(outputFile);
            }

            // 构建成功结果
            result.setSuccess(true);
            result.setOutputPath(outputFile.getAbsolutePath());
            result.setOutputSize(outputFile.length());

        } catch (IOException e) {
            result.setSuccess(false);
            result.setErrorMessage("压缩失败 [" + e.getClass().getSimpleName() + "]: " + e.getMessage());
        } catch (OutOfMemoryError e) {
            result.setSuccess(false);
            result.setErrorMessage("内存不足，图片过大无法处理（建议调大 JVM 堆内存: -Xmx1024m）");
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage("未知错误 [" + e.getClass().getSimpleName() + "]: " + e.getMessage());
        }

        result.setElapsedMs(System.currentTimeMillis() - startTime);
        return result;
    }

    // ==================== 效果预览 ====================

    /**
     * 生成压缩效果预览图（应用压缩参数后的预览）。
     *
     * @param inputFile 输入图片
     * @param config    压缩配置
     * @param maxWidth  最大宽度
     * @param maxHeight 最大高度
     * @return 模拟压缩效果后的 BufferedImage，失败返回 null
     */
    public static BufferedImage generatePreview(File inputFile, CompressConfig config,
                                                 int maxWidth, int maxHeight) {
        try {
            Thumbnails.Builder<? extends File> builder = Thumbnails.of(inputFile)
                    .size(maxWidth, maxHeight);

            // 应用质量（用于效果对比）
            double quality = config.getQuality() / 100.0;
            builder.outputQuality(quality);

            return builder.asBufferedImage();
        } catch (IOException e) {
            return null;
        }
    }

    // ==================== 内部辅助 ====================

    /**
     * 应用缩放设置到 Thumbnailator Builder。
     */
    private static void applyScale(Thumbnails.Builder<?> builder, CompressConfig config) {
        switch (config.getScaleMode()) {
            case BY_PERCENT:
                double scale = config.getScalePercent() / 100.0;
                builder.scale(scale);
                break;

            case BY_MAX_SIZE:
                builder.size(config.getMaxWidth(), config.getMaxHeight());
                break;

            case NONE:
            default:
                // 显式设置 scale(1.0)，Thumbnailator 要求至少调用一次 size/scale
                builder.scale(1.0);
                break;
        }
    }

    /**
     * 确定输出文件的扩展名。
     */
    private static String resolveOutputExtension(File inputFile, CompressConfig config) {
        OutputFormat format = config.getOutputFormat();
        if (format == OutputFormat.ORIGINAL) {
            // 保持原格式
            String ext = getExtension(inputFile);
            return ext.isEmpty() ? "jpg" : ext;
        }
        return format.getExtension();
    }

    /**
     * 判断输出格式是否为 JPEG（EXIF 保留仅在 JPEG 上可用）。
     */
    private static boolean isJpegOutput(CompressConfig config) {
        OutputFormat fmt = config.getOutputFormat();
        if (fmt == OutputFormat.JPEG) return true;
        if (fmt == OutputFormat.ORIGINAL) return false; // 需要检查原格式
        return false;
    }

    /**
     * 保留 EXIF 元数据的压缩模式（JPEG 输出专用）。
     *
     * <p>流程：ImageIO 读原图 + 提取 JPEG 元数据 →
     * Thumbnailator 处理像素数据 →
     * ImageIO JPEG writer + 原始元数据写回。</p>
     */
    private static void compressWithMetadata(File inputFile, CompressConfig config,
                                             File outputFile) throws IOException {
        // Step 1: 读取原图并提取 JPEG 元数据
        ImageReader reader = null;
        IIOMetadata metadata = null;
        BufferedImage original = null;

        try {
            java.util.Iterator<ImageReader> readers =
                    ImageIO.getImageReadersBySuffix(getExtension(inputFile));
            if (readers.hasNext()) {
                reader = readers.next();
                ImageInputStream iis = ImageIO.createImageInputStream(inputFile);
                reader.setInput(iis);
                metadata = reader.getImageMetadata(0);
                original = reader.read(0);
                iis.close();
            }
        } catch (Exception ignored) {
            // 元数据提取失败：回退到普通模式
        }
        if (original == null) {
            original = ImageIO.read(inputFile);
        }

        if (original == null) {
            throw new IOException("无法读取图片: " + inputFile.getName());
        }

        // Step 2: 计算输出尺寸
        int outW = original.getWidth();
        int outH = original.getHeight();
        if (config.getScaleMode() == CompressConfig.ScaleMode.BY_PERCENT) {
            double scale = config.getScalePercent() / 100.0;
            outW = (int) (outW * scale);
            outH = (int) (outH * scale);
        } else if (config.getScaleMode() == CompressConfig.ScaleMode.BY_MAX_SIZE) {
            double ratio = Math.min(
                    (double) config.getMaxWidth() / outW,
                    (double) config.getMaxHeight() / outH);
            if (ratio < 1.0) {
                outW = (int) (outW * ratio);
                outH = (int) (outH * ratio);
            }
        }

        // Step 3: 用 Thumbnailator 处理像素数据
        BufferedImage processed = Thumbnails.of(original)
                .size(outW, outH)
                .outputQuality(config.getQuality() / 100.0)
                .asBufferedImage();

        // Step 4: 用 ImageIO 写入，携带原始元数据
        ImageWriter writer = null;
        try {
            java.util.Iterator<ImageWriter> writers =
                    ImageIO.getImageWritersByFormatName("jpeg");
            if (writers.hasNext() && metadata != null) {
                writer = writers.next();
                ImageOutputStream ios = ImageIO.createImageOutputStream(outputFile);
                writer.setOutput(ios);
                IIOImage iioImage = new IIOImage(processed, null, metadata);
                writer.write(iioImage);
                ios.close();
            } else {
                ImageIO.write(processed, "jpeg", outputFile);
            }
        } finally {
            if (writer != null) writer.dispose();
        }
    }

    /**
     * 判断是否需要透明背景填充（带透明度格式 → 不支持透明的格式）。
     *
     * <p>PNG / GIF / WebP / TIFF 均可能包含透明通道，
     * 输出为 JPEG 或 BMP（不支持透明）时需要白色背景填充，
     * 否则透明区域会显示为黑色。</p>
     */
    private static boolean needsAlphaBackgroundFill(File inputFile, CompressConfig config) {
        OutputFormat outputFormat = config.getOutputFormat();
        // 只有显式选择了 JPEG 或 BMP 才需要处理
        if (outputFormat != OutputFormat.JPEG && outputFormat != OutputFormat.BMP) {
            return false;
        }
        // 任何带透明通道的输入格式都需要填充
        String inputExt = getExtension(inputFile);
        return inputExt.equals("png") || inputExt.equals("gif")
                || inputExt.equals("tiff") || inputExt.equals("tif")
                || inputExt.equals("webp");
    }

    /**
     * 含透明通道图片转不透明格式：填充白色背景后输出。
     *
     * <p>先读取为 BufferedImage，绘制到白色背景上消除透明通道，
     * 再通过 Thumbnailator 应用缩放和质量参数写入目标格式。</p>
     */
    private static void compressWithAlphaFill(File inputFile, CompressConfig config,
                                              File outputFile, double quality) throws IOException {
        BufferedImage original = ImageIO.read(inputFile);
        if (original == null) {
            throw new IOException("无法读取图片: " + inputFile.getName());
        }

        // 计算输出尺寸
        int outW = original.getWidth();
        int outH = original.getHeight();
        if (config.getScaleMode() == CompressConfig.ScaleMode.BY_PERCENT) {
            double scale = config.getScalePercent() / 100.0;
            outW = (int) (outW * scale);
            outH = (int) (outH * scale);
        } else if (config.getScaleMode() == CompressConfig.ScaleMode.BY_MAX_SIZE) {
            double ratio = Math.min(
                    (double) config.getMaxWidth() / outW,
                    (double) config.getMaxHeight() / outH);
            if (ratio < 1.0) {
                outW = (int) (outW * ratio);
                outH = (int) (outH * ratio);
            }
        }

        // 创建不透明背景的 BufferedImage（TYPE_INT_RGB = 无 Alpha 通道）
        BufferedImage rgbImage = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = rgbImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, outW, outH);
        g2d.drawImage(original, 0, 0, outW, outH, null);
        g2d.dispose();
        original.flush();

        // 输出为目标格式
        String formatExt = config.getOutputFormat().getExtension();
        Thumbnails.of(rgbImage)
                .size(outW, outH)
                .outputQuality(quality)
                .outputFormat(formatExt)
                .toFile(outputFile);

        rgbImage.flush();
    }

    /**
     * 获取文件的小写扩展名。
     */
    private static String getExtension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return (dot < 0 || dot == name.length() - 1) ? "" : name.substring(dot + 1).toLowerCase();
    }

    /**
     * 读取图片尺寸（仅解析文件头，不加载像素数据）。
     *
     * <p>使用 ImageReader 读取元数据而非 ImageIO.read() 全量解码，
     * 避免：(1) 大图全量解码浪费内存 (2) CMYK JPEG 解码失败
     * (3) Windows 文件锁未释放导致 Thumbnailator 二次读取失败。</p>
     *
     * @param imageFile 图片文件
     * @return int[]{width, height}，失败时返回 {0, 0}
     */
    private static int[] readDimensions(File imageFile) {
        if (imageFile == null || !imageFile.isFile()) {
            return new int[]{0, 0};
        }
        ImageInputStream iis = null;
        try {
            iis = ImageIO.createImageInputStream(imageFile);
            if (iis == null) {
                return new int[]{0, 0};
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return new int[]{0, 0};
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                // 仅读取 SOF 标记中的宽高，不解码像素
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            return new int[]{0, 0};
        } finally {
            if (iis != null) {
                try {
                    iis.close();
                } catch (IOException ignored) {
                    // 静默关闭
                }
            }
        }
    }
}
