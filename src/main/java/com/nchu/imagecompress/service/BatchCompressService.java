package com.nchu.imagecompress.service;

import com.nchu.imagecompress.model.CompressConfig;
import com.nchu.imagecompress.model.CompressResult;
import com.nchu.imagecompress.model.ImageFileInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量压缩服务。
 *
 * <p>遍历文件列表，逐张调用 CompressService 执行压缩。
 * 通过 ProgressListener 回调进度，支持中途取消（检查 Thread 中断标志位）。
 * 单文件失败不影响整体：失败的 CompressResult 标记 success=false，其余继续处理。</p>
 *
 * <h3>线程模型</h3>
 * <p>本类在调用线程（SwingWorker 后台线程）中同步执行，不创建新线程。
 * 取消通过 Thread.interrupt() + CompressWorker.cancel(true) 实现。</p>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-08
 */
public class BatchCompressService {

    /** 单张压缩服务 */
    private final CompressService compressService;

    /**
     * 构造批量压缩服务。
     */
    public BatchCompressService() {
        this.compressService = new CompressService();
    }

    /**
     * 批量压缩图片。
     *
     * <p>遍历 fileInfoList，逐张调用 CompressService 压缩。
     * 每完成一个文件，通过 ProgressListener 通知进度。
     * 支持以下取消方式：
     * <ul>
     *   <li>每次开始新文件前检查 Thread.interrupted() 标志位</li>
     *   <li>调用方通过 SwingWorker.cancel(true) 设置中断标志</li>
     * </ul>
     *
     * @param fileInfoList 待压缩文件列表
     * @param config       压缩配置
     * @param listener     进度回调（可为 null，不回调）
     * @return 所有文件的压缩结果列表
     */
    public List<CompressResult> compressBatch(List<ImageFileInfo> fileInfoList,
                                               CompressConfig config,
                                               ProgressListener listener) {
        long startTime = System.currentTimeMillis();
        int total = fileInfoList.size();
        List<CompressResult> results = new ArrayList<>(total);
        int successCount = 0;
        int failCount = 0;

        for (int i = 0; i < total; i++) {
            // === 取消检查 ===
            if (Thread.currentThread().isInterrupted()) {
                // 用户取消：标记剩余文件
                for (int j = i; j < total; j++) {
                    ImageFileInfo info = fileInfoList.get(j);
                    info.setStatus(ImageFileInfo.Status.PENDING);
                }
                if (listener != null) {
                    listener.onBatchCancelled();
                }
                return results;
            }

            ImageFileInfo fileInfo = fileInfoList.get(i);

            // ① 通知：开始处理
            fileInfo.setStatus(ImageFileInfo.Status.PROCESSING);
            if (listener != null) {
                listener.onFileStarted(i, total, fileInfo.getFileName());
            }

            // ② 进度更新（单文件内无法精确拆分，直接跳到 100%）
            if (listener != null) {
                listener.onFileProgress(i, fileInfo.getFileName(), 0.5);
            }

            // ③ 执行压缩
            CompressResult result = compressService.compress(fileInfo, config);
            results.add(result);

            // ④ 更新状态
            if (result.isSuccess()) {
                fileInfo.setStatus(ImageFileInfo.Status.SUCCESS);
                successCount++;
            } else {
                fileInfo.setStatus(ImageFileInfo.Status.FAILED);
                fileInfo.setErrorMessage(result.getErrorMessage());
                failCount++;
            }

            // ⑤ 通知：文件完成
            if (listener != null) {
                listener.onFileCompleted(i, result);
            }
        }

        // ⑥ 通知：批量完成
        long elapsedMs = System.currentTimeMillis() - startTime;
        if (listener != null) {
            listener.onBatchCompleted(results, successCount, failCount, elapsedMs);
        }

        return results;
    }

    /**
     * 设置取消标志（通过 Thread.interrupt()）。
     * <p>由 Controller 层在用户点击取消按钮时调用。</p>
     */
    public void cancel() {
        Thread.currentThread().interrupt();
    }
}
