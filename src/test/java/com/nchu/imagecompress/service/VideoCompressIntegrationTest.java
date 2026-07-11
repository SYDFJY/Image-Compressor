package com.nchu.imagecompress.service;

import com.nchu.imagecompress.model.VideoCompressConfig;
import com.nchu.imagecompress.model.VideoFileInfo;
import com.nchu.imagecompress.util.VideoUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

/**
 * 视频压缩端到端集成测试。
 *
 * <p>需要系统 PATH 上有 ffmpeg + ffprobe（FFmpeg 4.0+）。
 * 测试会自动创建临时视频、解析元数据、压缩、验证输出。
 * 如果 FFmpeg 不可用，所有测试会被自动跳过。</p>
 *
 * @author NCHU-Student
 * @version 2.0.0
 * @since 2026-07-11
 */
public class VideoCompressIntegrationTest {

    private static final File TEST_DIR = new File(System.getProperty("java.io.tmpdir"),
            "nchu-video-integration-test");
    private static File testVideoFile;
    private static boolean ffmpegAvailable;
    private static String ffmpegBinPath;

    @BeforeClass
    public static void setUpClass() throws Exception {
        // 读取系统属性指定的 FFmpeg 路径
        ffmpegBinPath = System.getProperty("ffmpeg.bin.path", "");
        // 检测 FFmpeg 可用性（清除缓存强制重新检测）
        VideoUtil.resetFfmpegCheck();
        ffmpegAvailable = VideoUtil.checkFfmpegAvailable();

        if (!ffmpegAvailable) {
            System.out.println("[SKIP] FFmpeg 不可用，跳过所有集成测试");
            return;
        }

        // 创建临时测试目录
        if (TEST_DIR.exists()) {
            deleteRecursively(TEST_DIR);
        }
        TEST_DIR.mkdirs();

        // 生成一个 5 秒的测试视频（颜色测试图案 + 正弦波音频）
        testVideoFile = new File(TEST_DIR, "test_input.mp4");
        generateTestVideo(testVideoFile);

        System.out.println("[SETUP] 测试视频已生成: " + testVideoFile.getAbsolutePath()
                + " (" + testVideoFile.length() + " bytes)");
    }

    @AfterClass
    public static void tearDownClass() {
        if (TEST_DIR.exists()) {
            deleteRecursively(TEST_DIR);
        }
    }

    /**
     * 使用 FFmpeg 生成 5 秒测试视频。
     */
    private static String resolveFfmpeg(String command) {
        if (!ffmpegBinPath.isEmpty()) {
            return ffmpegBinPath + File.separator + command;
        }
        return command;
    }

