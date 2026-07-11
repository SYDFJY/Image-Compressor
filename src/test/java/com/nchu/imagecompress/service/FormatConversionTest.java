package com.nchu.imagecompress.service;

import com.nchu.imagecompress.model.CompressConfig;
import com.nchu.imagecompress.model.CompressResult;
import com.nchu.imagecompress.model.OutputFormat;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 格式转换诊断测试。
 *
 * <p>系统性测试所有输入格式 → 输出格式的转换是否正常，
 * 输出完整的通过/失败矩阵，用于定位格式转换的 bug。</p>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-08
 */
public class FormatConversionTest {

    /** 测试输入格式列表 */
    private static final List<String> INPUT_FORMATS = Arrays.asList(
            "jpg", "png", "bmp", "gif", "tiff", "ico"
    );

    /** 测试输出格式列表 */
    private static final List<OutputFormat> OUTPUT_FORMATS = Arrays.asList(
            OutputFormat.ORIGINAL,
            OutputFormat.JPEG,
            OutputFormat.PNG,
            OutputFormat.BMP
    );

    /** 临时测试目录 */
    private static final File TEST_DIR = new File(
            System.getProperty("java.io.tmpdir"), "nchu-format-test");

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║   格式转换诊断测试 — 全组合验证                  ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();

        // 准备工作目录
        if (TEST_DIR.exists()) {
            deleteRecursively(TEST_DIR);
        }
        TEST_DIR.mkdirs();

        // 构建测试文件（每种格式一个样本）
        Map<String, File> testFiles = createTestFiles();

        // 结果矩阵：inputFormat → outputFormat → "OK" / "FAIL: reason"
        Map<String, Map<String, String>> matrix = new LinkedHashMap<>();

        int total = 0, passed = 0, failed = 0;

        for (String inputFmt : INPUT_FORMATS) {
            File inputFile = testFiles.get(inputFmt);
            Map<String, String> row = new LinkedHashMap<>();
            matrix.put(inputFmt, row);

            for (OutputFormat outputFmt : OUTPUT_FORMATS) {
                total++;
                String result;
                long start = System.currentTimeMillis();

                if (inputFile == null) {
                    result = "⏭ 无测试文件";
                } else {
                    CompressConfig config = new CompressConfig();
                    config.setQuality(80);
                    config.setScaleMode(CompressConfig.ScaleMode.NONE);
                    config.setOutputFormat(outputFmt);
                    config.setOutputPath(TEST_DIR.getAbsolutePath());
                    config.setNamingRule(CompressConfig.NamingRule.ADD_SUFFIX);
                    config.setSuffix("_" + outputFmt.getExtension());
                    config.setOverwrite(true);

                    CompressService service = new CompressService();
                    // 手动创建 ImageFileInfo
                    com.nchu.imagecompress.model.ImageFileInfo info =
                            new com.nchu.imagecompress.model.ImageFileInfo(inputFile);
                    CompressResult compressResult = service.compress(info, config);

                    long elapsed = System.currentTimeMillis() - start;
                    if (compressResult.isSuccess()) {
                        result = "✅ OK (" + elapsed + "ms)";
                        passed++;
                    } else {
                        result = "❌ " + compressResult.getErrorMessage();
                        failed++;
                    }
                }
                row.put(outputFmt.getDisplayName(), result);
            }
        }

        // 打印矩阵
        System.out.println("测试文件信息:");
        for (Map.Entry<String, File> entry : testFiles.entrySet()) {
            File f = entry.getValue();
            String status = (f != null && f.exists()) ? "✓" : "✗ (无)";
            String name = (f != null) ? f.getName() : "N/A";
            System.out.printf("  %s: %s %s%n", entry.getKey(), status, name);
        }
        System.out.println();

        System.out.println("格式转换矩阵 (输入 → 输出):");
        System.out.println();

        // 表头
        System.out.printf("%-6s", "输入↓");
        for (OutputFormat fmt : OUTPUT_FORMATS) {
            System.out.printf("  %-12s", fmt.getDisplayName());
        }
        System.out.println();
        System.out.print("──────");
        for (int i = 0; i < OUTPUT_FORMATS.size(); i++) System.out.print("──────────────");
        System.out.println();

        // 数据行
        for (String inputFmt : INPUT_FORMATS) {
            System.out.printf("%-6s", inputFmt.toUpperCase());
            Map<String, String> row = matrix.get(inputFmt);
            for (OutputFormat outputFmt : OUTPUT_FORMATS) {
                String cell = row.get(outputFmt.getDisplayName());
                String shortCell;
                if (cell.startsWith("✅")) {
                    shortCell = "✅ OK";
                } else if (cell.startsWith("❌")) {
                    // 提取错误关键词
                    shortCell = "❌ FAIL";
                } else {
                    shortCell = cell;
                }
                System.out.printf("  %-12s", shortCell);
            }
            System.out.println();
        }

