package com.nchu.imagecompress.util;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 文件操作工具类。
 *
 * <p>提供文件格式校验、扩展名提取、文件大小格式化、目录创建、
 * 唯一文件名生成等通用文件操作。所有方法均为静态、无状态方法。</p>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-08
 */
public final class FileUtil {

    private FileUtil() {
        // 工具类禁止实例化
    }

    // ==================== 支持的图片格式 ====================

    /** 支持的图片文件扩展名集合（小写） */
    private static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "bmp", "gif", "tiff", "tif", "ico", "webp"
    ));

    /** 不支持但常见的格式（会给出明确提示） */
    private static final Set<String> UNSUPPORTED_EXTENSIONS = new HashSet<>(Arrays.asList(
            "psd", "ai", "eps", "svg", "raw", "cr2", "nef", "arw", "dng"
    ));

    // ==================== 格式校验 ====================

    /**
     * 判断文件是否为支持的图片格式。
     *
     * @param file 待检测文件
     * @return true 表示格式支持
     */
    public static boolean isSupportedImage(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        String ext = getExtension(file);
        return SUPPORTED_EXTENSIONS.contains(ext);
    }

    /**
     * 判断文件是否为图片文件（含不支持但可识别的格式）。
     *
     * @param file 待检测文件
     * @return true 表示是图片文件
     */
    public static boolean isImageFile(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        String ext = getExtension(file);
        return SUPPORTED_EXTENSIONS.contains(ext) || UNSUPPORTED_EXTENSIONS.contains(ext);
    }

    /**
     * 判断是否为不支持但可识别的格式（需要明确告知用户）。
     */
    public static boolean isUnsupportedFormat(File file) {
        if (file == null) return false;
        return UNSUPPORTED_EXTENSIONS.contains(getExtension(file));
    }

    /**
     * 获取支持的文件扩展名列表（用于 JFileChooser 文件过滤器）。
     *
     * @return 扩展名描述数组
     */
    public static String[] getSupportedExtensions() {
        return SUPPORTED_EXTENSIONS.toArray(new String[0]);
    }

    // ==================== 扩展名处理 ====================

    /**
     * 获取文件扩展名（小写，不含点号）。
     *
     * @param file 文件对象
     * @return 小写扩展名，无扩展名时返回空字符串
     */
    public static String getExtension(File file) {
        if (file == null) return "";
        return getExtension(file.getName());
    }

    /**
     * 从文件名中提取扩展名（小写，不含点号）。
     *
     * @param fileName 文件名
     * @return 小写扩展名，无扩展名时返回空字符串
     */
    public static String getExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase();
    }

    /**
     * 获取不带扩展名的文件名。
     *
     * @param fileName 完整文件名
     * @return 无扩展名的文件名
     */
    public static String getNameWithoutExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0) {
            return fileName;
        }
        return fileName.substring(0, dotIndex);
    }

    // ==================== 文件大小格式化 ====================

    /**
     * 将字节数格式化为人类可读的字符串。
     *
     * @param bytes 字节数
     * @return 格式化后的字符串（如 "1.5 MB"）
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 0) {
            return "未知";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.1f MB", mb);
        }
        return String.format("%.2f GB", mb / 1024.0);
    }

    // ==================== 目录操作 ====================

    /**
     * 确保目录存在，不存在则递归创建。
     *
     * @param dirPath 目录路径
     * @return true 表示创建成功或目录已存在
     * @throws SecurityException 无权限创建时抛出
     */
    public static boolean ensureDirectoryExists(String dirPath) {
        if (dirPath == null || dirPath.isEmpty()) {
            return false;
        }
        File dir = new File(dirPath);
        if (dir.exists()) {
            return dir.isDirectory();
        }
        return dir.mkdirs();
    }

    /**
     * 检查目录是否可写。
     *
     * @param dirPath 目录路径
     * @return true 表示目录存在且可写
     */
    public static boolean isDirectoryWritable(String dirPath) {
        if (dirPath == null || dirPath.isEmpty()) {
            return false;
        }
        File dir = new File(dirPath);
        return dir.isDirectory() && dir.canWrite();
    }

    // ==================== 唯一文件名生成 ====================

    /**
     * 生成不重复的输出文件名。
     * <p>如果目标文件已存在，自动添加递增编号（如 "photo_1.jpg", "photo_2.jpg"）。
     *
     * @param outputDir 输出目录
     * @param baseName  基础文件名（不含路径）
     * @return 不重复的文件名
     */
    public static String generateUniqueFilename(String outputDir, String baseName) {
        File outputFile = new File(outputDir, baseName);
        if (!outputFile.exists()) {
            return baseName;
        }

        String nameWithoutExt = getNameWithoutExtension(baseName);
        String ext = getExtension(baseName);
        String extDot = ext.isEmpty() ? "" : "." + ext;

        int counter = 1;
        while (true) {
            String newName = nameWithoutExt + "_" + counter + extDot;
            File candidate = new File(outputDir, newName);
            if (!candidate.exists()) {
                return newName;
            }
            counter++;
            // 安全上限：防止死循环
            if (counter > 9999) {
                // 超过上限时使用时间戳
                return nameWithoutExt + "_" + System.currentTimeMillis() + extDot;
            }
        }
    }

    /**
     * 获取用户主目录下的默认输出路径。
     *
     * @return 默认输出目录路径
     */
    public static String getDefaultOutputDir() {
        String homeDir = System.getProperty("user.home");
        File defaultDir = new File(homeDir, "NCHUImageCompressor" + File.separator + "output");
        if (!defaultDir.exists()) {
            defaultDir.mkdirs();
        }
        return defaultDir.getAbsolutePath();
    }
}