    private static void generateTestVideo(File output) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                resolveFfmpeg("ffmpeg"),
                "-f", "lavfi",
                "-i", "testsrc=duration=5:size=640x480:rate=30",
                "-f", "lavfi",
                "-i", "sine=frequency=440:duration=5",
                "-c:v", "libx264",
                "-c:a", "aac",
                "-shortest",
                "-y",
                output.getAbsolutePath()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("无法生成测试视频，退出码: " + exitCode);
        }
    }

    // ==================== 测试 1：元数据解析 ====================

    @Test
    public void testParseMetadata_Duration() {
        assumeFfmpegAvailable();
        VideoFileInfo info = new VideoFileInfo(testVideoFile);
        boolean ok = VideoUtil.parseMetadata(info);

        assertTrue("Metadata parsing should succeed", ok);
        assertTrue("Duration should be > 0", info.getDurationSeconds() > 0);
        assertTrue("Duration should be ~5 seconds",
                Math.abs(info.getDurationSeconds() - 5.0) < 1.0);
    }

    @Test
    public void testParseMetadata_Resolution() {
        assumeFfmpegAvailable();
        VideoFileInfo info = new VideoFileInfo(testVideoFile);
        VideoUtil.parseMetadata(info);

        assertEquals("Width should be 640", 640, info.getWidth());
        assertEquals("Height should be 480", 480, info.getHeight());
    }

    @Test
    public void testParseMetadata_Fps() {
        assumeFfmpegAvailable();
        VideoFileInfo info = new VideoFileInfo(testVideoFile);
        VideoUtil.parseMetadata(info);

        assertTrue("FPS should be > 0", info.getFps() > 0);
        assertTrue("FPS should be ~30", Math.abs(info.getFps() - 30.0) < 2.0);
    }

    @Test
    public void testParseMetadata_Codecs() {
        assumeFfmpegAvailable();
        VideoFileInfo info = new VideoFileInfo(testVideoFile);
        VideoUtil.parseMetadata(info);

        assertNotNull("Video codec should not be null", info.getVideoCodec());
        assertFalse("Video codec should not be empty", info.getVideoCodec().isEmpty());
        assertNotNull("Audio codec should not be null", info.getAudioCodec());
    }

    @Test
    public void testParseMetadata_FileSize() {
        assumeFfmpegAvailable();
        VideoFileInfo info = new VideoFileInfo(testVideoFile);
        VideoUtil.parseMetadata(info);

        assertTrue("Original size should be > 0", info.getOriginalSize() > 0);
        assertEquals(testVideoFile.length(), info.getOriginalSize());
    }

    @Test
    public void testParseMetadata_Format() {
        assumeFfmpegAvailable();
        VideoFileInfo info = new VideoFileInfo(testVideoFile);
        VideoUtil.parseMetadata(info);

        assertEquals("Format should be mp4", "mp4", info.getFormat());
    }

    // ==================== 测试 2：实际压缩 ====================

    @Test
    public void testCompress_BasicCompression() throws Exception {
        assumeFfmpegAvailable();

        VideoFileInfo info = new VideoFileInfo(testVideoFile);
        VideoUtil.parseMetadata(info);

        VideoCompressConfig config = VideoCompressConfig.getDefault();
        config.setCrf(30); // 高压缩率
        config.setOutputPath(TEST_DIR.getAbsolutePath());
        config.setSuffix("_test");
        config.setOverwrite(true);

        File outputFile = VideoCompressUtil.generateOutputFile(testVideoFile, config);
        System.out.println("[TEST] 压缩输出: " + outputFile.getAbsolutePath());

        boolean ok = VideoCompressUtil.executeCompress(
                testVideoFile, outputFile, config,
                info.getDurationSeconds(), null);

        assertTrue("Compression should succeed", ok);
        assertTrue("Output file should exist", outputFile.exists());
        assertTrue("Output file should be > 0 bytes", outputFile.length() > 0);

        System.out.println("[TEST] 原始大小: " + info.getFormattedSize()
                + " → 压缩后: " + VideoFileInfo.formatFileSize(outputFile.length())
                + " (节省 " + String.format("%.1f%%",
                (1.0 - (double) outputFile.length() / info.getOriginalSize()) * 100) + ")");
    }

    @Test
    public void testCompress_ResolutionScale() throws Exception {
        assumeFfmpegAvailable();

        VideoFileInfo info = new VideoFileInfo(testVideoFile);
        VideoUtil.parseMetadata(info);

        VideoCompressConfig config = VideoCompressConfig.getDefault();
        config.setCrf(28);
        config.setResolutionMode(VideoCompressConfig.ResolutionMode.R480P);
        config.setOutputPath(TEST_DIR.getAbsolutePath());
        config.setSuffix("_480p");
        config.setOverwrite(true);

        File outputFile = VideoCompressUtil.generateOutputFile(testVideoFile, config);
        boolean ok = VideoCompressUtil.executeCompress(
                testVideoFile, outputFile, config,
                info.getDurationSeconds(), null);

        assertTrue("Resolution scale compression should succeed", ok);
        assertTrue("Output file should exist", outputFile.exists());

        // 验证输出文件比原文件小（因为降低了分辨率 + CRF 28）
        assertTrue("Scaled output should be smaller than input",
                outputFile.length() < info.getOriginalSize());

        System.out.println("[TEST] 640x480 → 480p: "
                + VideoFileInfo.formatFileSize(info.getOriginalSize())
                + " → " + VideoFileInfo.formatFileSize(outputFile.length()));
    }

    @Test
    public void testCompress_RemoveAudio() throws Exception {
        assumeFfmpegAvailable();

        VideoFileInfo info = new VideoFileInfo(testVideoFile);
        VideoUtil.parseMetadata(info);

        VideoCompressConfig config = VideoCompressConfig.getDefault();
        config.setCrf(28);
        config.setAudioMode(VideoCompressConfig.AudioMode.REMOVE);
        config.setOutputPath(TEST_DIR.getAbsolutePath());
        config.setSuffix("_noaudio");
        config.setOverwrite(true);

        File outputFile = VideoCompressUtil.generateOutputFile(testVideoFile, config);
        boolean ok = VideoCompressUtil.executeCompress(
                testVideoFile, outputFile, config,
                info.getDurationSeconds(), null);

        assertTrue("Remove audio compression should succeed", ok);
        assertTrue("Output file should exist", outputFile.exists());

        // 验证无声版比有声版小
        assertTrue("No-audio output should be smaller",
                outputFile.length() < info.getOriginalSize());

        System.out.println("[TEST] 移除音频: "
                + VideoFileInfo.formatFileSize(info.getOriginalSize())
                + " → " + VideoFileInfo.formatFileSize(outputFile.length()));
    }

    @Test
    public void testCompress_WechatPreset() throws Exception {
        assumeFfmpegAvailable();

        VideoFileInfo info = new VideoFileInfo(testVideoFile);
        VideoUtil.parseMetadata(info);

        VideoCompressConfig config = VideoCompressConfig.getDefault();
        config.applyPreset(VideoCompressConfig.Preset.WECHAT);
        config.setOutputPath(TEST_DIR.getAbsolutePath());
        config.setSuffix("_wechat");
        config.setOverwrite(true);

        File outputFile = VideoCompressUtil.generateOutputFile(testVideoFile, config);
        boolean ok = VideoCompressUtil.executeCompress(
                testVideoFile, outputFile, config,
                info.getDurationSeconds(), null);

        assertTrue("Wechat preset compression should succeed", ok);
        assertTrue("Output file should exist", outputFile.exists());

        // 微信预设：CRF 30 + 480p，文件应该明显减小
        long savedPercent = (1 - outputFile.length() / info.getOriginalSize()) * 100;
        System.out.println("[TEST] 微信预设: "
                + VideoFileInfo.formatFileSize(info.getOriginalSize())
                + " → " + VideoFileInfo.formatFileSize(outputFile.length())
                + " (节省 " + savedPercent + "%)");

        assertTrue("Wechat preset should significantly reduce size",
                outputFile.length() < info.getOriginalSize());
    }

    @Test
    public void testCompress_ProgressCallback() throws Exception {
        assumeFfmpegAvailable();

        VideoFileInfo info = new VideoFileInfo(testVideoFile);
        VideoUtil.parseMetadata(info);

        VideoCompressConfig config = VideoCompressConfig.getDefault();
        config.setCrf(30);
        config.setOutputPath(TEST_DIR.getAbsolutePath());
        config.setSuffix("_progress");
        config.setOverwrite(true);

        File outputFile = VideoCompressUtil.generateOutputFile(testVideoFile, config);

        // 使用进度回调
        final double[] lastProgress = {0};
        final boolean[] completed = {false};

        boolean ok = VideoCompressUtil.executeCompress(
                testVideoFile, outputFile, config,
                info.getDurationSeconds(),
                (progress, status) -> {
                    lastProgress[0] = progress;
                    System.out.println("[PROGRESS] " + String.format("%.0f%%", progress * 100)
                            + " — " + status);
                });

        assertTrue("Compression with callback should succeed", ok);
        // 进度应该在完成时达到 1.0
        assertEquals("Final progress should be 1.0", 1.0, lastProgress[0], 0.01);
    }

    // ==================== 测试 3：批量元数据解析 ====================

    @Test
    public void testParseMetadataBatch() {
        assumeFfmpegAvailable();

        java.util.List<VideoFileInfo> list = new java.util.ArrayList<>();
        list.add(new VideoFileInfo(testVideoFile));
        list.add(new VideoFileInfo(testVideoFile)); // 同一文件解析两次

        VideoUtil.parseMetadataBatch(list);

        for (VideoFileInfo info : list) {
            assertTrue("Duration should be parsed", info.getDurationSeconds() > 0);
            assertEquals("Resolution should be 640x480", 640, info.getWidth());
            assertEquals("Resolution should be 640x480", 480, info.getHeight());
        }
    }

    // ==================== 测试 4：formatFileSize 边界 ====================

    @Test
    public void testFormatFileSize_Boundary() {
        assertEquals("0 B", VideoFileInfo.formatFileSize(0));
        assertEquals("512 B", VideoFileInfo.formatFileSize(512));
        assertEquals("1.0 KB", VideoFileInfo.formatFileSize(1024));
        assertEquals("1.5 KB", VideoFileInfo.formatFileSize(1536));
        assertEquals("1.0 MB", VideoFileInfo.formatFileSize(1024 * 1024));
        assertEquals("未知", VideoFileInfo.formatFileSize(-1));
    }

    // ==================== 辅助方法 ====================

    private static void assumeFfmpegAvailable() {
        if (!ffmpegAvailable) {
            System.out.println("[SKIP] FFmpeg 不可用");
        }
        org.junit.Assume.assumeTrue("FFmpeg not available", ffmpegAvailable);
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
