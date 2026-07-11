package com.nchu.imagecompress.controller;

import com.nchu.imagecompress.model.CompressConfig;
import com.nchu.imagecompress.model.CompressResult;
import com.nchu.imagecompress.model.ImageFileInfo;
import com.nchu.imagecompress.model.ProgressChunk;
import com.nchu.imagecompress.service.BatchCompressService;
import com.nchu.imagecompress.service.ProgressListener;
import com.nchu.imagecompress.util.LogUtil;

import javax.swing.SwingWorker;
import java.util.List;

/**
 * 批量压缩后台任务（SwingWorker 封装）。
 *
 * <p>将耗时的批量压缩操作放到后台线程执行，通过
 * {@code publish/process} 机制在 EDT 中安全更新 UI。</p>
 *
 * <h3>线程模型</h3>
 * <pre>
 * 用户点击"开始压缩"
 *   → Controller 创建 CompressWorker
 *   → execute() 启动后台线程
 *   → doInBackground() {
 *       batchService.compressBatch(fileList, config, listener)
 *       └→ listener.onFileStarted()  → publish(ProgressChunk)
 *       └→ listener.onFileProgress() → publish(ProgressChunk)
 *       └→ listener.onBatchCompleted() → publish(ProgressChunk)
 *     }
 *   → process(chunks) {  ← EDT
 *       progressListener.onXxx()  // UI 更新
 *     }
 *   → done() {  ← EDT
 *       onComplete 回调
 *     }
 * </pre>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-08
 */
public class CompressWorker extends SwingWorker<List<CompressResult>, ProgressChunk> {

    /** 批量压缩服务 */
    private final BatchCompressService batchService;

    /** 待压缩文件列表 */
    private final List<ImageFileInfo> fileInfoList;

    /** 压缩配置 */
    private final CompressConfig config;

    /** 外部进度监听器（在 EDT 中回调） */
    private final ProgressListener uiListener;

    /** 完成回调（在 EDT 中执行） */
    private final Runnable onComplete;

    /** 取消回调（在 EDT 中执行） */
    private final Runnable onCancel;

    /**
     * 构造 CompressWorker。
     *
     * @param batchService 批量压缩服务实例
     * @param fileInfoList 待压缩文件列表
     * @param config       压缩配置
     * @param uiListener   进度回调（在 EDT 中执行），可为 null
     * @param onComplete   完成回调（在 EDT 中执行），可为 null
     * @param onCancel     取消回调（在 EDT 中执行），可为 null
     */
    public CompressWorker(BatchCompressService batchService,
                          List<ImageFileInfo> fileInfoList,
                          CompressConfig config,
                          ProgressListener uiListener,
                          Runnable onComplete,
                          Runnable onCancel) {
        this.batchService = batchService;
        this.fileInfoList = fileInfoList;
        this.config = config;
        this.uiListener = uiListener;
        this.onComplete = onComplete;
        this.onCancel = onCancel;
    }

