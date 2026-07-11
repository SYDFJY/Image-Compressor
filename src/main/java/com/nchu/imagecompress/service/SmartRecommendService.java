package com.nchu.imagecompress.service;

import com.nchu.imagecompress.model.CompressConfig;
import com.nchu.imagecompress.model.ImageFileInfo;
import com.nchu.imagecompress.model.OutputFormat;

import java.util.List;

/**
 * 智能参数推荐服务。
 *
 * <p>根据图片文件的尺寸、大小、宽高比等特征，自动识别图片类型
 * 并推荐最适合的压缩参数（质量、格式）。</p>
 *
 * <h3>推荐策略</h3>
 * <table>
 *   <tr><th>图片类型</th><th>判定条件</th><th>推荐质量</th><th>推荐格式</th></tr>
 *   <tr><td>图标/小图</td><td>宽度 &lt; 300 且 高度 &lt; 300</td><td>90</td><td>PNG（无损）</td></tr>
 *   <tr><td>截图</td><td>匹配常见屏幕宽高比或宽度 &ge; 1920</td><td>75</td><td>JPEG</td></tr>
 *   <tr><td>照片</td><td>大尺寸 + 高分辨率 + 4:3/3:2 比例</td><td>70</td><td>JPEG</td></tr>
 *   <tr><td>默认</td><td>无法判定时</td><td>80</td><td>保持原格式</td></tr>
 * </table>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-08
 */
public class SmartRecommendService {

    /** 图标/小图判定阈值（像素） */
    private static final int ICON_MAX_SIZE = 300;

    /** 截图宽度下限 */
    private static final int SCREENSHOT_MIN_WIDTH = 1280;

    /** 照片最小尺寸 */
    private static final int PHOTO_MIN_DIMENSION = 800;

    /** 截图常见宽高比（误差 ±3%） */
    private static final double[] SCREEN_RATIOS = {
            16.0 / 9.0,   // 1920×1080, 2560×1440
            16.0 / 10.0,  // 1920×1200, 2560×1600
            4.0 / 3.0,    // 1024×768, 1600×1200
    };

    /** 照片常见宽高比（误差 ±3%） */
    private static final double[] PHOTO_RATIOS = {
            3.0 / 2.0,    // DSLR 标准
            4.0 / 3.0,    // 手机/卡片机
            1.0 / 1.0,    // 正方形（Instagram）
    };

    // ==================== 图片类型识别 ====================

    /**
     * 图片类型枚举。
     */
    public enum ImageType {
        ICON("图标/小图"),
        SCREENSHOT("截图"),
        PHOTO("照片"),
        UNKNOWN("未知");