        // 失败详情
        System.out.println();
        System.out.println("════ 失败详情 ════");
        boolean hasFailures = false;
        for (String inputFmt : INPUT_FORMATS) {
            Map<String, String> row = matrix.get(inputFmt);
            for (OutputFormat outputFmt : OUTPUT_FORMATS) {
                String cell = row.get(outputFmt.getDisplayName());
                if (cell.startsWith("❌")) {
                    hasFailures = true;
                    System.out.printf("  %s → %s: %s%n",
                            inputFmt.toUpperCase(),
                            outputFmt.getDisplayName(),
                            cell.substring(2)); // 去掉 ❌
                }
            }
        }
        if (!hasFailures) {
            System.out.println("  🎉 全部通过!");
        }

        // 统计
        System.out.println();
        System.out.println("════ 统计 ════");
        System.out.printf("  总计: %d, 通过: %d, 失败: %d (%.0f%%)%n",
                total, passed, failed, total > 0 ? 100.0 * passed / total : 0);

        // 检查 JDK ImageIO 支持的格式
        System.out.println();
        System.out.println("════ JDK ImageIO 能力 ════");
        System.out.println("  Reader 格式: " + Arrays.toString(ImageIO.getReaderFormatNames()));
        System.out.println("  Writer 格式: " + Arrays.toString(ImageIO.getWriterFormatNames()));

        // 清理
        deleteRecursively(TEST_DIR);

        // 返回状态码
        System.exit(failed > 0 ? 1 : 0);
    }

    /**
     * 创建每种测试格式的样本图片文件。
     */
    private static Map<String, File> createTestFiles() {
        Map<String, File> files = new LinkedHashMap<>();

        // 创建一个 100×100 的 RGB 测试图像
        BufferedImage testImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = testImage.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 50, 50);
        g.setColor(Color.BLUE);
        g.fillRect(50, 0, 50, 50);
        g.setColor(Color.GREEN);
        g.fillRect(0, 50, 50, 50);
        g.setColor(Color.YELLOW);
        g.fillRect(50, 50, 50, 50);
        g.dispose();

        // 创建带透明通道的测试图像
        BufferedImage testImageAlpha = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D ga = testImageAlpha.createGraphics();
        ga.setColor(new Color(255, 0, 0, 128));
        ga.fillRect(0, 0, 50, 50);
        ga.setColor(new Color(0, 0, 255, 200));
        ga.fillRect(50, 0, 50, 50);
        ga.setColor(new Color(0, 255, 0, 80));
        ga.fillRect(0, 50, 50, 50);
        ga.setColor(new Color(255, 255, 0, 255));
        ga.fillRect(50, 50, 50, 50);
        ga.dispose();

        // 保存为各格式
        // JPG (不支持透明，用 RGB 图)
        files.put("jpg", saveImage(testImage, "test_rgb.jpg", "JPEG"));

        // PNG (保存两个版本：RGB 和 ARGB)
        files.put("png", saveImage(testImageAlpha, "test_argb.png", "PNG"));

        // BMP
        files.put("bmp", saveImage(testImage, "test_rgb.bmp", "BMP"));

        // GIF (ImageIO.write 支持 GIF)
        files.put("gif", saveImage(testImage, "test_rgb.gif", "GIF"));

        // TIFF — JDK 8 不支持
        File tiffFile = new File(TEST_DIR, "test_rgb.tiff");
        try {
            // TIFF 在 JDK 8 默认不支持
            boolean ok = ImageIO.write(testImage, "TIFF", tiffFile);
            if (ok && tiffFile.exists() && tiffFile.length() > 0) {
                files.put("tiff", tiffFile);
            } else {
                System.out.println("  ⚠ TIFF 测试文件创建失败（JDK 8 无 TIFF Writer），跳过 TIFF 输入测试");
                files.put("tiff", null);
            }
        } catch (Exception e) {
            System.out.println("  ⚠ TIFF 测试文件创建失败: " + e.getMessage());
            files.put("tiff", null);
        }

        // ICO — 不是标准 ImageIO 格式
        File icoFile = new File(TEST_DIR, "test_rgb.ico");
        try {
            boolean ok = ImageIO.write(testImage, "ICO", icoFile);
            if (ok && icoFile.exists() && icoFile.length() > 0) {
                files.put("ico", icoFile);
            } else {
                System.out.println("  ⚠ ICO 测试文件创建失败（JDK 8 无 ICO Writer），跳过 ICO 输入测试");
                files.put("ico", null);
            }
        } catch (Exception e) {
            System.out.println("  ⚠ ICO 测试文件创建失败: " + e.getMessage());
            files.put("ico", null);
        }

        return files;
    }

    private static File saveImage(BufferedImage image, String name, String format) {
        File file = new File(TEST_DIR, name);
        try {
            boolean ok = ImageIO.write(image, format, file);
            if (!ok || file.length() == 0) {
                System.out.println("  ⚠ " + format + " 写入失败: ImageIO.write returned false");
                return null;
            }
            return file;
        } catch (IOException e) {
            System.out.println("  ⚠ " + format + " 写入失败: " + e.getMessage());
            return null;
        }
    }

    private static void deleteRecursively(File dir) {
        if (!dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteRecursively(f);
                else f.delete();
            }
        }
        dir.delete();
    }
}