    /**
     * 后台线程：执行批量压缩。
     *
     * <p>创建一个委托 ProgressListener，将所有进度事件
     * 通过 {@code publish(ProgressChunk)} 发送到 EDT。</p>
     *
     * @return 所有文件的压缩结果列表
     * @throws Exception 未捕获的异常将导致 done() 中 get() 失败
     */
    @Override
    protected List<CompressResult> doInBackground() throws Exception {
        // 创建委托监听器：Service 回调 → publish → process → EDT UI 更新
        ProgressListener delegate = new ProgressListener() {
            @Override
            public void onFileStarted(int index, int total, String fileName) {
                publish(ProgressChunk.fileStarted(index, total, fileName));
            }

            @Override
            public void onFileProgress(int index, String fileName, double percent) {
                publish(ProgressChunk.fileProgress(index, fileInfoList.size(),
                        fileName, percent));
            }

            @Override
            public void onFileCompleted(int index, CompressResult result) {
                // 完成事件：更新整体进度
                publish(ProgressChunk.fileProgress(index + 1,
                        fileInfoList.size(),
                        result.getInputInfo() != null
                                ? result.getInputInfo().getFileName() : "",
                        1.0));
                // 同时更新 UI listener（通过 process 间接调用）
            }

            @Override
            public void onBatchCompleted(List<CompressResult> results,
                                          int success, int fail, long elapsedMs) {
                publish(ProgressChunk.batchCompleted(
                        fileInfoList.size(), success, fail, elapsedMs));
            }

            @Override
            public void onBatchCancelled() {
                // 估算已完成数
                int completed = 0;
                for (ImageFileInfo info : fileInfoList) {
                    if (info.getStatus() == ImageFileInfo.Status.SUCCESS
                            || info.getStatus() == ImageFileInfo.Status.FAILED) {
                        completed++;
                    }
                }
                publish(ProgressChunk.batchCancelled(completed,
                        fileInfoList.size()));
            }
        };

        // 执行批量压缩
        return batchService.compressBatch(fileInfoList, config, delegate);
    }

    /**
     * EDT 中处理进度更新。
     *
     * <p>SwingWorker 自动将多次 publish() 调用合并为一次 process() 调用，
     * 这里取最新的一条进度数据来更新 UI。如果外部 listener 存在，
     * 将进度事件转发给它。</p>
     *
     * @param chunks 累积的进度数据块列表
     */
    @Override
    protected void process(List<ProgressChunk> chunks) {
        if (uiListener == null || chunks.isEmpty()) return;

        // 取最新的一块
        ProgressChunk latest = chunks.get(chunks.size() - 1);

        // 注意：process 中对 Service 回调的转发是有限的，
        // 因为 ProgressChunk 无法完全还原 CompressResult 等信息。
        // 真正完整的 CompressResult 列表在 done() 中通过 get() 获取。
        // 这里主要转发进度更新和完成/取消事件。
        if (latest.isCancelled()) {
            uiListener.onBatchCancelled();
        } else if (latest.isFinished()) {
            // 完成事件在 done() 中统一处理，process 中只更新进度
        }
        // 中间进度事件：由 MainController 直接读取 latest 的字段更新 UI
    }

    /**
     * EDT 中执行：任务完成后的处理。
     *
     * <p>无论是正常完成、取消还是异常，都会进入此方法。</p>
     */
    @Override
    protected void done() {
        try {
            if (isCancelled()) {
                // 用户取消
                if (onCancel != null) {
                    onCancel.run();
                }
            } else {
                // 正常完成：获取结果
                List<CompressResult> results = get();
                // 通过 uiListener 发送完成事件
                if (uiListener != null) {
                    int success = 0;
                    int fail = 0;
                    for (CompressResult r : results) {
                        if (r.isSuccess()) success++;
                        else fail++;
                    }
                    // 计算总耗时（近似）
                    long elapsed = 0;
                    for (CompressResult r : results) {
                        elapsed += r.getElapsedMs();
                    }
                    uiListener.onBatchCompleted(results, success, fail, elapsed);
                }
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        } catch (InterruptedException e) {
            // 被中断
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.CancellationException e) {
            if (onCancel != null) {
                onCancel.run();
            }
        } catch (Exception e) {
            // 执行异常
            LogUtil.error("[CompressWorker] 批量压缩异常: " + e.getMessage());
            // 通知失败
            if (uiListener != null) {
                uiListener.onBatchCancelled();
            }
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }

    /**
     * 获取进度数据块列表（供 MainController 的 process 回调中使用）。
     * <p>此方法仅在 {@link #process(List)} 被覆写时有用，
     * MainController 通过覆写 process 来读取进度数据。</p>
     *
     * @return 最近一次 publish 的数据
     */
    public List<ImageFileInfo> getFileInfoList() {
        return fileInfoList;
    }
}
