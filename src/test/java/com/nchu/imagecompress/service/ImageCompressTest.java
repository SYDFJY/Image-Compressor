package com.nchu.imagecompress.service;

import com.nchu.imagecompress.model.CompressConfig;
import com.nchu.imagecompress.model.CompressResult;
import com.nchu.imagecompress.model.ImageFileInfo;
import com.nchu.imagecompress.model.OutputFormat;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import static org.junit.Assert.*;

/**
 * 图片压缩核心测试 — CompressConfig + CompressService + ImageCompressUtil。
 *
 * <p>覆盖：质量压缩、格式转换、缩放、文件命名规则、目标大小二分搜索。</p>
 *
 * @author NCHU-Student
 * @since 2026-07-17
 */
public class ImageCompressTest {

    private File tempDir;
    private File testImage;

    /** v2.5: 从文件名创建轻量 ImageFileInfo，适配新的 buildOutputFileName 签名 */
    private static ImageFileInfo info(String fileName) {
        ImageFileInfo f = new ImageFileInfo();
        f.setFileName(fileName);
        return f;
    }

    @Before
    public void setUp() throws Exception {
        tempDir = new File(System.getProperty("java.io.tmpdir"), "nchu_test_" + System.currentTimeMillis());
        tempDir.mkdirs();

        // 创建 200×200 RGB 测试图像
        BufferedImage img = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(new Color(255, 100, 50));
        g2.fillRect(0, 0, 100, 100);
        g2.setColor(new Color(50, 100, 255));
        g2.fillRect(100, 100, 100, 100);
        g2.dispose();

        testImage = new File(tempDir, "test_input.jpg");
        ImageIO.write(img, "JPEG", testImage);
    }

    @After
    public void tearDown() {
        if (tempDir.exists()) {
            File[] files = tempDir.listFiles();
            if (files != null) for (File f : files) f.delete();
            tempDir.delete();
        }
    }

    // ==================== CompressConfig 测试 ====================

    @Test
    public void testConfigDefaults() {
        CompressConfig config = new CompressConfig();
        assertEquals(80, config.getQuality());
        assertEquals(CompressConfig.ScaleMode.NONE, config.getScaleMode());
        assertEquals(OutputFormat.ORIGINAL, config.getOutputFormat());
        assertEquals(0, config.getTargetSizeKB());
        assertFalse(config.isOverwrite());
    }

    @Test
    public void testConfigQualityBoundaries() {
        CompressConfig config = new CompressConfig();
        config.setQuality(-10);
        assertEquals(0, config.getQuality());
        config.setQuality(150);
        assertEquals(100, config.getQuality());
        config.setQuality(50);
        assertEquals(50, config.getQuality());
    }

    @Test
    public void testConfigTargetSizeKB() {
        CompressConfig config = new CompressConfig();
        assertEquals(0, config.getTargetSizeKB());
        config.setTargetSizeKB(500);
        assertEquals(500, config.getTargetSizeKB());
        config.setTargetSizeKB(-1);
        assertEquals(0, config.getTargetSizeKB());
    }

    @Test
    public void testConfigScalePercentBoundaries() {
        CompressConfig config = new CompressConfig();
        config.setScalePercent(0);
        assertEquals(1, config.getScalePercent());
        config.setScalePercent(200);
        assertEquals(100, config.getScalePercent());
    }

    @Test
    public void testIsEffectivelyNoOp() {
        // 质量100 + 不缩放 + 原格式 = 无意义压缩
        CompressConfig config = new CompressConfig();
        config.setQuality(100);
        config.setScaleMode(CompressConfig.ScaleMode.NONE);
        config.setOutputFormat(OutputFormat.ORIGINAL);
        assertTrue(config.isEffectivelyNoOp());

        // 质量80 → 有意义
        config.setQuality(80);
        assertFalse(config.isEffectivelyNoOp());

        // 质量100 + JPEG格式 → 有意义
        config.setQuality(100);
        config.setOutputFormat(OutputFormat.JPEG);
        assertFalse(config.isEffectivelyNoOp());

        // 质量100 + 缩放 → 有意义
        config.setOutputFormat(OutputFormat.ORIGINAL);
        config.setScaleMode(CompressConfig.ScaleMode.BY_PERCENT);
        config.setScalePercent(50);
        assertFalse(config.isEffectivelyNoOp());
    }

