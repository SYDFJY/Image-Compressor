package com.nchu.imagecompress.util;

import net.coobird.thumbnailator.Thumbnails;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.ImageIcon;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

/**
 * 图片处理工具类。
 *
 * <p>封装基于 Thumbnailator 和 JDK ImageIO 的图片操作：
 * 读取尺寸、生成缩略图、创建 ImageIcon 等。所有方法均为无状态的静态方法。</p>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-08
 */
public final class ImageUtil {

    /** 缩略图默认最大宽度 */
    public static final int THUMBNAIL_MAX_WIDTH = 200;

    /** 缩略图默认最大高度 */
    public static final int THUMBNAIL_MAX_HEIGHT = 150;

    /** 预览图默认最大宽度 */
    public static final int PREVIEW_MAX_WIDTH = 1024;

    /** 预览图默认最大高度 */
    public static final int PREVIEW_MAX_HEIGHT = 768;

    private ImageUtil() {
        // 工具类禁止实例化
    }

    // ==================== 尺寸读取 ====================

    /**
     * 读取图片文件的像素尺寸（仅解析文件头，不加载像素数据）。
     *
     * <p>使用 ImageReader 读取元数据而非 ImageIO.read() 全量解码，
     * 避免 CMYK JPEG 等格式解码失败及 Windows 文件锁问题。</p>
     *
     * @param imageFile 图片文件
     * @return int[]{width, height}，失败时返回 {0, 0}
     */
    public static int[] readDimensions(File imageFile) {
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
                // 仅读取 SOF 标记中的宽高，不解码像素，CMYK 等格式也能正确获取尺寸
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

    /**
     * 读取图片宽度。
     */
    public static int readWidth(File imageFile) {
        return readDimensions(imageFile)[0];
    }

    /**
     * 读取图片高度。
     */
    public static int readHeight(File imageFile) {
        return readDimensions(imageFile)[1];
    }

    // ==================== 缩略图生成 ====================

    /**
     * 生成缩略图 BufferedImage（列表预览用，小尺寸）。
     *
     * @param imageFile 原始图片文件
     * @return 缩略图 BufferedImage，失败时返回 null
     */
    public static BufferedImage generateThumbnail(File imageFile) {
        return generateThumbnail(imageFile, THUMBNAIL_MAX_WIDTH, THUMBNAIL_MAX_HEIGHT);
    }

    /**
     * 生成指定尺寸的缩略图。
     *
     * @param imageFile 原始图片文件
     * @param maxWidth  最大宽度
     * @param maxHeight 最大高度
     * @return 缩略图 BufferedImage，失败时返回 null
     */
    public static BufferedImage generateThumbnail(File imageFile, int maxWidth, int maxHeight) {
        if (imageFile == null || !imageFile.isFile()) {
            return null;
        }
        try {
            return Thumbnails.of(imageFile)
                    .size(maxWidth, maxHeight)
                    .asBufferedImage();
        } catch (IOException e) {
            // 图片损坏或格式不支持时返回 null
            return null;
        }
    }

    /**
     * 生成缩略图并包装为 ImageIcon（直接用于 JLabel 显示）。
     *
     * @param imageFile 原始图片文件
     * @return 缩略图 ImageIcon，失败时返回 null
     */
    public static ImageIcon createThumbnailIcon(File imageFile) {
        BufferedImage thumb = generateThumbnail(imageFile);
        if (thumb == null) {
            return null;
        }
        return new ImageIcon(thumb);
    }

    // ==================== 预览图生成 ====================

    /**
     * 生成预览图（用于原图/效果图对比面板）。
     * <p>等比缩放至预览区域范围内，保持图片比例。</p>
     *
     * @param imageFile 图片文件
     * @return 缩放后的 BufferedImage，失败时返回 null
     */
    public static BufferedImage generatePreview(File imageFile) {
        return generatePreview(imageFile, PREVIEW_MAX_WIDTH, PREVIEW_MAX_HEIGHT);
    }

    /**
     * 生成指定尺寸限制的预览图。
     *
     * @param imageFile 图片文件
     * @param maxWidth  最大宽度
     * @param maxHeight 最大高度
     * @return 缩放后的 BufferedImage
     */
    public static BufferedImage generatePreview(File imageFile, int maxWidth, int maxHeight) {
        if (imageFile == null || !imageFile.isFile()) {
            return null;
        }
        try {
            return Thumbnails.of(imageFile)
                    .size(maxWidth, maxHeight)
                    .asBufferedImage();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 生成效果预览图（应用压缩参数后预览）。
     *
     * @param imageFile 原始图片
     * @param quality   压缩质量 (0.0-1.0)
     * @param maxWidth  最大宽度
     * @param maxHeight 最大高度
     * @return 模拟压缩效果后的 BufferedImage
     */
    public static BufferedImage generateEffectPreview(File imageFile, double quality,
                                                       int maxWidth, int maxHeight) {
        if (imageFile == null || !imageFile.isFile()) {
            return null;
        }
        try {
            return Thumbnails.of(imageFile)
                    .size(maxWidth, maxHeight)
                    .outputQuality(quality)
                    .asBufferedImage();
        } catch (IOException e) {
            return null;
        }
    }

    // ==================== ImageIcon 创建 ====================

    /**
     * 将 BufferedImage 安全地转换为 ImageIcon（可用于 JLabel）。
     *
     * @param image BufferedImage
     * @return ImageIcon，image 为 null 时返回 null
     */
    public static ImageIcon toImageIcon(BufferedImage image) {
        if (image == null) {
            return null;
        }
        return new ImageIcon(image);
    }

    /**
     * 加载图片文件并缩放为适合显示的 ImageIcon。
     *
     * @param imageFile 图片文件
     * @param maxWidth  最大宽度
     * @param maxHeight 最大高度
     * @return 缩放后的 ImageIcon
     */
    public static ImageIcon loadScaledIcon(File imageFile, int maxWidth, int maxHeight) {
        BufferedImage scaled = generatePreview(imageFile, maxWidth, maxHeight);
        return toImageIcon(scaled);
    }
}
