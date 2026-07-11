package com.nchu.imagecompress.model;

/**
 * 进度数据块 — 用于 SwingWorker 的 publish/process 机制。
 *
 * <p>Service 层批量处理时，将进度信息封装为此对象，
 * 通过 SwingWorker<Void, ProgressChunk> 发布到 EDT，
 * View 层在 process() 方法中消费更新进度条和状态文字。</p>
 *
 * <p>为什么不用简单的 int/double 传进度？
 * 因为进度信息需要包含文件名、当前索引等上下文，
 * 单一进度数值让 UI 无法显示"正在处理: photo_01.jpg"这样的详情。</p>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-08
 */
public class ProgressChunk {

    // ==================== 进度字段 ====================

    /** 当前处理的文件索引 (0-based) */
    private int currentIndex;

    /** 文件总数 */
    private int totalFiles;

    /** 当前文件名 */
    private String currentFileName;

    /** 当前文件进度百分比 (0.0 - 1.0) */
    private double fileProgress;

    /** 整体进度百分比 (0.0 - 1.0) */
    private double overallProgress;

    /** 已完成文件数 */
    private int completedCount;

    /** 失败文件数 */
    private int failedCount;

    /** 进度描述文字（如 "正在压缩 3/20..."） */
    private String statusText;

    /** 是否是最终完成通知 */
    private boolean finished;

    /** 是否已被用户取消 */
    private boolean cancelled;

    // ==================== 工厂方法 ====================

    /** 创建"开始处理某文件"的进度块 */
    public static ProgressChunk fileStarted(int index, int total, String fileName) {
        ProgressChunk chunk = new ProgressChunk();
        chunk.currentIndex = index;
        chunk.totalFiles = total;
        chunk.currentFileName = fileName;
        chunk.fileProgress = 0.0;
        chunk.overallProgress = (double) index / total;
        chunk.completedCount = index;
        chunk.failedCount = 0;
        chunk.finished = false;
        chunk.cancelled = false;
        chunk.statusText = "正在处理 " + fileName + " (" + (index + 1) + "/" + total + ")";
        return chunk;
    }

    /** 创建"某文件进度更新"的进度块 */
    public static ProgressChunk fileProgress(int index, int total, String fileName, double filePct) {
        ProgressChunk chunk = new ProgressChunk();
        chunk.currentIndex = index;
        chunk.totalFiles = total;
        chunk.currentFileName = fileName;
        chunk.fileProgress = filePct;
        // 整体进度 = 已完成文件的进度 + 当前文件的部分进度
        chunk.overallProgress = (index + filePct) / total;
        chunk.completedCount = index;
        chunk.failedCount = 0;
        chunk.finished = false;
        chunk.cancelled = false;
        chunk.statusText = "正在处理 " + fileName + " (" + (index + 1) + "/" + total + ")";
        return chunk;
    }

    /** 创建"批量处理完成"的进度块 */
    public static ProgressChunk batchCompleted(int total, int success, int failed, long elapsedMs) {
        ProgressChunk chunk = new ProgressChunk();
        chunk.totalFiles = total;
        chunk.fileProgress = 1.0;
        chunk.overallProgress = 1.0;
        chunk.completedCount = success;
        chunk.failedCount = failed;
        chunk.finished = true;
        chunk.cancelled = false;
        chunk.statusText = String.format("完成！成功 %d / 失败 %d / 耗时 %s",
                success, failed, elapsedMs < 1000 ? elapsedMs + "ms" : String.format("%.1fs", elapsedMs / 1000.0));
        return chunk;
    }

    /** 创建"批量处理取消"的进度块 */
    public static ProgressChunk batchCancelled(int completed, int total) {
        ProgressChunk chunk = new ProgressChunk();
        chunk.totalFiles = total;
        chunk.completedCount = completed;
        chunk.overallProgress = (double) completed / total;
        chunk.finished = false;
        chunk.cancelled = true;
        chunk.statusText = "已取消 (已完成 " + completed + "/" + total + ")";
        return chunk;
    }

    // ==================== Getter ====================

    public int getCurrentIndex() { return currentIndex; }
    public int getTotalFiles() { return totalFiles; }
    public String getCurrentFileName() { return currentFileName; }
    public double getFileProgress() { return fileProgress; }
    public double getOverallProgress() { return overallProgress; }
    public int getCompletedCount() { return completedCount; }
    public int getFailedCount() { return failedCount; }
    public String getStatusText() { return statusText; }
    public boolean isFinished() { return finished; }
    public boolean isCancelled() { return cancelled; }

    @Override
    public String toString() {
        return "ProgressChunk{" + statusText + ", overall="
                + String.format("%.0f%%", overallProgress * 100) + "}";
    }
}
