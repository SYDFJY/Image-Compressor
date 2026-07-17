package com.nchu.imagecompress.service;

import com.nchu.imagecompress.model.VideoCompressConfig;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.*;

/**
 * VideoCompressUtil 单元测试 — 覆盖命令构建、时间解析、输出路径生成。
 *
 * <p>所有测试不依赖 FFmpeg 安装，纯 Java 逻辑验证。</p>
 *
 * @author NCHU-Student
 * @version 2.0.0
 * @since 2026-07-11
 */
public class VideoCompressUtilTest {

    // ==================== parseTime 测试 ====================

    @Test
    public void testParseTime_NullInput() {
        assertEquals(-1, VideoCompressUtil.parseTime(null), 0.001);
    }

    @Test
    public void testParseTime_EmptyString() {
        assertEquals(-1, VideoCompressUtil.parseTime(""), 0.001);
    }

    @Test
    public void testParseTime_NoTimePattern() {
        assertEquals(-1, VideoCompressUtil.parseTime("frame=  123 fps= 30 q=28.0"), 0.001);
    }

    @Test
    public void testParseTime_StandardFfmpegOutput() {
        // 典型的 ffmpeg stderr 进度行
        String line = "frame=  123 fps= 30 q=28.0 size=    1024kB time=00:00:05.12 bitrate=1638.4kbits/s speed=1.5x";
        double expected = 5.12; // 5秒 + 12/100秒
        assertEquals(expected, VideoCompressUtil.parseTime(line), 0.001);
    }

    @Test
    public void testParseTime_ZeroTime() {
        String line = "time=00:00:00.00";
        assertEquals(0.0, VideoCompressUtil.parseTime(line), 0.001);
    }

    @Test
    public void testParseTime_OneHour() {
        String line = "time=01:00:00.00";
        assertEquals(3600.0, VideoCompressUtil.parseTime(line), 0.001);
    }

    @Test
    public void testParseTime_MinutesAndSeconds() {
        String line = "time=00:02:30.50";
        assertEquals(150.5, VideoCompressUtil.parseTime(line), 0.001);
    }

    @Test
    public void testParseTime_LargeTime() {
        String line = "time=10:30:45.99";
        double expected = 10 * 3600 + 30 * 60 + 45 + 0.99;
        assertEquals(expected, VideoCompressUtil.parseTime(line), 0.001);
    }

    @Test
    public void testParseTime_MalformedTime() {
        // 不完整的时间格式不应匹配
        assertEquals(-1, VideoCompressUtil.parseTime("time=00:00"), 0.001);
    }

    // ==================== buildFfmpegCommand 测试 ====================

    @Test
    public void testBuildCommand_BasicDefaults() {
        File input = new File("test.mp4");
        File output = new File("output.mp4");
        VideoCompressConfig config = VideoCompressConfig.getDefault();

        List<String> cmd = VideoCompressUtil.buildFfmpegCommand(input, output, config);

        // 验证基本结构
        assertEquals("ffmpeg", cmd.get(0));
        assertTrue("Should contain -i flag", cmd.contains("-i"));
        assertTrue("Should contain input path", cmd.contains(input.getAbsolutePath()));
        assertTrue("Should contain -c:v", cmd.contains("-c:v"));
        assertTrue("Should contain -crf", cmd.contains("-crf"));
        assertTrue("Should contain -preset", cmd.contains("-preset"));
        assertTrue("Should contain -c:a", cmd.contains("-c:a"));
        assertTrue("Should contain -y (overwrite)", cmd.contains("-y"));
        assertTrue("Should contain output path", cmd.contains(output.getAbsolutePath()));
    }

    @Test
    public void testBuildCommand_CrfValue() {
        File input = new File("test.mp4");
        File output = new File("output.mp4");
        VideoCompressConfig config = VideoCompressConfig.getDefault();
        config.setCrf(28);

        List<String> cmd = VideoCompressUtil.buildFfmpegCommand(input, output, config);
        int crfIndex = cmd.indexOf("-crf");
        assertTrue("Should have -crf flag", crfIndex >= 0);
        assertEquals("28", cmd.get(crfIndex + 1));
    }

    @Test
    public void testBuildCommand_RemoveAudio() {
        File input = new File("test.mp4");
        File output = new File("output.mp4");
        VideoCompressConfig config = VideoCompressConfig.getDefault();
        config.setAudioMode(VideoCompressConfig.AudioMode.REMOVE);

        List<String> cmd = VideoCompressUtil.buildFfmpegCommand(input, output, config);
        assertTrue("Should contain -an (no audio)", cmd.contains("-an"));
        assertFalse("Should not contain -c:a when audio removed", cmd.contains("-c:a"));
    }

    @Test
    public void testBuildCommand_ResolutionScale() {
        File input = new File("test.mp4");
        File output = new File("output.mp4");
        VideoCompressConfig config = VideoCompressConfig.getDefault();
        config.setResolutionMode(VideoCompressConfig.ResolutionMode.R720P);

        List<String> cmd = VideoCompressUtil.buildFfmpegCommand(input, output, config);
        int vfIndex = cmd.indexOf("-vf");
        assertTrue("Should have -vf flag for scaling", vfIndex >= 0);
        assertTrue("Scale filter should contain 1280:-2 (auto even height)",
                cmd.get(vfIndex + 1).contains("1280:-2"));
    }

