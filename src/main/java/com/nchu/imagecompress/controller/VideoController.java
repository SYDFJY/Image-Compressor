package com.nchu.imagecompress.controller;

import com.nchu.imagecompress.model.AppConfig;
import com.nchu.imagecompress.model.CompressResult;
import com.nchu.imagecompress.model.FileInfo;
import com.nchu.imagecompress.model.VideoCompressConfig;
import com.nchu.imagecompress.model.VideoFileInfo;
import com.nchu.imagecompress.service.ConfigService;
import com.nchu.imagecompress.service.VideoCompressService;
import com.nchu.imagecompress.util.LogUtil;
import com.nchu.imagecompress.util.VideoUtil;
import com.nchu.imagecompress.view.FileListPanel;
import com.nchu.imagecompress.view.MainFrame;
import com.nchu.imagecompress.view.ResultDialog;
import com.nchu.imagecompress.view.StatusBar;
import com.nchu.imagecompress.view.ToastNotification;
import com.nchu.imagecompress.view.VideoParamPanel;
import com.nchu.imagecompress.view.VideoPreviewPanel;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 视频压缩子控制器 — 管理视频模式的完整生命周期。
 *
 * <p>从 MainController 拆分出来，负责视频导入、元数据解析、预览、
 * 压缩执行（单版本 + 多变体批量）、播放以及 FFmpeg/FFplay 进程管理。</p>
 *
 * <p>通过 {@link VideoControllerCallback} 向 MainController 报告
 * 需要跨模式协调的状态变更。</p>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-17
 */
public class VideoController {

    // ==================== 依赖组件 ====================

    private final MainFrame mainFrame;
    private final FileListPanel fileListPanel;
    private final VideoPreviewPanel videoPreviewPanel;
    private final VideoParamPanel videoParamPanel;
    private final StatusBar statusBar;

    // 共享配置
    private final AppConfig appConfig;
    private final ConfigService configService;

    // 服务层
    private final VideoCompressService videoCompressService;

    // 回调桥梁
    private final VideoControllerCallback callback;

    // ==================== 状态 ====================

    /** 当前视频压缩配置 */
    private VideoCompressConfig currentVideoConfig;

    /** 视频压缩看门狗线程 */
    private Thread currentVideoThread;

    /** 视频批量压缩线程池 */
    private ExecutorService videoExecutor;

    /** 当前 ffplay 播放进程 */
    private Process currentFfplayProcess;

    /** 视频文件列表（视频模式专用，与 FileListPanel 显示同步） */
    private final List<VideoFileInfo> videoFileList = new ArrayList<>();

    /** 上次打开的视频目录 */
    private File lastVideoOpenDir;

    // ==================== 构造与初始化 ====================

    /**
     * 构造视频控制器。
     *
     * @param mainFrame     主窗口
     * @param appConfig     应用配置（共享引用）
     * @param configService 配置持久化服务
     * @param callback      回调桥梁
     */
    public VideoController(MainFrame mainFrame,
                           AppConfig appConfig,
                           ConfigService configService,
                           VideoControllerCallback callback) {
        this.mainFrame = mainFrame;
        this.appConfig = appConfig;
        this.configService = configService;
        this.callback = callback;

        this.fileListPanel = mainFrame.getFileListPanel();
        this.videoPreviewPanel = mainFrame.getVideoPreviewPanel();
        this.videoParamPanel = mainFrame.getVideoParamPanel();
        this.statusBar = mainFrame.getStatusBar();

        this.videoCompressService = new VideoCompressService();
    }

    /**
     * 绑定视频面板的 UI 事件监听器。
     */
    public void bindEvents() {
        videoParamPanel.getCompressButton().addActionListener(e -> onStartCompress());
        videoParamPanel.getCancelButton().addActionListener(e -> onCancelCompress());
        videoParamPanel.getOutputDirButton().addActionListener(e -> onChooseVideoOutputDir());

        videoParamPanel.getBatchModeCheckBox().addActionListener(e ->
                updateVideoCompressButtonState());
        videoParamPanel.setOnVariantChanged(this::updateVideoCompressButtonState);

        videoParamPanel.getCrfSlider().addChangeListener(e -> {
            int quality = videoParamPanel.getQualityDisplay();
            videoParamPanel.setCrfDisplay(quality);
            if (!videoParamPanel.getCrfSlider().getValueIsAdjusting()) {
                appConfig.setLastVideoCrf(videoParamPanel.getCrf());
                configService.saveConfig(appConfig);
                updateEstimatedVideoSize();
            }
        });

        LogUtil.info("[VideoController] 事件绑定完成");
    }

