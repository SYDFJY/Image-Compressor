package com.nchu.imagecompress.service;

import com.nchu.imagecompress.model.CompressConfig;
import com.nchu.imagecompress.model.CompressResult;
import com.nchu.imagecompress.model.ImageFileInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 批量压缩服务（v2 — 多线程并行压缩）。
 *
 * <p>使用固定线程池并行压缩多个文件，通过 {@link ExecutorCompletionService}
 * 收集完成结果，保持与旧版兼容的 {@link ProgressListener} 回调接口。
 * 单文件失败不影响整体，支持中途取消。</p>
 *
 * <h3>线程模型</h3>
 * <p>在调用线程中创建线程池并提交所有任务，阻塞等待全部完成。
 * 每完成一个文件即回调 progress listener。取消通过
 * {@link ExecutorService#shutdownNow()} 实现。</p>
 *
 * <h3>线程池大小</h3>
 * <p>使用 {@code Runtime.getRuntime().availableProcessors()} 作为基础值，
 * 上限 4、下限 2，平衡性能与资源消耗。</p>
 *
 * @author NCHU-Student
 * @version 2.0.0
 * @since 2026-07-08
 */
public class BatchCompressService {

    /** 线程池大小上限 */
    private static final int MAX_THREADS = 4;
    /** 线程池大小下限 */
    private static final int MIN_THREADS = 2;

    private ExecutorService executor;

    /**
     * 构造批量压缩服务。
     */
    public BatchCompressService() {
    }

    /**
     * 计算线程池大小：N_CPU 为基数，钳制在 [2, 4] 区间。
     */
    private static int poolSize() {
        int cpus = Runtime.getRuntime().availableProcessors();
        return Math.max(MIN_THREADS, Math.min(MAX_THREADS, cpus));
    }

    /**
     * 批量压缩图片（多线程并行版）。
     *
     * <p>遍历 fileInfoList，通过线程池并行执行压缩。
     * 每完成一个文件，通过 ProgressListener 通知进度（已完成数 / 总数）。
     * 支持取消：调用 {@link #cancel()} 后，线程池中的未开始任务被丢弃，
     * 正在执行的任务被中断。</p>
     *
     * @param fileInfoList 待压缩文件列表
     * @param config       压缩配置
     * @param listener     进度回调（可为 null，不回调）
     * @return 所有文件的压缩结果列表（顺序与输入列表一致）
     */
    public List<CompressResult> compressBatch(List<ImageFileInfo> fileInfoList,
                                               CompressConfig config,
                                               ProgressListener listener) {
        long startTime = System.currentTimeMillis();
        int total = fileInfoList.size();

        // 单文件或小批量：串行处理（避免线程池开销）
        if (total <= 1) {
            return compressSequential(fileInfoList, config, listener, startTime, total);
        }

        int nThreads = Math.min(poolSize(), total);
        executor = Executors.newFixedThreadPool(nThreads);
        ExecutorCompletionService<CompressResult> completionService =
                new ExecutorCompletionService<>(executor);

        final CompressConfig cfg = config;
        Map<Future<CompressResult>, Integer> futureIndexMap = new HashMap<>();
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<CompressResult> results = new ArrayList<>(total);

        // 预填充结果列表
        for (int i = 0; i < total; i++) {
            results.add(null);
        }

        // 提交所有任务
        for (int i = 0; i < total; i++) {
            final int index = i;
            final ImageFileInfo fileInfo = fileInfoList.get(i);

            // 取消检查
            if (executor.isShutdown()) {
                markRemaining(fileInfoList, index, total, listener);
                return results;
            }

            fileInfo.setStatus(ImageFileInfo.Status.PENDING);
            if (listener != null) {
                listener.onFileStarted(index, total, fileInfo.getFileName());
            }

            Callable<CompressResult> task = () -> {
                if (Thread.currentThread().isInterrupted()) {
                    CompressResult cancelled = CompressResult.fail(fileInfo, cfg, "任务已取消");
                    return cancelled;
                }
                CompressService service = new CompressService();
                // v2.5: 应用逐文件参数覆盖
                CompressConfig effectiveConfig = fileInfo.hasPerFileConfig()
                        ? cfg.applyOverride(fileInfo.getPerFileConfig())
                        : cfg;
                return service.compress(fileInfo, effectiveConfig);
            };

            Future<CompressResult> future = completionService.submit(task);
            futureIndexMap.put(future, index);
        }

        // 等待所有任务完成（按完成顺序收集，但通过 Map 还原索引）
        for (int i = 0; i < total; i++) {
            try {
                Future<CompressResult> future = completionService.take();
                int resultIndex = futureIndexMap.get(future);
                CompressResult result = future.get();
                int done = completed.incrementAndGet();

                results.set(resultIndex, result);
                ImageFileInfo info = fileInfoList.get(resultIndex);
                if (result.isSuccess()) {
                    info.setStatus(ImageFileInfo.Status.SUCCESS);
                    successCount.incrementAndGet();
                } else {
                    info.setStatus(ImageFileInfo.Status.FAILED);
                    info.setErrorMessage(result.getErrorMessage());
                    failCount.incrementAndGet();
                }

                if (listener != null) {
                    // 进度回调用已完成数（近似），文件名从 inputInfo 获取
                    String name = result.getInputInfo() != null
                            ? result.getInputInfo().getFileName() : info.getFileName();
                    listener.onFileProgress(done - 1, name, 1.0);
                    listener.onFileCompleted(done - 1, result);
                }

            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
                markRemaining(fileInfoList, completed.get(), total, listener);
                if (listener != null) {
                    listener.onBatchCancelled();
                }
                return results;
            } catch (ExecutionException e) {
                int done = completed.incrementAndGet();
                CompressResult errorResult = new CompressResult();
                errorResult.setSuccess(false);
                errorResult.setErrorMessage(e.getCause() != null
                        ? e.getCause().getMessage() : e.getMessage());
                results.add(errorResult);
                failCount.incrementAndGet();
                if (listener != null) {
                    listener.onFileCompleted(done - 1, errorResult);
                }
            }
        }

        executor.shutdown();
        try {
            executor.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        long elapsedMs = System.currentTimeMillis() - startTime;
        if (listener != null) {
            listener.onBatchCompleted(results, successCount.get(), failCount.get(), elapsedMs);
        }

        return results;
    }

    /**
     * 串行压缩（单文件时使用，避免线程池开销）。
     */
    private List<CompressResult> compressSequential(List<ImageFileInfo> fileInfoList,
                                                     CompressConfig config,
                                                     ProgressListener listener,
                                                     long startTime, int total) {
        CompressService service = new CompressService();
        List<CompressResult> results = new ArrayList<>(total);
        int successCount = 0;
        int failCount = 0;

        for (int i = 0; i < total; i++) {
            if (Thread.currentThread().isInterrupted()) {
                for (int j = i; j < total; j++) {
                    fileInfoList.get(j).setStatus(ImageFileInfo.Status.PENDING);
                }
                if (listener != null) listener.onBatchCancelled();
                return results;
            }

            ImageFileInfo fileInfo = fileInfoList.get(i);
            fileInfo.setStatus(ImageFileInfo.Status.PROCESSING);
            if (listener != null) {
                listener.onFileStarted(i, total, fileInfo.getFileName());
                listener.onFileProgress(i, fileInfo.getFileName(), 0.5);
            }

            // v2.5: 应用逐文件参数覆盖
            CompressConfig effectiveConfig = fileInfo.hasPerFileConfig()
                    ? config.applyOverride(fileInfo.getPerFileConfig())
                    : config;
            CompressResult result = service.compress(fileInfo, effectiveConfig);
            results.add(result);

            if (result.isSuccess()) {
                fileInfo.setStatus(ImageFileInfo.Status.SUCCESS);
                successCount++;
            } else {
                fileInfo.setStatus(ImageFileInfo.Status.FAILED);
                fileInfo.setErrorMessage(result.getErrorMessage());
                failCount++;
            }

            if (listener != null) {
                listener.onFileCompleted(i, result);
            }
        }

        long elapsedMs = System.currentTimeMillis() - startTime;
        if (listener != null) {
            listener.onBatchCompleted(results, successCount, failCount, elapsedMs);
        }
        return results;
    }

    /**
     * 将剩余未处理文件标记为 PENDING 并通知取消。
     */
    private void markRemaining(List<ImageFileInfo> list, int from, int total,
                               ProgressListener listener) {
        for (int j = from; j < total; j++) {
            list.get(j).setStatus(ImageFileInfo.Status.PENDING);
        }
        if (listener != null) {
            listener.onBatchCancelled();
        }
    }

    /**
     * 取消正在进行的批量压缩。
     *
     * <p>关闭线程池，中断正在执行的任务，未开始的任务将被丢弃。
     * 调用后 compressBatch 立即返回已收集的结果。</p>
     */
    public void cancel() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
    }
}
