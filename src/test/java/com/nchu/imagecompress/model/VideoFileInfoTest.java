package com.nchu.imagecompress.model;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * VideoFileInfo 单元测试 — 格式化、时长显示、FileInfo 接口一致性。
 *
 * @author NCHU-Student
 * @version 2.0.0
 * @since 2026-07-11
 */
public class VideoFileInfoTest {

    // ==================== 构造函数测试 ====================

    @Test
    public void testConstructor_FromFile() {
        File file = new File("/test/video.mp4");
        VideoFileInfo info = new VideoFileInfo(file);

        assertEquals("video.mp4", info.getFileName());
        assertEquals("mp4", info.getFormat());
        assertEquals(file, info.getSourceFile());
        assertEquals(FileInfo.FileType.VIDEO, info.getFileType());
        assertEquals(FileInfo.Status.PENDING, info.getFileInfoStatus());
    }

    @Test
    public void testConstructor_Default() {
        VideoFileInfo info = new VideoFileInfo();
        assertEquals(FileInfo.Status.PENDING, info.getFileInfoStatus());
        assertNull(info.getSourceFile());
        assertNull(info.getFileName());
    }

    // ==================== getDurationString (mm:ss) ====================

    @Test
    public void testGetDurationString_ZeroSeconds() {
        VideoFileInfo info = new VideoFileInfo();
        info.setDurationSeconds(0);
        assertEquals("", info.getDurationString());
    }

    @Test
    public void testGetDurationString_Negative() {
        VideoFileInfo info = new VideoFileInfo();
        info.setDurationSeconds(-5);
        assertEquals("", info.getDurationString());
    }

    @Test
    public void testGetDurationString_LessThanMinute() {
        VideoFileInfo info = new VideoFileInfo();
        info.setDurationSeconds(45);
        assertEquals("0:45", info.getDurationString());
    }

    @Test
    public void testGetDurationString_ExactlyOneMinute() {
        VideoFileInfo info = new VideoFileInfo();
        info.setDurationSeconds(60);
        assertEquals("1:00", info.getDurationString());
    }

    @Test
    public void testGetDurationString_MinutesAndSeconds() {
        VideoFileInfo info = new VideoFileInfo();
        info.setDurationSeconds(185); // 3:05
        assertEquals("3:05", info.getDurationString());
    }

    @Test
    public void testGetDurationString_LeadingZeroSeconds() {
        VideoFileInfo info = new VideoFileInfo();
        info.setDurationSeconds(125); // 2:05
        assertEquals("2:05", info.getDurationString());
    }

    // ==================== getFullDurationString (HH:mm:ss) ====================

    @Test
    public void testGetFullDurationString_NoDuration() {
        VideoFileInfo info = new VideoFileInfo();
        assertEquals("未知", info.getFullDurationString());
    }

    @Test
    public void testGetFullDurationString_LessThanHour() {
        VideoFileInfo info = new VideoFileInfo();
        info.setDurationSeconds(3661); // 1h 1m 1s → 但小于 1 小时的话就只显示 m:ss
        // Actually: 3661 / 3600 = 1h + 61s → 1:01:01
        assertEquals("1:01:01", info.getFullDurationString());
    }

    @Test
    public void testGetFullDurationString_OnlyMinutes() {
        VideoFileInfo info = new VideoFileInfo();
        info.setDurationSeconds(125); // 2:05
        assertEquals("2:05", info.getFullDurationString());
    }

    @Test
    public void testGetFullDurationString_ExactlyOneHour() {
        VideoFileInfo info = new VideoFileInfo();
        info.setDurationSeconds(3600);
        assertEquals("1:00:00", info.getFullDurationString());
    }

    @Test
    public void testGetFullDurationString_TwoHours() {
        VideoFileInfo info = new VideoFileInfo();
        info.setDurationSeconds(7200 + 180 + 7); // 2:03:07
        assertEquals("2:03:07", info.getFullDurationString());
    }

    // ==================== getFormattedSize ====================

    @Test
    public void testGetFormattedSize_Bytes() {
        VideoFileInfo info = new VideoFileInfo();
        info.setOriginalSize(500);
        assertEquals("500 B", info.getFormattedSize());
    }

    @Test
    public void testGetFormattedSize_KB() {
        VideoFileInfo info = new VideoFileInfo();
        info.setOriginalSize(1536); // 1.5 KB
        assertEquals("1.5 KB", info.getFormattedSize());
    }

    @Test
    public void testGetFormattedSize_MB() {
        VideoFileInfo info = new VideoFileInfo();
        info.setOriginalSize(50 * 1024 * 1024); // 50 MB
        assertEquals("50.0 MB", info.getFormattedSize());
    }

    @Test
    public void testGetFormattedSize_GB() {
        VideoFileInfo info = new VideoFileInfo();
        info.setOriginalSize(2L * 1024 * 1024 * 1024 + 500 * 1024 * 1024); // ~2.5 GB
        String result = info.getFormattedSize();
        assertTrue("Large file should show GB", result.contains("GB"));
    }

    @Test
    public void testGetFormattedSize_Negative() {
        VideoFileInfo info = new VideoFileInfo();
        info.setOriginalSize(-1);
        assertEquals("未知", info.getFormattedSize());
    }

    // ==================== FileInfo 接口一致性 ====================

    @Test
    public void testFileInfoStatusMapping() {
        VideoFileInfo info = new VideoFileInfo();

        info.setFileInfoStatus(FileInfo.Status.PROCESSING);
        assertEquals(FileInfo.Status.PROCESSING, info.getFileInfoStatus());

        info.setFileInfoStatus(FileInfo.Status.SUCCESS);
        assertEquals(FileInfo.Status.SUCCESS, info.getFileInfoStatus());

        info.setFileInfoStatus(FileInfo.Status.FAILED);
        assertEquals(FileInfo.Status.FAILED, info.getFileInfoStatus());

        info.setFileInfoStatus(FileInfo.Status.PENDING);
        assertEquals(FileInfo.Status.PENDING, info.getFileInfoStatus());
    }

    @Test
    public void testErrorMessage() {
        VideoFileInfo info = new VideoFileInfo();
        assertNull(info.getErrorMessage());

        info.setErrorMessage("FFmpeg not found");
        assertEquals("FFmpeg not found", info.getErrorMessage());
    }

    @Test
    public void testIndex() {
        VideoFileInfo info = new VideoFileInfo();
        assertEquals(0, info.getIndex());

        info.setIndex(5);
        assertEquals(5, info.getIndex());
    }

    // ==================== equals / hashCode ====================

    @Test
    public void testEquals_SamePath() {
        VideoFileInfo info1 = new VideoFileInfo(new File("/test/video.mp4"));
        VideoFileInfo info2 = new VideoFileInfo(new File("/test/video.mp4"));
        assertEquals(info1, info2);
        assertEquals(info1.hashCode(), info2.hashCode());
    }

    @Test
    public void testEquals_DifferentPath() {
        VideoFileInfo info1 = new VideoFileInfo(new File("/test/video1.mp4"));
        VideoFileInfo info2 = new VideoFileInfo(new File("/test/video2.mp4"));
        assertNotEquals(info1, info2);
    }

    @Test
    public void testEquals_NullSource() {
        VideoFileInfo info1 = new VideoFileInfo();
        VideoFileInfo info2 = new VideoFileInfo();
        assertNotEquals(info1, info2); // both have null sourceFile → should be false
    }

    @Test
    public void testEquals_SameObject() {
        VideoFileInfo info = new VideoFileInfo(new File("/test/video.mp4"));
        assertEquals(info, info);
    }
}