    /**
     * 从配置恢复上次的视频压缩参数。
     */
    public void restoreParamsFromConfig() {
        if (appConfig.getLastVideoCrf() > 0) {
            videoParamPanel.setCrf(appConfig.getLastVideoCrf());
        }
        String resolution = appConfig.getLastVideoResolution();
        if (resolution != null) {
            try {
                videoParamPanel.setResolutionMode(
                        VideoCompressConfig.ResolutionMode.valueOf(resolution));
            } catch (IllegalArgumentException ignored) {}
        }
        String fps = appConfig.getLastVideoFps();
        if (fps != null) {
            try {
                videoParamPanel.setFpsMode(VideoCompressConfig.FpsMode.valueOf(fps));
            } catch (IllegalArgumentException ignored) {}
        }
        String audio = appConfig.getLastVideoAudio();
        if (audio != null) {
            try {
                videoParamPanel.setAudioMode(VideoCompressConfig.AudioMode.valueOf(audio));
            } catch (IllegalArgumentException ignored) {}
        }
        String format = appConfig.getLastVideoFormat();
        if (format != null) {
            try {
                videoParamPanel.setOutputFormat(VideoCompressConfig.VideoFormat.valueOf(format));
            } catch (IllegalArgumentException ignored) {}
        }

        updateVideoCompressButtonState();
    }

    // ==================== 文件导入 ====================

    /**
     * 弹出视频文件选择器并导入。
     */
    public void onImportFiles() {
        JFileChooser chooser = createVideoFileChooser(true);
        int result = chooser.showOpenDialog(mainFrame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File[] files = chooser.getSelectedFiles();
            lastVideoOpenDir = chooser.getCurrentDirectory();
            appConfig.setRecentVideoImportDir(lastVideoOpenDir.getAbsolutePath());
            configService.saveConfig(appConfig);
            importVideoFiles(files);
        }
    }

