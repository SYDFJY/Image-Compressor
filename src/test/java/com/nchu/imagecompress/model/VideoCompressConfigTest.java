package com.nchu.imagecompress.model;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * VideoCompressConfig 单元测试 — 枚举、预设、边界值、isEffectivelyNoOp。
 *
 * @author NCHU-Student
 * @version 2.0.0
 * @since 2026-07-11
 */
public class VideoCompressConfigTest {

    // ==================== 默认值测试 ====================

    @Test
    public void testDefaultValues() {
        VideoCompressConfig config = VideoCompressConfig.getDefault();

        assertEquals("Default CRF should be 23", 23, config.getCrf());
        assertEquals("Default resolution should be ORIGINAL",
                VideoCompressConfig.ResolutionMode.ORIGINAL, config.getResolutionMode());
        assertEquals("Default fps should be ORIGINAL",
                VideoCompressConfig.FpsMode.ORIGINAL, config.getFpsMode());
        assertEquals("Default audio should be KEEP",
                VideoCompressConfig.AudioMode.KEEP, config.getAudioMode());
        assertEquals("Default format should be ORIGINAL",
                VideoCompressConfig.VideoFormat.ORIGINAL, config.getOutputFormat());
        assertEquals("Default suffix should be _compressed", "_compressed", config.getSuffix());
        assertFalse("Default overwrite should be false", config.isOverwrite());
    }

    // ==================== CRF 边界值测试 ====================

    @Test
    public void testSetCrf_BoundaryMin() {
        VideoCompressConfig config = new VideoCompressConfig();
        config.setCrf(-10);
        assertEquals("CRF should be clamped to 0", 0, config.getCrf());
    }

    @Test
    public void testSetCrf_BoundaryMax() {
        VideoCompressConfig config = new VideoCompressConfig();
        config.setCrf(100);
        assertEquals("CRF should be clamped to 51", 51, config.getCrf());
    }

    @Test
    public void testSetCrf_Normal() {
        VideoCompressConfig config = new VideoCompressConfig();
        config.setCrf(28);
        assertEquals(28, config.getCrf());
    }

    @Test
    public void testSetCrf_Zero() {
        VideoCompressConfig config = new VideoCompressConfig();
        config.setCrf(0); // 无损
        assertEquals(0, config.getCrf());
    }

    // ==================== isEffectivelyNoOp 测试 ====================

    @Test
    public void testIsEffectivelyNoOp_Defaults() {
        // 默认配置（crf=23, 原始分辨率, 原始帧率, 保留音频, 原格式）→ crf>0，不是 NoOp
        VideoCompressConfig config = VideoCompressConfig.getDefault();
        assertFalse("Default config should NOT be no-op (crf=23 > 0)", config.isEffectivelyNoOp());
    }

    @Test
    public void testIsEffectivelyNoOp_AllOriginal() {
        VideoCompressConfig config = new VideoCompressConfig();
        config.setCrf(0);
        config.setResolutionMode(VideoCompressConfig.ResolutionMode.ORIGINAL);
        config.setFpsMode(VideoCompressConfig.FpsMode.ORIGINAL);
        config.setAudioMode(VideoCompressConfig.AudioMode.KEEP);
        config.setOutputFormat(VideoCompressConfig.VideoFormat.ORIGINAL);
        assertTrue("Should be no-op when crf=0 and all original", config.isEffectivelyNoOp());
    }

    @Test
    public void testIsEffectivelyNoOp_CrfNotZero() {
        VideoCompressConfig config = new VideoCompressConfig();
        config.setCrf(1); // crf > 0, 不应为 NoOp
        config.setResolutionMode(VideoCompressConfig.ResolutionMode.ORIGINAL);
        config.setFpsMode(VideoCompressConfig.FpsMode.ORIGINAL);
        config.setAudioMode(VideoCompressConfig.AudioMode.KEEP);
        config.setOutputFormat(VideoCompressConfig.VideoFormat.ORIGINAL);
        assertFalse("crf=1 should NOT be no-op", config.isEffectivelyNoOp());
    }

    @Test
    public void testIsEffectivelyNoOp_ChangeResolution() {
        VideoCompressConfig config = new VideoCompressConfig();
        config.setCrf(0);
        config.setResolutionMode(VideoCompressConfig.ResolutionMode.R720P); // 改变了分辨率
        config.setFpsMode(VideoCompressConfig.FpsMode.ORIGINAL);
        config.setAudioMode(VideoCompressConfig.AudioMode.KEEP);
        config.setOutputFormat(VideoCompressConfig.VideoFormat.ORIGINAL);
        assertFalse("Resolution change should NOT be no-op", config.isEffectivelyNoOp());
    }

    @Test
    public void testIsEffectivelyNoOp_RemoveAudio() {
        VideoCompressConfig config = new VideoCompressConfig();
        config.setCrf(0);
        config.setResolutionMode(VideoCompressConfig.ResolutionMode.ORIGINAL);
        config.setFpsMode(VideoCompressConfig.FpsMode.ORIGINAL);
        config.setAudioMode(VideoCompressConfig.AudioMode.REMOVE); // 移除了音频
        config.setOutputFormat(VideoCompressConfig.VideoFormat.ORIGINAL);
        assertFalse("Audio removal should NOT be no-op", config.isEffectivelyNoOp());
    }

