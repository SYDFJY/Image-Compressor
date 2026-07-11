package com.nchu.imagecompress.service;

import com.nchu.imagecompress.model.CompressResult;

import java.util.List;

/**
 * 批量压缩进度回调接口。
 *
 * <p>定义 Service 层向 Controller 层报告批量压缩进度的契约。
 * Service 层不依赖任何 Swing 类，仅通过此接口回调进度事件，
 * 由 Controller 层负责将事件转发到 EDT 更新 UI。</p>
 *
 * <h3>调用时序</h3>
 * <pre>
 * onFileStarted(0, 10, "photo1.jpg")
 *   → onFileProgress(0, "photo1.jpg", 0.5)
 *   → onFileCompleted(0, result)
 * onFileStarted(1, 10, "photo2.jpg")
 *   → ...
 * onBatchCompleted(results, 9, 1, 3200)
 * </pre>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-08
 */
public interface ProgressListener {

    /**
     * 开始处理某个文件时回调。
     *
     * @param index    当前文件索引（0-based）
     * @param total    文件总数
     * @param fileName 当前文件名
     */
    void onFileStarted(int index, int total, String fileName);

    /**
     * 单个文件处理进度更新时回调。
     *
     * @param index    当前文件索引
     * @param fileName 当前文件名
     * @param percent  当前文件处理进度 (0.0 - 1.0)
     */
    void onFileProgress(int index, String fileName, double percent);

    /**
     * 某个文件处理完成后回调。
     *
     * @param index  文件索引
     * @param result 压缩结果（含成功/失败状态、大小、耗时等）
     */
    void onFileCompleted(int index, CompressResult result);

    /**
     * 全部文件处理完成后回调。
     *
     * @param results   所有文件的压缩结果列表
     * @param success   成功数量
     * @param fail      失败数量
     * @param elapsedMs 总耗时（毫秒）
     */
    void onBatchCompleted(List<CompressResult> results, int success, int fail, long elapsedMs);

    /**
     * 批量处理被取消时回调。
     * <p>在用户点击取消按钮时触发，已完成的文件结果保留。</p>
     */
    void onBatchCancelled();
}