    /**
     * 弹出视频文件夹选择器并导入。
     */
    public void onImportFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择视频文件夹");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        if (lastVideoOpenDir != null) {
            chooser.setCurrentDirectory(lastVideoOpenDir);
        }

        int result = chooser.showOpenDialog(mainFrame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File folder = chooser.getSelectedFile();
            lastVideoOpenDir = folder;

            File[] files = folder.listFiles((dir, name) -> {
                String lower = name.toLowerCase();
                return lower.endsWith(".mp4") || lower.endsWith(".avi")
                        || lower.endsWith(".mov") || lower.endsWith(".mkv")
                        || lower.endsWith(".webm") || lower.endsWith(".flv")
                        || lower.endsWith(".wmv") || lower.endsWith(".m4v");
            });

            if (files != null && files.length > 0) {
                importVideoFiles(files);
            } else {
                ToastNotification.warning("文件夹中未找到视频文件");
            }
        }
    }

    /**
     * 处理拖拽导入的视频文件。
     */
    public void onDragImport(List<File> files) {
        if (files == null || files.isEmpty()) return;
        importVideoFiles(files.toArray(new File[0]));
    }

    /**
     * 内部统一视频导入逻辑（后台解析元数据，EDT 更新列表）。
     */
    private void importVideoFiles(File[] files) {
        statusBar.setStatus("正在导入视频文件...", "working");

        new Thread(() -> {
            List<VideoFileInfo> imported = new ArrayList<>();
            for (File file : files) {
                if (!VideoUtil.isSupportedVideo(file)) continue;
                VideoFileInfo info = new VideoFileInfo(file);
                VideoUtil.parseMetadata(info);
                imported.add(info);
            }

            SwingUtilities.invokeLater(() -> {
                if (imported.isEmpty()) {
                    statusBar.setStatus("未找到支持的视频文件", "error");
                    ToastNotification.warning("未找到支持的视频格式");
                    return;
                }
                videoFileList.clear();
                videoFileList.addAll(imported);
                // 按文件修改时间倒序排列（最新的在前）
                Collections.sort(videoFileList, new Comparator<VideoFileInfo>() {
                    @Override
                    public int compare(VideoFileInfo a, VideoFileInfo b) {
                        long t1 = a.getSourceFile() != null ? a.getSourceFile().lastModified() : 0L;
                        long t2 = b.getSourceFile() != null ? b.getSourceFile().lastModified() : 0L;
                        return Long.compare(t2, t1);
                    }
                });
                fileListPanel.setFileList(videoFileList);
                updateVideoCompressButtonState();
                statusBar.setStatus("已导入 " + imported.size() + " 个视频", "success");
                ToastNotification.success("成功导入 " + imported.size() + " 个视频文件");

                if (!imported.isEmpty()) {
                    videoPreviewPanel.showVideoInfo(imported.get(0));
                }
            });
        }).start();
    }

    // ==================== 预览 ====================

    /**
     * 选中视频文件时显示元信息。
     */
    /** 当前选中视频的原始大小（用于预估输出大小），-1 表示无选中 */
    private long selectedVideoOriginalSize = -1;

    public void onFileSelected(int index) {
        if (index < 0) {
            videoPreviewPanel.clearPreview();
            videoParamPanel.hideEstimatedSize();
            selectedVideoOriginalSize = -1;
            return;
        }
        FileInfo info = fileListPanel.getFileList().get(index);
        if (info instanceof VideoFileInfo) {
            VideoFileInfo vInfo = (VideoFileInfo) info;
            selectedVideoOriginalSize = vInfo.getOriginalSize();
            videoPreviewPanel.showVideoInfo(vInfo);
            updateEstimatedVideoSize();
        }
    }

    /**
     * 清除视频预览并停止播放。
     */
    public void clearPreview() {
        videoPreviewPanel.clearPreview();
        videoParamPanel.hideEstimatedSize();
        selectedVideoOriginalSize = -1;
    }

    /**
     * 根据当前 CRF 查表预估输出大小范围。
     */
    private void updateEstimatedVideoSize() {
        if (selectedVideoOriginalSize <= 0) {
            videoParamPanel.hideEstimatedSize();
            return;
        }
        int quality = videoParamPanel.getQualityDisplay();
        int lowPct, highPct;
        if (quality >= 90) { lowPct = 70; highPct = 100; }
        else if (quality >= 70) { lowPct = 50; highPct = 80; }
        else if (quality >= 50) { lowPct = 30; highPct = 55; }
        else if (quality >= 30) { lowPct = 20; highPct = 40; }
        else if (quality >= 15) { lowPct = 10; highPct = 25; }
        else { lowPct = 5; highPct = 15; }

        long lowBytes = selectedVideoOriginalSize * lowPct / 100;
        long highBytes = selectedVideoOriginalSize * highPct / 100;
        String lowSize = formatVideoSize(lowBytes);
        String highSize = formatVideoSize(highBytes);
        videoParamPanel.setEstimatedSize("预估输出: 约 " + lowSize + " ~ " + highSize);
    }

    /** 视频文件大小格式化 */
    private static String formatVideoSize(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        return String.format("%.2f GB", mb / 1024.0);
    }

    // ==================== 压缩调度 ====================

    /**
     * 开始视频压缩（根据批量模式分支）。
     */
    public void onStartCompress() {
        if (videoFileList.isEmpty()) {
            ToastNotification.warning("请先导入视频文件");
            return;
        }

        if (currentVideoThread != null && currentVideoThread.isAlive()) {
            ToastNotification.warning("视频压缩任务正在进行中");
            return;
        }

        if (!VideoUtil.checkFfmpegAvailable()) {
            ToastNotification.error("FFmpeg 未安装，无法压缩视频。请先安装 FFmpeg 4.0+");
            return;
        }

        if (videoParamPanel.isBatchMode()) {
            List<VideoCompressConfig.VariantPreset> variants = videoParamPanel.getBatchVariants();
            if (variants.isEmpty()) {
                ToastNotification.warning("请至少添加一个导出变体");
                return;
            }
            startBatchVideoCompress(variants);
        } else {
            startSingleVideoCompress();
        }
    }

    /**
     * 取消视频压缩。
     */
    public void onCancelCompress() {
        if (videoExecutor != null && !videoExecutor.isShutdown()) {
            videoExecutor.shutdownNow();
        }
        if (currentVideoThread != null && currentVideoThread.isAlive()) {
            LogUtil.info("[VideoController] 用户取消视频压缩任务");
            currentVideoThread.interrupt();
            statusBar.setStatus("正在取消...", "working");
            videoParamPanel.getCancelButton().setEnabled(false);
        }
    }

    // ==================== 单版本压缩 ====================

    /**
     * 单版本视频压缩（多线程并行，最多 2 线程）。
     */
    private void startSingleVideoCompress() {
        currentVideoConfig = videoParamPanel.buildConfig();
        if (currentVideoConfig == null) return; // 参数收集失败（Toast 已在 buildConfig 中弹出）

        // 检查是否为无效压缩（保持原格式 + 无画质/分辨率变化）
        if (currentVideoConfig.isEffectivelyNoOp()) {
            ToastNotification.warning("当前参数不会产生有效压缩（保持原格式、无画质变化、无分辨率变化），请调整参数后重试。");
            return;
        }

        currentVideoConfig.setOutputPath(getVideoOutputPath());
        final int total = videoFileList.size();
        callback.setCompressingState(true);
        statusBar.showProgress(0, "0/" + total,
                "并行压缩 " + total + " 个视频（2 线程）...");

        videoExecutor = Executors.newFixedThreadPool(2);
        final List<CompressResult> results = new ArrayList<>(total);
        final int[] completed = {0};

        for (int i = 0; i < total; i++) {
            final VideoFileInfo info = videoFileList.get(i);
            videoExecutor.submit(() -> {
                if (Thread.currentThread().isInterrupted()) return;
                final CompressResult result = videoCompressService.compress(info, currentVideoConfig);

                synchronized (results) {
                    results.add(result);
                    completed[0]++;
                    final int done = completed[0];
                    SwingUtilities.invokeLater(() -> {
                        statusBar.showProgress(
                                (int) ((double) done / total * 100),
                                done + "/" + total,
                                (result.isSuccess() ? "[OK] " : "[FAIL] ") + info.getFileName());
                    });
                }
            });
        }

        videoExecutor.shutdown();
        currentVideoThread = new Thread(() -> {
            try {
                videoExecutor.awaitTermination(12, TimeUnit.HOURS);
            } catch (InterruptedException e) {
                videoExecutor.shutdownNow();
            }
            SwingUtilities.invokeLater(() -> onVideoCompressComplete(results));
        }, "VideoCompress-Watchdog");
        currentVideoThread.start();
        LogUtil.info("[VideoController] 视频并行压缩已启动，共 " + total + " 个文件（2 线程）");
    }

    // ==================== 批量多变体压缩 ====================

    /**
     * 批量多变体视频压缩（外层文件 × 内层变体配置）。
     */
    private void startBatchVideoCompress(List<VideoCompressConfig.VariantPreset> variants) {
        VideoCompressConfig baseConfig = videoParamPanel.buildConfig();
        baseConfig.setOutputPath(getVideoOutputPath());
        this.currentVideoConfig = baseConfig;

        final int fileCount = videoFileList.size();
        final int variantCount = variants.size();
        final int totalOps = fileCount * variantCount;

        callback.setCompressingState(true);
        statusBar.showProgress(0, "0/" + totalOps,
                "并行导出 " + fileCount + " 文件 × " + variantCount + " 变体（2 线程）...");

        videoExecutor = Executors.newFixedThreadPool(2);
        final List<CompressResult> results = java.util.Collections.synchronizedList(new ArrayList<>());
        final int[] completed = {0};

        for (int fi = 0; fi < fileCount; fi++) {
            final VideoFileInfo info = videoFileList.get(fi);
            for (int vi = 0; vi < variantCount; vi++) {
                final VideoCompressConfig.VariantPreset variant = variants.get(vi);
                final String variantLabel = variant.buildSuffix().replaceFirst("^_", "");
                final VideoCompressConfig mergedConfig = variant.mergeWith(baseConfig);

                videoExecutor.submit(() -> {
                    if (Thread.currentThread().isInterrupted()) return;
                    final CompressResult result = videoCompressService.compress(info, mergedConfig);
                    result.setVariantLabel(variantLabel);
                    results.add(result);

                    synchronized (completed) {
                        completed[0]++;
                        final int done = completed[0];
                        SwingUtilities.invokeLater(() -> {
                            String status = result.isSuccess()
                                    ? "[OK] " + info.getFileName() + " → " + variantLabel
                                    : "[FAIL] " + info.getFileName() + " → " + variantLabel;
                            statusBar.showProgress(
                                    (int) ((double) done / totalOps * 100),
                                    done + "/" + totalOps, status);
                        });
                    }
                });
            }
        }

        videoExecutor.shutdown();
        currentVideoThread = new Thread(() -> {
            try {
                videoExecutor.awaitTermination(12, TimeUnit.HOURS);
            } catch (InterruptedException e) {
                videoExecutor.shutdownNow();
            }
            final List<CompressResult> finalResults = new ArrayList<>(results);
            SwingUtilities.invokeLater(() -> onVideoCompressComplete(finalResults));
        }, "VideoCompress-Batch-Watchdog");
        currentVideoThread.start();
        LogUtil.info("[VideoController] 批量视频压缩已启动，共 "
                + fileCount + " 文件 × " + variantCount + " 变体 = " + totalOps + " 个任务");
    }

    // ==================== 压缩完成回调 ====================

    /**
     * 视频压缩完成回调（在 EDT 中执行）。
     */
    private void onVideoCompressComplete(List<CompressResult> results) {
        int success = 0;
        int fail = 0;
        long totalElapsed = 0;

        for (CompressResult r : results) {
            if (r.isSuccess()) {
                success++;
                totalElapsed += r.getElapsedMs();
            } else {
                fail++;
            }
        }

        statusBar.hideProgress();
        String msg = String.format("完成 — 成功 %d, 失败 %d", success, fail);
        if (success > 0) {
            statusBar.flashSuccess(msg);
        } else {
            statusBar.flashError(msg);
        }

        String outputDir = currentVideoConfig != null
                ? currentVideoConfig.getOutputPath() : "";
        ResultDialog.show(mainFrame, results, outputDir, totalElapsed, appConfig);

        // 将压缩结果写入 VideoFileInfo Model
        for (CompressResult r : results) {
            if (r.isSuccess() && r.getVideoInputInfo() != null) {
                VideoFileInfo info = r.getVideoInputInfo();
                info.setCompressedSize(r.getOutputSize());
                info.setCompressedPath(r.getOutputPath());

                if (info.getDurationSeconds() > 0) {
                    long estimatedBitrate = (r.getOutputSize() * 8)
                            / (long) info.getDurationSeconds();
                    info.setCompressedBitrate(estimatedBitrate);
                }

                VideoCompressConfig cfg = currentVideoConfig;
                if (cfg != null) {
                    if (cfg.getOutputFormat() == VideoCompressConfig.VideoFormat.WEBM) {
                        info.setCompressedCodec("vp9");
                    } else {
                        info.setCompressedCodec("h264");
                    }
                    if (cfg.getResolutionMode() != VideoCompressConfig.ResolutionMode.ORIGINAL) {
                        info.setCompressedWidth(cfg.getResolutionMode().getMaxWidth());
                        info.setCompressedHeight(cfg.getResolutionMode().getMaxHeight());
                    } else {
                        info.setCompressedWidth(info.getWidth());
                        info.setCompressedHeight(info.getHeight());
                    }
                }
            }
        }

        // 显示第一个成功文件的压缩对比
        for (CompressResult r : results) {
            if (r.isSuccess() && r.getVideoInputInfo() != null) {
                videoPreviewPanel.showCompressionResult(r.getVideoInputInfo());
                break;
            }
        }

        callback.setCompressingState(false);
        currentVideoThread = null;
    }

    // ==================== 输出目录 ====================

    /**
     * 弹出视频输出目录选择器。
     */
    private void onChooseVideoOutputDir() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择视频输出目录");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        String recentDir = appConfig.getLastVideoOutputPath();
        if (recentDir != null && !recentDir.isEmpty()) {
            File dir = new File(recentDir);
            if (dir.exists()) chooser.setCurrentDirectory(dir);
        }

        int result = chooser.showOpenDialog(mainFrame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File dir = chooser.getSelectedFile();
            appConfig.setLastVideoOutputPath(dir.getAbsolutePath());
            configService.saveConfig(appConfig);
            ToastNotification.info("视频输出目录: " + dir.getName());
        }
    }

    /**
     * 获取视频输出路径（优先使用用户指定，其次默认）。
     */
    private String getVideoOutputPath() {
        String path = appConfig.getLastVideoOutputPath();
        if (path != null && !path.isEmpty()) return path;
        return configService.getDefaultOutputDir();
    }

    // ==================== 按钮状态 ====================

    /**
     * 更新视频压缩按钮的启用状态和文案。
     */
    public void updateVideoCompressButtonState() {
        boolean hasFiles = !fileListPanel.getFileList().isEmpty();
        videoParamPanel.getCompressButton().setEnabled(hasFiles);
        videoParamPanel.updateCompressButtonText(hasFiles ? videoFileList.size() : 0);
    }

    // ==================== 配置持久化 ====================

    /**
     * 保存当前视频压缩参数到配置（供 MainController 窗口关闭时调用）。
     */
    public void saveParamsToConfig() {
        appConfig.setLastVideoCrf(videoParamPanel.getCrf());
        appConfig.setLastVideoFormat(
                VideoCompressConfig.VideoFormat.values()[videoParamPanel.getOutputFormatIndex()].name());
        appConfig.setLastVideoResolution(
                VideoCompressConfig.ResolutionMode.values()[videoParamPanel.getResolutionIndex()].name());
        appConfig.setLastVideoFps(
                VideoCompressConfig.FpsMode.values()[videoParamPanel.getFpsIndex()].name());
        appConfig.setLastVideoAudio(
                videoParamPanel.getAudioIndex() == 1
                        ? VideoCompressConfig.AudioMode.REMOVE.name()
                        : VideoCompressConfig.AudioMode.KEEP.name());
    }

    // ==================== 视频播放 ====================

    /**
     * 播放原始视频文件。
     */
    public void onPlayOriginalVideo(VideoFileInfo info) {
        if (info == null || info.getSourceFile() == null || !info.getSourceFile().exists()) {
            ToastNotification.error("视频文件不存在");
            return;
        }
        if (videoPreviewPanel.getVideoPlayerPanel().isVlcUsable()
                || VideoUtil.checkFfmpegAvailable()) {
            videoPreviewPanel.showVideoInfo(info);
        } else {
            playVideoFile(info.getSourceFile());
        }
    }

    /**
     * 播放压缩后视频文件。
     */
    public void onPlayCompressedVideo(VideoFileInfo info) {
        if (info == null || info.getCompressedPath() == null) {
            ToastNotification.error("压缩视频不存在");
            return;
        }
        final File compressedFile = new File(info.getCompressedPath());
        if (!compressedFile.exists()) {
            ToastNotification.error("压缩视频文件已被删除或移动");
            return;
        }
        if (videoPreviewPanel.getVideoPlayerPanel().isVlcUsable()
                || VideoUtil.checkFfmpegAvailable()) {
            videoPreviewPanel.getVideoPlayerPanel().play(compressedFile);
        } else {
            playVideoFile(compressedFile);
        }
    }

    /**
     * 启动 ffplay 外部播放视频（后台线程，不阻塞 EDT）。
     */
    private void playVideoFile(final File videoFile) {
        statusBar.setStatus("正在启动播放器...", "working");

        new Thread(() -> {
            try {
                if (!VideoUtil.checkFfplayAvailable()) {
                    SwingUtilities.invokeLater(() -> {
                        statusBar.setStatus("就绪", "ready");
                        ToastNotification.error("FFplay 未安装，无法播放视频。");
                    });
                    return;
                }

                killFfplayProcess();

                currentFfplayProcess = VideoUtil.playVideo(videoFile);

                SwingUtilities.invokeLater(() -> {
                    statusBar.setStatus("正在播放: " + videoFile.getName(), "ready");
                });

                currentFfplayProcess.waitFor();

                SwingUtilities.invokeLater(() -> {
                    statusBar.setStatus("就绪", "ready");
                });

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                LogUtil.error("[VideoController] ffplay 启动失败: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    statusBar.setStatus("播放失败", "error");
                    ToastNotification.error("无法启动播放器: " + e.getMessage());
                });
            } finally {
                currentFfplayProcess = null;
            }
        }, "FFplay-Thread").start();
    }

    /**
     * 安全终止当前 ffplay 进程。
     */
    public void killFfplayProcess() {
        if (currentFfplayProcess != null && currentFfplayProcess.isAlive()) {
            currentFfplayProcess.destroy();
            try {
                currentFfplayProcess.waitFor(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                currentFfplayProcess.destroyForcibly();
                Thread.currentThread().interrupt();
            }
            currentFfplayProcess = null;
        }
    }

    // ==================== 资源释放 ====================

    /**
     * 释放视频相关资源（VLCJ 播放器等）。
     */
    public void release() {
        killFfplayProcess();
        videoPreviewPanel.release();
    }

    // ==================== 状态查询 ====================

    /**
     * 是否有正在进行的压缩任务。
     */
    public boolean isCompressing() {
        return currentVideoThread != null && currentVideoThread.isAlive();
    }

    /**
     * 获取视频文件列表。
     */
    public List<VideoFileInfo> getVideoFileList() {
        return videoFileList;
    }

    /**
     * 获取视频文件列表（FileInfo 形式，用于文件列表同步）。
     */
    public List<? extends FileInfo> getFileList() {
        return videoFileList;
    }

    /**
     * 是否为空列表。
     */
    public boolean isFileListEmpty() {
        return videoFileList.isEmpty();
    }

    /**
     * 获取上次打开的视频目录（供保存窗口状态）。
     */
    public File getLastVideoOpenDir() {
        return lastVideoOpenDir;
    }

    /**
     * 获取文件数量。
     */
    public int getFileCount() {
        return videoFileList.size();
    }

    /**
     * 获取视频参数面板（供 MainController 设置压缩状态）。
     */
    public VideoParamPanel getVideoParamPanel() {
        return videoParamPanel;
    }

    // ==================== 回调接口 ====================

    /**
     * 视频控制器向 MainController 的回调桥梁。
     */
    public interface VideoControllerCallback {
        /**
         * 切换工具栏和面板的压缩/空闲状态。
         */
        void setCompressingState(boolean compressing);
    }

    // ==================== 工具方法 ====================

    /**
     * 创建视频文件选择器。
     */
    private JFileChooser createVideoFileChooser(boolean multiSelect) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择视频文件");
        chooser.setMultiSelectionEnabled(multiSelect);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "视频文件 (MP4, AVI, MOV, MKV, WebM, FLV, WMV)",
                "mp4", "avi", "mov", "mkv", "webm", "flv", "wmv", "m4v", "ts", "3gp");
        chooser.setFileFilter(filter);

        if (lastVideoOpenDir != null && lastVideoOpenDir.exists()) {
            chooser.setCurrentDirectory(lastVideoOpenDir);
        } else {
            String recentDir = appConfig.getRecentVideoImportDir();
            if (recentDir != null && !recentDir.isEmpty()) {
                File dir = new File(recentDir);
                if (dir.exists()) chooser.setCurrentDirectory(dir);
            }
        }

        return chooser;
    }
}