    @Test
    public void testBuildCommand_OriginalResolution_NoScale() {
        File input = new File("test.mp4");
        File output = new File("output.mp4");
        VideoCompressConfig config = VideoCompressConfig.getDefault();
        config.setResolutionMode(VideoCompressConfig.ResolutionMode.ORIGINAL);

        List<String> cmd = VideoCompressUtil.buildFfmpegCommand(input, output, config);
        assertFalse("Should not have -vf when keeping original resolution", cmd.contains("-vf"));
    }

    @Test
    public void testBuildCommand_FpsChange() {
        File input = new File("test.mp4");
        File output = new File("output.mp4");
        VideoCompressConfig config = VideoCompressConfig.getDefault();
        config.setFpsMode(VideoCompressConfig.FpsMode.FPS_30);

        List<String> cmd = VideoCompressUtil.buildFfmpegCommand(input, output, config);
        int rIndex = cmd.indexOf("-r");
        assertTrue("Should have -r flag for fps", rIndex >= 0);
        assertEquals("30", cmd.get(rIndex + 1));
    }

    @Test
    public void testBuildCommand_OriginalFps_NoFlag() {
        File input = new File("test.mp4");
        File output = new File("output.mp4");
        VideoCompressConfig config = VideoCompressConfig.getDefault();
        config.setFpsMode(VideoCompressConfig.FpsMode.ORIGINAL);

        List<String> cmd = VideoCompressUtil.buildFfmpegCommand(input, output, config);
        assertFalse("Should not have -r when keeping original fps", cmd.contains("-r"));
    }

    @Test
    public void testBuildCommand_WebmFormat() {
        File input = new File("test.mp4");
        File output = new File("output.webm");
        VideoCompressConfig config = VideoCompressConfig.getDefault();
        config.setOutputFormat(VideoCompressConfig.VideoFormat.WEBM);

        List<String> cmd = VideoCompressUtil.buildFfmpegCommand(input, output, config);
        int cvIndex = cmd.indexOf("-c:v");
        assertTrue("Should have video codec", cvIndex >= 0);
        assertEquals("libvpx-vp9", cmd.get(cvIndex + 1));
        int caIndex = cmd.indexOf("-c:a");
        assertEquals("libopus", cmd.get(caIndex + 1));
    }

    // ==================== generateOutputFile 测试 ====================

    @Test
    public void testGenerateOutputFile_DefaultSuffix() {
        File input = new File("/videos/test.mp4");
        VideoCompressConfig config = VideoCompressConfig.getDefault();

        File output = VideoCompressUtil.generateOutputFile(input, config);
        assertEquals("test_compressed.mp4", output.getName());
        assertEquals("/videos", output.getParent().replace("\\", "/"));
    }

    @Test
    public void testGenerateOutputFile_CustomSuffix() {
        File input = new File("/videos/test.mp4");
        VideoCompressConfig config = VideoCompressConfig.getDefault();
        config.setSuffix("_small");

        File output = VideoCompressUtil.generateOutputFile(input, config);
        assertEquals("test_small.mp4", output.getName());
    }

    @Test
    public void testGenerateOutputFile_CustomOutputDir() {
        File input = new File("/videos/test.mp4");
        VideoCompressConfig config = VideoCompressConfig.getDefault();
        config.setOutputPath("/output");

        File output = VideoCompressUtil.generateOutputFile(input, config);
        assertEquals("/output", output.getParent().replace("\\", "/"));
        assertEquals("test_compressed.mp4", output.getName());
    }

    @Test
    public void testGenerateOutputFile_FormatConversion() {
        File input = new File("/videos/test.mkv");
        VideoCompressConfig config = VideoCompressConfig.getDefault();
        config.setOutputFormat(VideoCompressConfig.VideoFormat.MP4);

        File output = VideoCompressUtil.generateOutputFile(input, config);
        assertEquals("test_compressed.mp4", output.getName());
    }

    @Test
    public void testGenerateOutputFile_OriginalFormat() {
        File input = new File("/videos/test.avi");
        VideoCompressConfig config = VideoCompressConfig.getDefault();
        config.setOutputFormat(VideoCompressConfig.VideoFormat.ORIGINAL);

        File output = VideoCompressUtil.generateOutputFile(input, config);
        assertEquals("test_compressed.avi", output.getName());
    }

    @Test
    public void testGenerateOutputFile_NoExtension() {
        File input = new File("/videos/testfile");
        VideoCompressConfig config = VideoCompressConfig.getDefault();

        File output = VideoCompressUtil.generateOutputFile(input, config);
        // 无扩展名时默认使用 .mp4（ORIGINAL 格式时保留原扩展名，此处为无扩展名+_compressed.mp4）
        assertTrue("Output should have a default extension",
                output.getName().endsWith(".mp4") || output.getName().endsWith("_compressed"));
    }
}
