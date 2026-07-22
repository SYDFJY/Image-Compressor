package com.nchu.imagecompress.service;

import com.nchu.imagecompress.model.FolderWatchConfig;
import com.nchu.imagecompress.model.ImageFileInfo;
import com.nchu.imagecompress.model.VideoFileInfo;
import com.nchu.imagecompress.util.FileUtil;
import com.nchu.imagecompress.util.LogUtil;
import com.nchu.imagecompress.util.VideoUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 文件夹监控服务 — 使用 JDK WatchService 监控源文件夹的新增文件，
 * 自动按预设参数压缩后输出到目标文件夹。
 *
 * <h3>文件写入完成检测</h3>
 * <p>文件被创建后，可能仍在写入中（大文件复制、截图保存等）。
 * 采用文件大小轮询策略：每 500ms 检查一次，连续 2 次大小不变判定为写入完成。
 * 最长等待 30 秒，超时跳过。</p>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-22
 */
public class FolderWatchService {

    private final FolderWatchConfig config;
    private final Consumer<File> onNewFile;
    private final Map<String, Long> processedPaths = new ConcurrentHashMap<>();

    private WatchService watchService;
    private Thread watchThread;
    private final ExecutorService compressExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Watch-Compress-Worker");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean running = false;

    /**
     * @param config    监控配置
     * @param onNewFile 检测到新文件时的回调（在压缩线程中执行）
     */
    public FolderWatchService(FolderWatchConfig config, Consumer<File> onNewFile) {
        this.config = config;
        this.onNewFile = onNewFile;
    }

    /**
     * 启动文件夹监控。
     *
     * @throws IOException 如果 WatchService 注册失败
     */
    public void start() throws IOException {
        if (running) return;

        Path watchPath = new File(config.getWatchFolderPath()).toPath();
        watchService = FileSystems.getDefault().newWatchService();
        watchPath.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY);

        running = true;
        watchThread = new Thread(this::watchLoop, "Folder-Watch-Thread");
        watchThread.setDaemon(true);
        watchThread.start();

        LogUtil.info("[FolderWatchService] 已启动监控: " + watchPath);
    }

    /**
     * 停止文件夹监控。
     */
    public void stop() {
        running = false;
        if (watchThread != null) {
            watchThread.interrupt();
        }
        if (watchService != null) {
            try { watchService.close(); } catch (IOException ignored) {}
        }
        compressExecutor.shutdownNow();
        LogUtil.info("[FolderWatchService] 监控已停止");
    }

    public boolean isRunning() { return running; }

    // ==================== 监控主循环 ====================

    private void watchLoop() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.poll(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                break;
            }
            if (key == null) continue;

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path filename = ev.context();
                File file = new File(config.getWatchFolderPath(), filename.toString());

                if (kind == StandardWatchEventKinds.ENTRY_CREATE
                        || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                    handleNewFile(file);
                }
            }
            key.reset();
        }
    }

    // ==================== 文件处理 ====================

    private void handleNewFile(File file) {
        String absPath = file.getAbsolutePath();

        // 去重检查
        if (processedPaths.containsKey(absPath)) return;

        // 类型检查
        if (!isSupportedFile(file)) return;

        // 标记为处理中（防止重复）
        processedPaths.put(absPath, System.currentTimeMillis());

        // 提交到压缩线程池（写入完成检测在 worker 中执行）
        compressExecutor.submit(() -> {
            if (!waitForWriteComplete(file)) {
                LogUtil.info("[FolderWatchService] 文件写入超时，跳过: " + file.getName());
                processedPaths.remove(absPath);
                return;
            }
            try {
                onNewFile.accept(file);
            } catch (Exception e) {
                LogUtil.info("[FolderWatchService] 文件处理异常: " + e.getMessage());
            }
        });
    }

    // ==================== 写入完成检测 ====================

    /**
     * 轮询文件大小，直到写入完成或超时。
     *
     * @return true = 写入完成，false = 超时
     */
    private boolean waitForWriteComplete(File file) {
        long deadline = System.currentTimeMillis() + 30_000;
        long lastSize = -1;
        int stableCount = 0;

        while (System.currentTimeMillis() < deadline) {
            if (!file.exists()) {
                // 文件可能被删除或尚未创建
                try { Thread.sleep(500); } catch (InterruptedException e) { return false; }
                continue;
            }

            long currentSize = file.length();
            if (currentSize == lastSize) {
                stableCount++;
                if (stableCount >= 2) {
                    // 额外等待 200ms 确保完全落盘
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                    return file.length() == currentSize;
                }
            } else {
                stableCount = 0;
                lastSize = currentSize;
            }

            try { Thread.sleep(500); } catch (InterruptedException e) { return false; }
        }
        return false; // 超时
    }

    // ==================== 文件类型判断 ====================

    private boolean isSupportedFile(File file) {
        if (!file.isFile()) return false;
        String name = file.getName().toLowerCase();
        // 忽略临时文件和系统文件
        if (name.startsWith(".") || name.endsWith(".tmp")
                || name.endsWith(".crdownload") || name.endsWith(".part")) {
            return false;
        }
        if (config.isCompressImages() && FileUtil.isSupportedImage(file)) return true;
        if (config.isCompressVideos() && VideoUtil.isSupportedVideo(file)) return true;
        return false;
    }

    // ==================== 去重清理 ====================

    /**
     * 清理 24 小时前的去重记录。
     */
    public void purgeProcessedPaths() {
        long cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000;
        processedPaths.entrySet().removeIf(e -> e.getValue() < cutoff);
    }
}
