package com.nchu.imagecompress.model;

import java.io.File;

/**
 * 文件信息公共接口 — 统一图片和视频文件的元数据访问。
 *
 * <p>设计目的：让 {@code FileListPanel} 无需区分文件类型即可渲染列表，
 * 通过 {@code instanceof} 在渲染器中提供差异化显示（🖼 / 🎬 图标）。</p>
 *
 * @author NCHU-Student
 * @version 2.0.0
 * @since 2026-07-11
 */
public interface FileInfo {

    /** 处理状态枚举（与 ImageFileInfo.Status 保持一致） */
    enum Status {
        PENDING, PROCESSING, SUCCESS, FAILED
    }

    /** 文件类型枚举 */
    enum FileType {
        IMAGE, VIDEO
    }

    // ==================== 基本信息 ====================

    /** 原始文件对象 */
    File getSourceFile();

    /** 文件名（含扩展名） */
    String getFileName();

    /** 原始文件大小（字节） */
    long getOriginalSize();

    /** 文件格式/扩展名（如 "jpg", "mp4"） */
    String getFormat();

    /** 文件类型（图片 / 视频） */
    FileType getFileType();

    // ==================== 状态信息 ====================

    /** 当前处理状态 */
    Status getFileInfoStatus();

    /** 设置处理状态 */
    void setFileInfoStatus(Status status);

    /** 失败时的错误信息 */
    String getErrorMessage();

    /** 设置失败错误信息 */
    void setErrorMessage(String errorMessage);

    /** 列表中的索引位置 */
    int getIndex();

    /** 设置列表索引 */
    void setIndex(int index);

    // ==================== 显示信息 ====================

    /** 获取格式化的文件大小字符串（如 "1.5 MB"） */
    String getFormattedSize();

    /** 获取文件时长字符串（图片返回空，视频返回 mm:ss） */
    String getDurationString();
}