    // ==================== CompressService 文件命名测试 ====================

    @Test
    public void testBuildOutputFileNameAddSuffix() {
        CompressService service = new CompressService();
        CompressConfig config = new CompressConfig();
        config.setNamingRule(CompressConfig.NamingRule.ADD_SUFFIX);
        config.setSuffix("_mini");
        assertEquals("photo_mini.jpg", service.buildOutputFileName(info("photo.jpg"), config));
    }

    @Test
    public void testBuildOutputFileNameAddPrefix() {
        CompressService service = new CompressService();
        CompressConfig config = new CompressConfig();
        config.setNamingRule(CompressConfig.NamingRule.ADD_PREFIX);
        config.setPrefix("thumb_");
        assertEquals("thumb_photo.jpg", service.buildOutputFileName(info("photo.jpg"), config));
    }

    @Test
    public void testBuildOutputFileNameKeepOriginal() {
        CompressService service = new CompressService();
        CompressConfig config = new CompressConfig();
        config.setNamingRule(CompressConfig.NamingRule.KEEP_ORIGINAL);
        assertEquals("photo.jpg", service.buildOutputFileName(info("photo.jpg"), config));
    }

    @Test
    public void testBuildOutputFileNameCustom() {
        CompressService service = new CompressService();
        CompressConfig config = new CompressConfig();
        config.setNamingRule(CompressConfig.NamingRule.CUSTOM);
        config.setCustomName("my_photo");
        // customName + 原扩展名
        assertEquals("my_photo.jpg", service.buildOutputFileName(info("photo.jpg"), config));
    }

    @Test
    public void testBuildOutputFileNameFormatConversion() {
        CompressService service = new CompressService();
        CompressConfig config = new CompressConfig();
        config.setOutputFormat(OutputFormat.PNG);
        assertEquals("photo_compressed.png", service.buildOutputFileName(info("photo.jpg"), config));
    }

    @Test
    public void testBuildOutputFileNameIllegalChars() {
        CompressService service = new CompressService();
        CompressConfig config = new CompressConfig();
        config.setNamingRule(CompressConfig.NamingRule.CUSTOM);
        config.setCustomName("test:file<name>");
        // 非法字符被过滤
        assertEquals("testfilename.jpg", service.buildOutputFileName(info("photo.jpg"), config));
    }

    // ==================== ImageCompressUtil 压缩测试 ====================

    @Test
    public void testCompressJpegToJpeg() throws Exception {
        ImageFileInfo info = new ImageFileInfo(testImage);
        CompressConfig config = new CompressConfig();
        config.setQuality(50);
        config.setOutputPath(tempDir.getAbsolutePath());

        CompressService service = new CompressService();
        CompressResult result = service.compress(info, config);
        assertTrue("JPEG→JPEG 压缩应成功: " + result.getErrorMessage(), result.isSuccess());
        assertTrue(result.getOutputSize() > 0);
        assertTrue(result.getOutputSize() < info.getOriginalSize());
    }

    @Test
    public void testCompressJpegToPng() throws Exception {
        ImageFileInfo info = new ImageFileInfo(testImage);
        CompressConfig config = new CompressConfig();
        config.setQuality(80);
        config.setOutputFormat(OutputFormat.PNG);
        config.setOutputPath(tempDir.getAbsolutePath());

        CompressService service = new CompressService();
        CompressResult result = service.compress(info, config);
        assertTrue("JPEG→PNG 转换应成功: " + result.getErrorMessage(), result.isSuccess());
        assertTrue(result.getOutputSize() > 0);
    }