    // ==================== 预设测试 ====================

    @Test
    public void testApplyPreset_Wechat() {
        VideoCompressConfig config = new VideoCompressConfig();
        config.applyPreset(VideoCompressConfig.Preset.WECHAT);

        assertEquals(30, config.getCrf());
        assertEquals(VideoCompressConfig.ResolutionMode.R480P, config.getResolutionMode());
        assertEquals(VideoCompressConfig.FpsMode.ORIGINAL, config.getFpsMode());
        assertEquals(VideoCompressConfig.AudioMode.KEEP, config.getAudioMode());
        // 预设不改变输出格式
        assertEquals(VideoCompressConfig.VideoFormat.ORIGINAL, config.getOutputFormat());
    }

    @Test
    public void testApplyPreset_Bilibili() {
        VideoCompressConfig config = new VideoCompressConfig();
        config.applyPreset(VideoCompressConfig.Preset.BILIBILI);

        assertEquals(20, config.getCrf());
        assertEquals(VideoCompressConfig.ResolutionMode.R1080P, config.getResolutionMode());
        assertEquals(VideoCompressConfig.FpsMode.FPS_30, config.getFpsMode());
        assertEquals(VideoCompressConfig.AudioMode.KEEP, config.getAudioMode());
    }

    @Test
    public void testApplyPreset_Archive() {
        VideoCompressConfig config = new VideoCompressConfig();
        config.applyPreset(VideoCompressConfig.Preset.ARCHIVE);

        assertEquals(15, config.getCrf());
        assertEquals(VideoCompressConfig.ResolutionMode.ORIGINAL, config.getResolutionMode());
        assertEquals(VideoCompressConfig.FpsMode.ORIGINAL, config.getFpsMode());
        assertEquals(VideoCompressConfig.AudioMode.KEEP, config.getAudioMode());
    }

    // ==================== VideoFormat fromExtension 测试 ====================

    @Test
    public void testVideoFormatFromExtension_Mp4() {
        assertEquals(VideoCompressConfig.VideoFormat.MP4,
                VideoCompressConfig.VideoFormat.fromExtension("mp4"));
    }

    @Test
    public void testVideoFormatFromExtension_UpperCase() {
        assertEquals(VideoCompressConfig.VideoFormat.MP4,
                VideoCompressConfig.VideoFormat.fromExtension("MP4"));
    }

    @Test
    public void testVideoFormatFromExtension_WithDot() {
        assertEquals(VideoCompressConfig.VideoFormat.WEBM,
                VideoCompressConfig.VideoFormat.fromExtension(".webm"));
    }

    @Test
    public void testVideoFormatFromExtension_Null() {
        assertEquals(VideoCompressConfig.VideoFormat.ORIGINAL,
                VideoCompressConfig.VideoFormat.fromExtension(null));
    }

    @Test
    public void testVideoFormatFromExtension_Empty() {
        assertEquals(VideoCompressConfig.VideoFormat.ORIGINAL,
                VideoCompressConfig.VideoFormat.fromExtension(""));
    }

    @Test
    public void testVideoFormatFromExtension_Unknown() {
        assertEquals(VideoCompressConfig.VideoFormat.ORIGINAL,
                VideoCompressConfig.VideoFormat.fromExtension("flv")); // FLV 未在 VideoFormat 中定义
    }

    // ==================== ResolutionMode 值测试 ====================

    @Test
    public void testResolutionMode_Values() {
        assertEquals(-1, VideoCompressConfig.ResolutionMode.ORIGINAL.getMaxWidth());
        assertEquals(-1, VideoCompressConfig.ResolutionMode.ORIGINAL.getMaxHeight());
        assertEquals(854, VideoCompressConfig.ResolutionMode.R480P.getMaxWidth());
        assertEquals(480, VideoCompressConfig.ResolutionMode.R480P.getMaxHeight());
        assertEquals(1920, VideoCompressConfig.ResolutionMode.R1080P.getMaxWidth());
        assertEquals(1080, VideoCompressConfig.ResolutionMode.R1080P.getMaxHeight());
        assertEquals(3840, VideoCompressConfig.ResolutionMode.R4K.getMaxWidth());
        assertEquals(2160, VideoCompressConfig.ResolutionMode.R4K.getMaxHeight());
    }

    // ==================== FpsMode 值测试 ====================

    @Test
    public void testFpsMode_Values() {
        assertEquals(-1, VideoCompressConfig.FpsMode.ORIGINAL.getFps());
        assertEquals(24, VideoCompressConfig.FpsMode.FPS_24.getFps());
        assertEquals(30, VideoCompressConfig.FpsMode.FPS_30.getFps());
        assertEquals(60, VideoCompressConfig.FpsMode.FPS_60.getFps());
    }
}
