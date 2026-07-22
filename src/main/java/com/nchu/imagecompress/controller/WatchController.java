package com.nchu.imagecompress.controller;

import com.nchu.imagecompress.model.CompressConfig;
import com.nchu.imagecompress.model.CompressResult;
import com.nchu.imagecompress.model.FolderWatchConfig;
import com.nchu.imagecompress.model.ImageFileInfo;
import com.nchu.imagecompress.model.VideoCompressConfig;
import com.nchu.imagecompress.model.VideoFileInfo;
import com.nchu.imagecompress.service.CompressService;
import com.nchu.imagecompress.service.FolderWatchService;
import com.nchu.imagecompress.service.VideoCompressService;
import com.nchu.imagecompress.util.FileUtil;
import com.nchu.imagecompress.util.LogUtil;
import com.nchu.imagecompress.util.VideoUtil;
import com.nchu.imagecompress.view.SystemTrayManager;

import java.awt.Frame;
import java.io.File;
import java.io.IOException;

/**
 * 文件夹监控生命周期控制器 — 连接 WatchService、压缩服务和系统托盘。
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-22
 */
public class WatchController {

    private final Frame mainFrame;
    private FolderWatchService watchService;
    private SystemTrayManager trayManager;
    private FolderWatchConfig config;

    private final CompressService compressService = new CompressService();
    private final VideoCompressService videoCompressService = new VideoCompressService();

    private volatile boolean running = false;

    /** 托盘恢复回调 */
    private Runnable onRestoreFromTray;

    /** 托盘退出回调 */
    private Runnable onExitFromTray;

    public WatchController(Frame mainFrame) {
        this.mainFrame = mainFrame;
    }

    // ==================== 启动/停止 ====================

    /**
     * 启动文件夹监控。
     *
     * @param config 监控配置
     * @throws IOException 如果 WatchService 启动失败
     */
    public void start(FolderWatchConfig config) throws IOException {
        if (running) stop();

        this.config = config;
        watchService = new FolderWatchService(config, this::compressAndOutput);
        watchService.start();

        // 初始化系统托盘
        if (trayManager == null) {
            trayManager = new SystemTrayManager(mainFrame,
                    () -> { if (onRestoreFromTray != null) onRestoreFromTray.run(); },
                    () -> { if (onExitFromTray != null) onExitFromTray.run(); });
            trayManager.initialize();
        }

        running = true;
        LogUtil.info("[WatchController] 监控已启动");
    }

    /**
     * 停止文件夹监控。
     */
    public void stop() {
        running = false;
        if (watchService != null) {
            watchService.stop();
            watchService = null;
        }
        LogUtil.info("[WatchController] 监控已停止");
    }

    public boolean isRunning() { return running; }

    // ==================== 托盘 ====================

    public void setOnRestoreFromTray(Runnable callback) { this.onRestoreFromTray = callback; }
    public void setOnExitFromTray(Runnable callback) { this.onExitFromTray = callback; }

    public void minimizeToTray() {
        if (trayManager != null) trayManager.minimizeToTray();
    }

    public void removeTray() {
        if (trayManager != null) trayManager.remove();
    }

    /**
     * 显示系统托盘通知。
     */
    public void showTrayNotification(String title, String message) {
        if (trayManager != null) {
            trayManager.showNotification(title, message,
                    java.awt.TrayIcon.MessageType.INFO);
        }
    }

    // ==================== 压缩调度 ====================

    private void compressAndOutput(File file) {
        try {
            // 判断文件类型
            if (FileUtil.isSupportedImage(file)) {
                compressImage(file);
            } else if (VideoUtil.isSupportedVideo(file)) {
                compressVideo(file);
            }
        } catch (Exception e) {
            LogUtil.info("[WatchController] 压缩失败: " + file.getName()
                    + " — " + e.getMessage());
        }
    }

    private void compressImage(File file) {
        ImageFileInfo info = new ImageFileInfo(file);

        CompressConfig compressConfig = new CompressConfig();
        compressConfig.setQuality(config.getImageQuality());

        // 输出路径
        CompressConfig.NamingRule namingRule = CompressConfig.NamingRule.ADD_SUFFIX;
        try {
            namingRule = CompressConfig.NamingRule.valueOf("ADD_SUFFIX");
        } catch (Exception ignored) {}
        compressConfig.setNamingRule(namingRule);
        compressConfig.setSuffix("_compressed");

        File outputDir = new File(config.getOutputFolderPath());
        if (!outputDir.exists()) outputDir.mkdirs();

        compressConfig.setOutputPath(outputDir.getAbsolutePath());

        CompressResult result = compressService.compress(info, compressConfig);

        if (result.isSuccess()) {
            LogUtil.info("[WatchController] 图片压缩完成: " + file.getName()
                    + " → " + result.getOutputPath());

            // 删除原文件（如果配置要求）
            if (config.isDeleteOriginal() && !file.getAbsolutePath().equals(result.getOutputPath())) {
                if (file.delete()) {
                    LogUtil.info("[WatchController] 已删除原文件: " + file.getName());
                }
            }

            showTrayNotification("图片压缩完成",
                    file.getName() + " → 已保存");
        } else {
            LogUtil.info("[WatchController] 图片压缩失败: " + file.getName()
                    + " — " + result.getErrorMessage());
        }
    }

    private void compressVideo(File file) {
        VideoFileInfo info = new VideoFileInfo(file);

        // 解析视频元数据
        try {
            VideoUtil.parseMetadata(info);
        } catch (Exception e) {
            LogUtil.info("[WatchController] 无法解析视频元数据: " + file.getName());
        }

        VideoCompressConfig videoConfig = VideoCompressConfig.getDefault();
        videoConfig.setCrf(config.getVideoCrf());

        File outputDir = new File(config.getOutputFolderPath());
        if (!outputDir.exists()) outputDir.mkdirs();
        videoConfig.setOutputPath(outputDir.getAbsolutePath());

        CompressResult result = videoCompressService.compress(info, videoConfig);

        if (result.isSuccess()) {
            LogUtil.info("[WatchController] 视频压缩完成: " + file.getName()
                    + " → " + result.getOutputPath());

            if (config.isDeleteOriginal() && !file.getAbsolutePath().equals(result.getOutputPath())) {
                if (file.delete()) {
                    LogUtil.info("[WatchController] 已删除原文件: " + file.getName());
                }
            }

            showTrayNotification("视频压缩完成",
                    file.getName() + " → 已保存");
        } else {
            LogUtil.info("[WatchController] 视频压缩失败: " + file.getName()
                    + " — " + result.getErrorMessage());
        }
    }
}