    @Test
    public void testCompressWithScalePercent() throws Exception {
        ImageFileInfo info = new ImageFileInfo(testImage);
        CompressConfig config = new CompressConfig();
        config.setQuality(80);
        config.setScaleMode(CompressConfig.ScaleMode.BY_PERCENT);
        config.setScalePercent(50);
        config.setOutputPath(tempDir.getAbsolutePath());

        CompressService service = new CompressService();
        CompressResult result = service.compress(info, config);
        assertTrue("缩放压缩应成功: " + result.getErrorMessage(), result.isSuccess());
    }

    @Test
    public void testCompressTargetSizeEnabled() throws Exception {
        ImageFileInfo info = new ImageFileInfo(testImage);
        CompressConfig config = new CompressConfig();
        config.setTargetSizeKB(100); // 目标 100KB
        config.setOutputPath(tempDir.getAbsolutePath());

        CompressService service = new CompressService();
        CompressResult result = service.compress(info, config);
        assertTrue("目标大小压缩应成功: " + result.getErrorMessage(), result.isSuccess());
        assertTrue(result.getOutputSize() > 0);
        // 质量应该被二分搜索调整过
        assertTrue("二分搜索后质量应在合理范围内: " + config.getQuality(),
                config.getQuality() >= 5 && config.getQuality() <= 100);
    }

    @Test
    public void testCompressQuality100ProducesLargerFile() throws Exception {
        ImageFileInfo info = new ImageFileInfo(testImage);
        CompressConfig config = new CompressConfig();
        config.setQuality(100);
        config.setOutputFormat(OutputFormat.PNG);
        config.setOutputPath(tempDir.getAbsolutePath());

        CompressService service = new CompressService();
        CompressResult result = service.compress(info, config);
        assertTrue("PNG 无损应成功", result.isSuccess());
        // PNG 无损可能比 JPEG 源文件大
        assertTrue(result.getOutputSize() > 0);
    }

    @Test
    public void testCompressInvalidFileReturnsFail() {
        ImageFileInfo info = new ImageFileInfo(new File(tempDir, "nonexistent.jpg"));
        CompressConfig config = new CompressConfig();

        CompressService service = new CompressService();
        CompressResult result = service.compress(info, config);
        assertFalse("不存在的文件应返回失败", result.isSuccess());
        assertNotNull(result.getErrorMessage());
    }

    // ==================== FileUtil 测试 ====================

    @Test
    public void testFormatFileSize() {
        assertEquals("0 B", com.nchu.imagecompress.util.FileUtil.formatFileSize(0));
        assertEquals("512 B", com.nchu.imagecompress.util.FileUtil.formatFileSize(512));
        assertEquals("1.0 KB", com.nchu.imagecompress.util.FileUtil.formatFileSize(1024));
        assertEquals("1.5 MB", com.nchu.imagecompress.util.FileUtil.formatFileSize(1572864));
    }

    @Test
    public void testGetExtension() {
        assertEquals("jpg", com.nchu.imagecompress.util.FileUtil.getExtension("photo.jpg"));
        assertEquals("png", com.nchu.imagecompress.util.FileUtil.getExtension("image.PNG"));
        assertEquals("", com.nchu.imagecompress.util.FileUtil.getExtension("noext"));
        assertEquals("", com.nchu.imagecompress.util.FileUtil.getExtension((String) null));
    }

    @Test
    public void testGenerateUniqueFilename() {
        // 文件不存在 → 原名
        String name = com.nchu.imagecompress.util.FileUtil.generateUniqueFilename(tempDir.getAbsolutePath(), "test.jpg");
        assertEquals("test.jpg", name);

        // 创建同名文件后 → 自动编号
        try { new File(tempDir, "test.jpg").createNewFile(); } catch (Exception ignored) {}
        String name2 = com.nchu.imagecompress.util.FileUtil.generateUniqueFilename(tempDir.getAbsolutePath(), "test.jpg");
        assertEquals("test_1.jpg", name2);
    }
}