        private final String displayName;
        ImageType(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    /**
     * 识别单张图片的类型。
     *
     * @param info 图片文件信息
     * @return 识别的图片类型
     */
    public ImageType classifyImage(ImageFileInfo info) {
        if (info == null) return ImageType.UNKNOWN;

        int width = info.getWidth();
        int height = info.getHeight();
        long fileSize = info.getOriginalSize();

        if (width <= 0 || height <= 0) return ImageType.UNKNOWN;

        // ① 图标/小图判定
        if (width < ICON_MAX_SIZE && height < ICON_MAX_SIZE) {
            return ImageType.ICON;
        }

        // ② 截图判定
        double ratio = (double) width / height;
        if (matchesAnyRatio(ratio, SCREEN_RATIOS, 0.05)) {
            // 宽高比匹配 + 足够大
            if (width >= SCREENSHOT_MIN_WIDTH || height >= SCREENSHOT_MIN_WIDTH) {
                return ImageType.SCREENSHOT;
            }
        }

        // 超宽屏（截图常见于拼接屏）
        if (ratio >= 2.0 && width >= SCREENSHOT_MIN_WIDTH) {
            return ImageType.SCREENSHOT;
        }

        // ③ 照片判定：大尺寸 + 照片比例 + 合理的文件大小
        if (width >= PHOTO_MIN_DIMENSION && height >= PHOTO_MIN_DIMENSION) {
            if (matchesAnyRatio(ratio, PHOTO_RATIOS, 0.05)) {
                // 照片通常文件较大（> 100KB）
                if (fileSize > 100 * 1024) {
                    return ImageType.PHOTO;
                }
            }
            // 尺寸够大且比例不匹配截图 → 也按照片处理
            if (!matchesAnyRatio(ratio, SCREEN_RATIOS, 0.05)) {
                return ImageType.PHOTO;
            }
        }

        return ImageType.UNKNOWN;
    }

    // ==================== 质量推荐 ====================

    /**
     * 根据图片类型推荐压缩质量。
     *
     * @param type 图片类型
     * @return 推荐的质量值 (0-100)
     */
    public int recommendQuality(ImageType type) {
        switch (type) {
            case ICON:       return 90;
            case SCREENSHOT: return 75;
            case PHOTO:      return 70;
            case UNKNOWN:
            default:         return 80;
        }
    }

    /**
     * 根据图片类型推荐输出格式。
     *
     * @param type 图片类型
     * @return 推荐格式
     */
    public OutputFormat recommendFormat(ImageType type) {
        switch (type) {
            case ICON:       return OutputFormat.PNG;      // 图标用无损 PNG
            case SCREENSHOT: return OutputFormat.JPEG;     // 截图用 JPEG 压缩率高
            case PHOTO:      return OutputFormat.JPEG;     // 照片用 JPEG
            case UNKNOWN:
            default:         return OutputFormat.ORIGINAL;  // 保持原格式
        }
    }

    /**
     * 一键推荐：分析图片并返回推荐的 CompressConfig。
     *
     * @param info 图片文件信息
     * @return 预填充推荐参数的 CompressConfig
     */
    public CompressConfig recommend(ImageFileInfo info) {
        ImageType type = classifyImage(info);
        CompressConfig config = new CompressConfig();
        config.setQuality(recommendQuality(type));
        config.setOutputFormat(recommendFormat(type));
        config.setSmartRecommend(true);
        config.setImageTypeHint(type.name());
        return config;
    }

    /**
     * 批量推荐：对列表中所有图片进行综合分析，返回适用于大多数图片的参数。
     *
     * @param fileList 文件列表
     * @return 综合分析后的推荐配置
     */
    public CompressConfig recommendBatch(List<ImageFileInfo> fileList) {
        if (fileList == null || fileList.isEmpty()) {
            return CompressConfig.getDefault();
        }

        // 统计各类型数量
        int iconCount = 0, screenshotCount = 0, photoCount = 0, unknownCount = 0;
        double totalQuality = 0;
        int classifiedCount = 0;

        for (ImageFileInfo info : fileList) {
            ImageType type = classifyImage(info);
            switch (type) {
                case ICON:       iconCount++;       break;
                case SCREENSHOT: screenshotCount++;  break;
                case PHOTO:      photoCount++;       break;
                default:         unknownCount++;
            }
            if (type != ImageType.UNKNOWN) {
                totalQuality += recommendQuality(type);
                classifiedCount++;
            }
        }

        int total = fileList.size();

        // 多数投票决定
        CompressConfig config = new CompressConfig();

        // 质量取加权平均
        if (classifiedCount > 0) {
            config.setQuality((int) Math.round(totalQuality / classifiedCount));
        } else {
            config.setQuality(80);
        }

        // 格式按多数类型决定
        if (photoCount + screenshotCount > iconCount) {
            config.setOutputFormat(OutputFormat.JPEG);
        } else if (iconCount > photoCount + screenshotCount) {
            config.setOutputFormat(OutputFormat.PNG);
        } else {
            config.setOutputFormat(OutputFormat.ORIGINAL);
        }

        config.setSmartRecommend(true);
        config.setImageTypeHint(String.format(
                "图标:%d 截图:%d 照片:%d 未知:%d (共%d)",
                iconCount, screenshotCount, photoCount, unknownCount, total));

        return config;
    }

    // ==================== 辅助方法 ====================

    /**
     * 判断宽高比是否匹配预设值之一（允许误差）。
     */
    private static boolean matchesAnyRatio(double actual, double[] candidates, double tolerance) {
        for (double candidate : candidates) {
            if (Math.abs(actual - candidate) < tolerance) {
                return true;
            }
        }
        return false;
    }
}
