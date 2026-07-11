package com.nchu.imagecompress.controller;

import com.nchu.imagecompress.model.AppConfig;
import com.nchu.imagecompress.model.CompressConfig;
import com.nchu.imagecompress.model.CompressResult;
import com.nchu.imagecompress.model.FileInfo;
import com.nchu.imagecompress.model.ImageFileInfo;
import com.nchu.imagecompress.model.OutputFormat;
import com.nchu.imagecompress.model.ProgressChunk;
import com.nchu.imagecompress.model.Theme;
import com.nchu.imagecompress.model.VideoCompressConfig;
import com.nchu.imagecompress.model.VideoFileInfo;
import com.nchu.imagecompress.service.BatchCompressService;
import com.nchu.imagecompress.service.CompressService;
import com.nchu.imagecompress.service.ConfigService;
import com.nchu.imagecompress.service.FileManagerService;
import com.nchu.imagecompress.service.SmartRecommendService;
import com.nchu.imagecompress.service.VideoCompressService;
import com.nchu.imagecompress.util.ImageUtil;
import com.nchu.imagecompress.util.LogUtil;
import com.nchu.imagecompress.util.ThemeUtil;
import com.nchu.imagecompress.util.VideoUtil;
import com.nchu.imagecompress.view.FileListPanel;
import com.nchu.imagecompress.view.MainFrame;
import com.nchu.imagecompress.view.ParamPanel;
import com.nchu.imagecompress.view.PreviewPanel;
import com.nchu.imagecompress.view.ResultDialog;
import com.nchu.imagecompress.view.StatusBar;
import com.nchu.imagecompress.view.ToastNotification;
import com.nchu.imagecompress.view.VideoParamPanel;
import com.nchu.imagecompress.view.VideoPreviewPanel;

import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Desktop;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 主控制器 — 应用核心调度中心。
 *
 * <p>负责协调所有 View 组件和 Service 服务，实现完整的用户交互流程。
 * 实现 {@link MainControllerCallback} 接收 View 事件，
 * 通过 Service 执行业务逻辑，再更新 View 反馈结果。</p>
 *
 * <h3>核心流程</h3>
 * <pre>
 * 用户操作 → View 事件 → Controller → Service → Controller(EDT) → View 更新
 * </pre>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-08
 */
public class MainController implements MainControllerCallback {

    // ==================== 依赖组件 ====================

    private final MainFrame mainFrame;

    // 服务层
    private final ConfigService configService;
    private final FileManagerService fileManagerService;
    private final BatchCompressService batchCompressService;
    private final CompressService compressService;
    private final SmartRecommendService smartRecommendService;
    private final VideoCompressService videoCompressService;

    // 视图面板（快捷引用）
    private final FileListPanel fileListPanel;
    private final PreviewPanel previewPanel;
    private final ParamPanel paramPanel;
    private final VideoPreviewPanel videoPreviewPanel;
    private final VideoParamPanel videoParamPanel;
    private final StatusBar statusBar;

    // ==================== 状态 ====================

    /** 当前应用配置（内存中） */
    private AppConfig appConfig;

    /** 当前正在运行的压缩 Worker */
    private CompressWorker currentWorker;

    /** 当前压缩任务使用的配置（供完成回调使用） */
    private CompressConfig currentCompressConfig;

    /** 当前视频压缩配置 */
    private VideoCompressConfig currentVideoConfig;

    /** 视频压缩后台线程（简化版） */
    private Thread currentVideoThread;

    /** 上次打开的目录（用于文件选择器记忆） */
    private File lastOpenDir;

    /** 上次打开的视频目录 */
    private File lastVideoOpenDir;

    /** 视频文件列表（视频模式专用） */
    private final List<VideoFileInfo> videoFileList = new ArrayList<>();

    /** 窗口是否正在关闭 */
    private boolean shuttingDown = false;

    // ==================== 构造与初始化 ====================

    /**
     * 构造主控制器，注入所有依赖。
     *
     * @param mainFrame 主窗口
     */
    public MainController(MainFrame mainFrame) {
        this.mainFrame = mainFrame;

        // 初始化服务层
        this.configService = new ConfigService();
        this.fileManagerService = new FileManagerService();
        this.batchCompressService = new BatchCompressService();
        this.compressService = new CompressService();
        this.smartRecommendService = new SmartRecommendService();
        this.videoCompressService = new VideoCompressService();

        // 快捷引用
        this.fileListPanel = mainFrame.getFileListPanel();
        this.previewPanel = mainFrame.getPreviewPanel();
        this.paramPanel = mainFrame.getParamPanel();
        this.videoPreviewPanel = mainFrame.getVideoPreviewPanel();
        this.videoParamPanel = mainFrame.getVideoParamPanel();
        this.statusBar = mainFrame.getStatusBar();
    }

    /**
     * 初始化应用：加载配置 → 应用主题 → 恢复参数 → 绑定事件。
     */
    public void initialize() {
        // ① 加载配置
        appConfig = configService.loadConfig();
        LogUtil.info("[MainController] 配置已加载: " + appConfig);

        // ② 恢复上次的压缩模式
        if ("VIDEO".equals(appConfig.getCompressMode())) {
            mainFrame.switchCompressMode("VIDEO");
            restoreVideoParamsFromConfig();
        } else {
            restoreParamsFromConfig();
        }

        // ③ 绑定所有事件监听器
        bindEvents();

        // ④ 设置关闭窗口行为
        bindWindowClose();

        // ⑤ 状态栏就绪
        statusBar.setStatus("就绪，请导入图片开始", "ready");

        LogUtil.info("[MainController] 初始化完成");
    }

    // ==================== 事件绑定 ====================

    /**
     * 绑定所有 UI 事件监听器。
     */
    private void bindEvents() {
        // ---- 菜单事件 ----
        mainFrame.getImportFilesItem().addActionListener(e -> onImportFiles());
        mainFrame.getImportFolderItem().addActionListener(e -> onImportFolder());
        mainFrame.getExitItem().addActionListener(e -> onExit());
        mainFrame.getClearAllItem().addActionListener(e -> onClearAllFiles());
        mainFrame.getSettingsItem().addActionListener(e -> onOpenSettings());
        mainFrame.getToggleThemeItem().addActionListener(e -> onToggleTheme());
        mainFrame.getAboutItem().addActionListener(e -> onOpenAbout());

        // ---- 工具栏按钮 ----
        mainFrame.getImportBtn().addActionListener(e -> onImportFiles());
        mainFrame.getImportFolderBtn().addActionListener(e -> onImportFolder());
        mainFrame.getClearBtn().addActionListener(e -> onClearAllFiles());
        mainFrame.getCompressBtn().addActionListener(e -> onStartCompress());
        mainFrame.getThemeBtn().addActionListener(e -> onToggleTheme());

        // ---- 模式切换 ----
        mainFrame.getModeToggleBtn().addActionListener(e -> {
            boolean videoMode = mainFrame.getModeToggleBtn().isSelected();
            onModeToggle(videoMode);
        });

        // ---- 视频参数面板按钮 ----
        videoParamPanel.getCompressButton().addActionListener(e -> onStartCompress());
        videoParamPanel.getCancelButton().addActionListener(e -> onCancelCompress());
        videoParamPanel.getOutputDirButton().addActionListener(e -> onChooseVideoOutputDir());

        // ---- 视频 CRF 滑块实时更新 ----
        videoParamPanel.getCrfSlider().addChangeListener(e -> {
            if (!videoParamPanel.getCrfSlider().getValueIsAdjusting()) {
                int crf = videoParamPanel.getCrf();
                videoParamPanel.setCrfDisplay(crf);
                appConfig.setLastVideoCrf(crf);
                configService.saveConfig(appConfig);
            }
        });

        // ---- 参数面板按钮 ----
        paramPanel.getCompressButton().addActionListener(e -> onStartCompress());
        paramPanel.getCancelButton().addActionListener(e -> onCancelCompress());
        paramPanel.getOutputDirButton().addActionListener(e -> onChooseOutputDir());

        // ---- 质量滑块实时更新 ----
        paramPanel.getQualitySlider().addChangeListener(e -> {
            if (!paramPanel.getQualitySlider().getValueIsAdjusting()) {
                int quality = paramPanel.getQuality();
                paramPanel.setQualityDisplay(quality);
                onQualityChanged(quality);
            }
        });

        // ---- 文件列表选中事件 ----
        fileListPanel.getFileJList().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    onFileSelected(fileListPanel.getSelectedIndex());
                }
            }
        });

        // ---- 文件列表清空按钮 ----
        fileListPanel.getClearButton().addActionListener(e -> onClearAllFiles());

        // ---- 文件列表右键菜单 ----
        bindFileListPopupMenu();

        // ---- 文件列表 Delete 键移除 ----
        fileListPanel.getFileJList().addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_DELETE) {
                    int index = fileListPanel.getSelectedIndex();
                    if (index >= 0) {
                        onRemoveFile(index);
                    }
                }
            }
        });

        // ---- 拖拽导入 ----
        bindDragAndDrop();

        // ---- 智能推荐按钮（工具栏） ----
        // 当文件列表变化时自动触发智能推荐提示
        // （在 addFilesToList 中触发）

        LogUtil.info("[MainController] 事件绑定完成（含右键菜单、Delete 快捷键）");
    }

    /**
     * 绑定窗口关闭行为（保存配置 + 释放资源）。
     */
    private void bindWindowClose() {
        mainFrame.setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        mainFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onWindowClosing();
            }
        });

        // 窗口移动/调整大小时保存状态
        mainFrame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                saveWindowState();
            }

            @Override
            public void componentResized(ComponentEvent e) {
                saveWindowState();
            }
        });
    }

    // ==================== 文件导入 ====================

    @Override
    public void onImportFiles() {
        if (mainFrame.isVideoMode()) {
            onImportVideoFiles();
        } else {
            JFileChooser chooser = createImageFileChooser(true);
            int result = chooser.showOpenDialog(mainFrame);
            if (result == JFileChooser.APPROVE_OPTION) {
                File[] files = chooser.getSelectedFiles();
                lastOpenDir = chooser.getCurrentDirectory();
                importFilesInternal(files);
            }
        }
    }

    @Override
    public void onImportFolder() {
        if (mainFrame.isVideoMode()) {
            onImportVideoFolder();
        } else {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("选择图片文件夹");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setAcceptAllFileFilterUsed(false);
            if (lastOpenDir != null) {
                chooser.setCurrentDirectory(lastOpenDir);
            }

            int result = chooser.showOpenDialog(mainFrame);
            if (result == JFileChooser.APPROVE_OPTION) {
                File folder = chooser.getSelectedFile();
                lastOpenDir = folder;
                List<ImageFileInfo> imported = fileManagerService.importFolder(folder, true);
                addFilesToList(imported);
            }
        }
    }

    @Override
    public void onDragImport(List<File> files) {
        if (files == null || files.isEmpty()) return;
        if (mainFrame.isVideoMode()) {
            importVideoFiles(files.toArray(new File[0]));
        } else {
            importFilesInternal(files.toArray(new File[0]));
        }
    }

    /**
     * 内部统一导入逻辑。
     */
    private void importFilesInternal(File[] files) {
        statusBar.setStatus("正在导入文件...", "working");

        // 后台线程读取尺寸信息（避免阻塞 EDT）
        new Thread(() -> {
            List<ImageFileInfo> imported = fileManagerService.importFiles(files);

            SwingUtilities.invokeLater(() -> {
                if (imported.isEmpty()) {
                    statusBar.setStatus("未找到支持的图片文件", "error");
                    ToastNotification.warning("未找到支持的图片格式");
                    return;
                }
                addFilesToList(imported);
                statusBar.setStatus("已导入 " + imported.size() + " 个文件", "success");
            });
        }).start();
    }

    /**
     * 将导入的文件添加到列表（去重后）。
     */
    private void addFilesToList(List<ImageFileInfo> newFiles) {
        List<ImageFileInfo> existing = fileListPanel.getImageFileList();

        // 检查上限
        if (existing.size() + newFiles.size() > 500) {
            ToastNotification.warning("文件列表已达到上限 (500)，部分文件未导入");
        }

        List<ImageFileInfo> deduped = fileManagerService.deduplicate(existing, newFiles);
        int skipped = newFiles.size() - deduped.size();

        for (ImageFileInfo info : deduped) {
            fileListPanel.addFile(info);
        }

        // 更新按钮状态
        updateCompressButtonState();

        if (skipped > 0) {
            ToastNotification.info("已跳过 " + skipped + " 个重复文件");
        }
        if (!deduped.isEmpty()) {
            ToastNotification.success("成功导入 " + deduped.size() + " 个文件");
        }
    }

    // ==================== 文件移除 ====================

    @Override
    public void onRemoveFile(int index) {
        fileListPanel.removeFile(index);
        updateCompressButtonState();
        // 如果移除的是当前预览的文件，清空预览
        if (fileListPanel.getFileList().isEmpty()) {
            previewPanel.clearPreview();
        }
        statusBar.setStatus("已移除文件");
    }

    @Override
    public void onClearAllFiles() {
        if (currentWorker != null && !currentWorker.isDone()) {
            ToastNotification.warning("压缩任务进行中，请先取消或等待完成");
            return;
        }
        if (currentVideoThread != null && currentVideoThread.isAlive()) {
            ToastNotification.warning("视频压缩任务进行中，请先取消或等待完成");
            return;
        }

        if (mainFrame.isVideoMode()) {
            int count = videoFileList.size();
            if (count == 0) return;
            videoFileList.clear();
            fileListPanel.clearAllFiles();
            videoPreviewPanel.clearPreview();
            updateVideoCompressButtonState();
            statusBar.setStatus("已清空 " + count + " 个视频", "ready");
            ToastNotification.info("已清空视频列表");
            return;
        }

        int count = fileListPanel.getFileList().size();
        if (count == 0) return;

        fileListPanel.clearAllFiles();
        previewPanel.clearPreview();
        updateCompressButtonState();
        statusBar.setStatus("已清空 " + count + " 个文件", "ready");
        ToastNotification.info("已清空文件列表");
    }

    // ==================== 预览 ====================

    @Override
    public void onFileSelected(int index) {
        if (index < 0) {
            if (mainFrame.isVideoMode()) {
                videoPreviewPanel.clearPreview();
            } else {
                previewPanel.clearPreview();
            }
            return;
        }

        // 视频模式：显示视频元信息
        if (mainFrame.isVideoMode()) {
            FileInfo info = fileListPanel.getFileList().get(index);
            if (info instanceof VideoFileInfo) {
                videoPreviewPanel.showVideoInfo((VideoFileInfo) info);
            }
            return;
        }

        // 图片模式：生成预览图
        FileInfo selected = fileListPanel.getFileList().get(index);
        if (!(selected instanceof ImageFileInfo)) return;
        ImageFileInfo info = (ImageFileInfo) selected;
        if (info.getSourceFile() == null) return;

        new Thread(() -> {
            ImageIcon preview = ImageUtil.loadScaledIcon(info.getSourceFile(), 1024, 768);
            SwingUtilities.invokeLater(() -> {
                previewPanel.showOriginal(preview);
            });
        }).start();
    }

    // ==================== 压缩 ====================

    @Override
    public void onStartCompress() {
        if (mainFrame.isVideoMode()) {
            onStartVideoCompress();
            return;
        }

        // ① 校验
        List<ImageFileInfo> fileList = fileListPanel.getImageFileList();
        if (fileList.isEmpty()) {
            ToastNotification.warning("请先导入图片文件");
            return;
        }

        // 检查是否有运行中的任务
        if (currentWorker != null && !currentWorker.isDone()) {
            ToastNotification.warning("压缩任务正在进行中");
            return;
        }

        // ② 构建配置
        currentCompressConfig = buildCompressConfig();
        final CompressConfig config = currentCompressConfig;

        // 检查是否为无效压缩（质量100 + 不缩放 + 原格式）
        if (config.isEffectivelyNoOp()) {
            int choice = JOptionPane.showConfirmDialog(mainFrame,
                    "当前参数不会产生任何压缩效果：\n"
                            + "• 质量 = 100%（无损输出）\n"
                            + "• 不缩放尺寸\n"
                            + "• 保持原格式\n\n"
                            + "是否仍要继续？（仅执行格式转换/文件复制）",
                    "提示",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.INFORMATION_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }

        // ③ 更新 UI 状态
        setCompressingState(true);

        // 将所有文件状态重置为 PENDING
        for (ImageFileInfo info : fileList) {
            info.setFileInfoStatus(FileInfo.Status.PENDING);
        }
        fileListPanel.getFileJList().repaint();

        statusBar.showProgress(0, "0/" + fileList.size(), "准备压缩 " + fileList.size() + " 个文件...");

        // ④ 创建并启动 Worker
        currentWorker = new CompressWorker(
                batchCompressService,
                new ArrayList<>(fileList),  // 复制一份，避免并发修改
                config,
                null,                       // uiListener 通过 publish/process 处理
                this::onCompressComplete,
                this::onCompressCancelled
        ) {
            // 覆写 process 以便在 EDT 更新 UI
            @Override
            protected void process(List<ProgressChunk> chunks) {
                if (chunks == null || chunks.isEmpty()) return;
                ProgressChunk latest = chunks.get(chunks.size() - 1);

                // 更新进度条
                int overallPercent = (int) (latest.getOverallProgress() * 100);
                String detail = (latest.getCompletedCount() + latest.getFailedCount())
                        + "/" + latest.getTotalFiles();
                statusBar.showProgress(overallPercent, detail, latest.getStatusText());

                // 更新文件列表状态
                if (latest.getCurrentIndex() >= 0
                        && latest.getCurrentIndex() < fileListPanel.getFileList().size()) {
                    // 单文件完成时更新列表显示
                    fileListPanel.getFileJList().repaint();
                }
            }
        };

        currentWorker.execute();
        LogUtil.info("[MainController] 压缩任务已启动，共 " + fileList.size() + " 个文件");
    }

    @Override
    public void onCancelCompress() {
        if (mainFrame.isVideoMode()) {
            if (currentVideoThread != null && currentVideoThread.isAlive()) {
                LogUtil.info("[MainController] 用户取消视频压缩任务");
                currentVideoThread.interrupt();
                statusBar.setStatus("正在取消...", "working");
                videoParamPanel.getCancelButton().setEnabled(false);
            }
            return;
        }

        if (currentWorker != null && !currentWorker.isDone()) {
            LogUtil.info("[MainController] 用户取消压缩任务");
            currentWorker.cancel(true);
            // cancel(true) 会中断后台线程
            statusBar.setStatus("正在取消...", "working");
            paramPanel.getCancelButton().setEnabled(false);
        }
    }

    /**
     * 压缩完成回调（在 EDT 中执行）。
     */
    private void onCompressComplete() {
        try {
            List<CompressResult> results = currentWorker.get();
            int success = 0;
            int fail = 0;

            for (CompressResult r : results) {
                if (r.isSuccess()) {
                    success++;
                } else {
                    fail++;
                }
            }

            // 更新状态栏
            statusBar.hideProgress();
            String msg = String.format("完成 — 成功 %d, 失败 %d", success, fail);
            if (success > 0) {
                statusBar.flashSuccess(msg);
            } else {
                statusBar.flashError(msg);
            }

            // 弹出结果弹窗
            String outputDir = currentCompressConfig != null
                    ? currentCompressConfig.getOutputPath() : null;
            if (outputDir == null || outputDir.isEmpty()) {
                outputDir = configService.getDefaultOutputDir();
            }
            // 计算总耗时（取所有结果中最大的）
            long totalElapsed = 0;
            for (CompressResult r : results) {
                totalElapsed += r.getElapsedMs();
            }
            ResultDialog.show(mainFrame, results, outputDir, totalElapsed);

            // 刷新列表显示状态
            fileListPanel.getFileJList().repaint();
            updateCompressButtonState();

            // 自动预览第一个成功的文件
            for (CompressResult r : results) {
                if (r.isSuccess()) {
                    final File outputFile = new File(r.getOutputPath());
                    if (outputFile.exists()) {
                        new Thread(() -> {
                            ImageIcon preview = ImageUtil.loadScaledIcon(outputFile, 1024, 768);
                            SwingUtilities.invokeLater(() -> {
                                previewPanel.showEffect(preview);
                                if (r.getInputInfo() != null) {
                                    previewPanel.updateComparison(
                                            r.getInputInfo().getOriginalSize(),
                                            r.getOutputSize(),
                                            r.getCompressionRatio());
                                }
                            });
                        }).start();
                    }
                    break;
                }
            }

        } catch (Exception e) {
            LogUtil.error("[MainController] 获取压缩结果异常: " + e.getMessage());
            statusBar.flashError("压缩过程出现异常");
        } finally {
            setCompressingState(false);
            currentWorker = null;
        }
    }

    /**
     * 压缩取消回调（在 EDT 中执行）。
     */
    private void onCompressCancelled() {
        statusBar.hideProgress();
        statusBar.setStatus("任务已取消", "ready");
        ToastNotification.info("压缩任务已取消");
        fileListPanel.getFileJList().repaint();
        setCompressingState(false);
        currentWorker = null;
    }

    /**
     * 切换 UI 的压缩/空闲状态。
     */
    private void setCompressingState(boolean compressing) {
        if (mainFrame.isVideoMode()) {
            videoParamPanel.getCompressButton().setEnabled(!compressing);
            videoParamPanel.getCancelButton().setEnabled(compressing);
        } else {
            paramPanel.getCompressButton().setEnabled(!compressing);
            paramPanel.getCancelButton().setEnabled(compressing);
        }
        mainFrame.getCompressBtn().setEnabled(!compressing);
        mainFrame.getImportBtn().setEnabled(!compressing);
        mainFrame.getImportFolderBtn().setEnabled(!compressing);
        mainFrame.getClearBtn().setEnabled(!compressing);

        if (!compressing) {
            statusBar.hideProgress();
        }
    }

    /**
     * 从 ParamPanel 构建 CompressConfig。
     */
    private CompressConfig buildCompressConfig() {
        CompressConfig config = new CompressConfig();

        // 质量
        config.setQuality(paramPanel.getQuality());

        // 缩放模式
        switch (paramPanel.getScaleModeIndex()) {
            case 0: config.setScaleMode(CompressConfig.ScaleMode.NONE); break;
            case 1:
                config.setScaleMode(CompressConfig.ScaleMode.BY_PERCENT);
                config.setScalePercent(paramPanel.getScalePercent());
                break;
            case 2: config.setScaleMode(CompressConfig.ScaleMode.BY_MAX_SIZE); break;
            default: config.setScaleMode(CompressConfig.ScaleMode.NONE);
        }

        // 输出格式
        switch (paramPanel.getOutputFormatIndex()) {
            case 0: config.setOutputFormat(OutputFormat.ORIGINAL); break;
            case 1: config.setOutputFormat(OutputFormat.JPEG); break;
            case 2: config.setOutputFormat(OutputFormat.PNG); break;
            case 3: config.setOutputFormat(OutputFormat.BMP); break;
            default: config.setOutputFormat(OutputFormat.ORIGINAL);
        }

        // 命名规则
        switch (paramPanel.getNamingRuleIndex()) {
            case 0: config.setNamingRule(CompressConfig.NamingRule.ADD_SUFFIX); break;
            case 1: config.setNamingRule(CompressConfig.NamingRule.ADD_PREFIX); break;
            case 2: config.setNamingRule(CompressConfig.NamingRule.KEEP_ORIGINAL); break;
            default: config.setNamingRule(CompressConfig.NamingRule.ADD_SUFFIX);
        }

        // 覆盖
        config.setOverwrite(paramPanel.isOverwrite());

        // 输出路径
        String outputPath = appConfig.getRecentOutputDir();
        if (outputPath != null && !outputPath.isEmpty()) {
            config.setOutputPath(outputPath);
        } else {
            config.setOutputPath(configService.getDefaultOutputDir());
        }

        return config;
    }

    // ==================== 主题切换 ====================

    @Override
    public void onToggleTheme() {
        // 弹出主题选择面板
        Theme currentTheme = ThemeUtil.getCurrentTheme();
        javax.swing.JPopupMenu popup = com.nchu.imagecompress.view.ThemePopup.create(
                currentTheme, this::switchToTheme);
        popup.show(mainFrame.getThemeBtn(), 0, mainFrame.getThemeBtn().getHeight());
    }

    /**
     * 切换到指定主题并持久化。
     */
    private void switchToTheme(Theme theme) {
        if (theme == ThemeUtil.getCurrentTheme()) return;

        ThemeUtil.switchTheme(theme);
        appConfig.setTheme(theme);
        configService.saveConfig(appConfig);

        // 刷新主窗口中的自定义绘制组件
        mainFrame.repaint();

        ToastNotification.info("已切换至 " + theme.getDisplayName());
        LogUtil.info("[MainController] 主题已切换: " + theme.getDisplayName());
    }

    // ==================== 设置 ====================

    @Override
    public void onOpenSettings() {
        // 显示简易设置对话框
        JOptionPane.showMessageDialog(mainFrame,
                "设置功能将在后续版本中完善。\n"
                        + "当前版本支持：\n"
                        + "• 压缩参数记忆（自动保存）\n"
                        + "• 主题切换（Ctrl+T）\n"
                        + "• 输出目录选择\n"
                        + "• 窗口状态记忆",
                "设置",
                JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void onOpenAbout() {
        JOptionPane.showMessageDialog(mainFrame,
                "<html><h2>NCHU Image Compressor</h2>"
                        + "<p>版本 1.0.0</p>"
                        + "<p>基于 Java Swing + FlatLaf + Thumbnailator</p>"
                        + "<p>南昌航空大学 软件工程 暑期实训项目</p>"
                        + "<p>© 2026 NCHU-Student</p></html>",
                "关于",
                JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void onQualityChanged(int quality) {
        // 保存到配置（防抖写入）
        appConfig.setLastQuality(quality);
        configService.saveConfig(appConfig);
    }

    @Override
    public void onOutputDirSelected(File dir) {
        if (dir != null && dir.isDirectory()) {
            appConfig.setRecentOutputDir(dir.getAbsolutePath());
            configService.saveConfig(appConfig);
            ToastNotification.info("输出目录: " + dir.getName());
        }
    }

    /**
     * 用户点击选择输出目录按钮。
     */
    private void onChooseOutputDir() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择输出目录");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        // 设置默认目录
        String recentDir = appConfig.getRecentOutputDir();
        if (recentDir != null && !recentDir.isEmpty()) {
            File dir = new File(recentDir);
            if (dir.exists()) {
                chooser.setCurrentDirectory(dir);
            }
        }

        int result = chooser.showOpenDialog(mainFrame);
        if (result == JFileChooser.APPROVE_OPTION) {
            onOutputDirSelected(chooser.getSelectedFile());
        }
    }

    // ==================== 窗口生命周期 ====================

    @Override
    public void onWindowClosing() {
        if (shuttingDown) return;

        // 检查是否有运行中的任务
        if (currentWorker != null && !currentWorker.isDone()
                || currentVideoThread != null && currentVideoThread.isAlive()) {
            String task = (currentVideoThread != null && currentVideoThread.isAlive())
                    ? "视频" : "图片";
            int choice = JOptionPane.showConfirmDialog(mainFrame,
                    task + "压缩任务正在进行中，确定要退出吗？",
                    "确认退出",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
            if (currentWorker != null) currentWorker.cancel(true);
            if (currentVideoThread != null) currentVideoThread.interrupt();
        }

        shuttingDown = true;

        // 保存窗口状态
        saveWindowState();

        // 强制保存配置
        configService.shutdown();

        LogUtil.info("[MainController] 应用退出");
        mainFrame.dispose();
        System.exit(0);
    }

    @Override
    public void onExit() {
        onWindowClosing();
    }

    /**
     * 保存当前窗口状态到配置。
     */
    private void saveWindowState() {
        if (shuttingDown) return;

        appConfig.setWindowX(mainFrame.getX());
        appConfig.setWindowY(mainFrame.getY());
        appConfig.setWindowWidth(mainFrame.getWidth());
        appConfig.setWindowHeight(mainFrame.getHeight());
        appConfig.setMaximized((mainFrame.getExtendedState()
                & java.awt.Frame.MAXIMIZED_BOTH) != 0);

        // 保存分割面板位置
        if (mainFrame.getMainSplitPane() != null) {
            appConfig.setSplitPaneLeftWidth(
                    mainFrame.getMainSplitPane().getDividerLocation());
        }
        if (mainFrame.getRightSplitPane() != null) {
            appConfig.setSplitPaneRightDivider(
                    mainFrame.getRightSplitPane().getDividerLocation());
        }

        // 保存最近导入目录
        if (lastOpenDir != null) {
            appConfig.setRecentImportDir(lastOpenDir.getAbsolutePath());
        }
        if (lastVideoOpenDir != null) {
            appConfig.setRecentVideoImportDir(lastVideoOpenDir.getAbsolutePath());
        }

        // 保存视频压缩参数
        saveVideoParamsToConfig();

        configService.saveConfig(appConfig);
    }

    /**
     * 保存当前视频压缩参数到配置。
     */
    private void saveVideoParamsToConfig() {
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

    /**
     * 从配置恢复上次的参数设置。
     */
    private void restoreParamsFromConfig() {
        // 恢复质量
        paramPanel.getQualitySlider().setValue(appConfig.getLastQuality());
        paramPanel.setQualityDisplay(appConfig.getLastQuality());

        // 恢复输出格式
        String format = appConfig.getLastOutputFormat();
        if (format != null) {
            switch (format) {
                case "JPEG": paramPanel.getOutputFormatCombo().setSelectedIndex(1); break;
                case "PNG":  paramPanel.getOutputFormatCombo().setSelectedIndex(2); break;
                case "BMP":  paramPanel.getOutputFormatCombo().setSelectedIndex(3); break;
                default:     paramPanel.getOutputFormatCombo().setSelectedIndex(0);
            }
        }

        // 恢复缩放模式
        String scale = appConfig.getLastScaleMode();
        if ("BY_PERCENT".equals(scale)) {
            paramPanel.getScaleModeCombo().setSelectedIndex(1);
        } else if ("BY_MAX_SIZE".equals(scale)) {
            paramPanel.getScaleModeCombo().setSelectedIndex(2);
        }

        // 恢复命名规则
        String naming = appConfig.getLastNamingRule();
        if ("ADD_PREFIX".equals(naming)) {
            paramPanel.getNamingRuleCombo().setSelectedIndex(1);
        } else if ("KEEP_ORIGINAL".equals(naming)) {
            paramPanel.getNamingRuleCombo().setSelectedIndex(2);
        }

        // 恢复窗口位置
        if (appConfig.getWindowX() >= 0 && appConfig.getWindowY() >= 0) {
            mainFrame.setLocation(appConfig.getWindowX(), appConfig.getWindowY());
        }
        if (appConfig.getWindowWidth() > 0 && appConfig.getWindowHeight() > 0) {
            mainFrame.setSize(appConfig.getWindowWidth(), appConfig.getWindowHeight());
        }
        if (appConfig.isMaximized()) {
            mainFrame.setExtendedState(java.awt.Frame.MAXIMIZED_BOTH);
        }

        LogUtil.info("[MainController] 参数恢复完成");
    }

    /**
     * 更新压缩按钮的启用状态（列表为空时禁用）。
     */
    private void updateCompressButtonState() {
        boolean hasFiles = !fileListPanel.getFileList().isEmpty();
        paramPanel.getCompressButton().setEnabled(hasFiles);
        mainFrame.getCompressBtn().setEnabled(hasFiles);
    }

    // ==================== 右键菜单 ====================

    /**
     * 绑定文件列表的右键弹出菜单。
     */
    private void bindFileListPopupMenu() {
        JPopupMenu popupMenu = new JPopupMenu();

        JMenuItem previewItem = new JMenuItem("🔍 预览");
        previewItem.addActionListener(e -> {
            int index = fileListPanel.getSelectedIndex();
            if (index >= 0) onFileSelected(index);
        });
        popupMenu.add(previewItem);

        JMenuItem openOriginalItem = new JMenuItem("🖼 打开原图");
        openOriginalItem.addActionListener(e -> {
            FileInfo selected = fileListPanel.getSelectedFile();
            if (selected instanceof ImageFileInfo) {
                ImageFileInfo info = (ImageFileInfo) selected;
                if (info.getSourceFile().exists()) {
                    try {
                        Desktop.getDesktop().open(info.getSourceFile());
                    } catch (IOException ex) {
                        ToastNotification.error("无法打开文件: " + ex.getMessage());
                    }
                }
            }
        });
        popupMenu.add(openOriginalItem);

        JMenuItem openFolderItem = new JMenuItem("📁 打开所在文件夹");
        openFolderItem.addActionListener(e -> {
            FileInfo selected = fileListPanel.getSelectedFile();
            if (selected != null) {
                File parent = selected.getSourceFile().getParentFile();
                if (parent != null && parent.exists()) {
                    try {
                        Desktop.getDesktop().open(parent);
                    } catch (IOException ex) {
                        ToastNotification.error("无法打开文件夹: " + ex.getMessage());
                    }
                }
            }
        });
        popupMenu.add(openFolderItem);

        popupMenu.addSeparator();

        JMenuItem compressSingleItem = new JMenuItem("⚡ 单独压缩此文件");
        compressSingleItem.addActionListener(e -> {
            FileInfo selected = fileListPanel.getSelectedFile();
            if (selected instanceof ImageFileInfo) {
                compressSingleFile((ImageFileInfo) selected);
            }
        });
        popupMenu.add(compressSingleItem);

        popupMenu.addSeparator();

        JMenuItem removeItem = new JMenuItem("🗑 从列表移除");
        removeItem.addActionListener(e -> {
            int index = fileListPanel.getSelectedIndex();
            if (index >= 0) onRemoveFile(index);
        });
        popupMenu.add(removeItem);

        JMenuItem clearAllItem = new JMenuItem("清空列表");
        clearAllItem.addActionListener(e -> onClearAllFiles());
        popupMenu.add(clearAllItem);

        // 绑定到 JList
        fileListPanel.getFileJList().addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                showPopupIfTrigger(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showPopupIfTrigger(e);
            }

            private void showPopupIfTrigger(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = fileListPanel.getFileJList().locationToIndex(e.getPoint());
                    if (row >= 0 && !fileListPanel.getFileJList().isSelectedIndex(row)) {
                        fileListPanel.getFileJList().setSelectedIndex(row);
                    }
                    popupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
    }

    /**
     * 单独压缩一个文件。
     */
    private void compressSingleFile(ImageFileInfo info) {
        CompressConfig config = buildCompressConfig();
        setCompressingState(true);
        info.setFileInfoStatus(FileInfo.Status.PROCESSING);
        fileListPanel.getFileJList().repaint();
        statusBar.showProgress(0, "1/1", "正在压缩 " + info.getFileName() + "...");

        new Thread(() -> {
            CompressResult result = compressService.compress(info, config);
            SwingUtilities.invokeLater(() -> {
                info.setFileInfoStatus(result.isSuccess()
                        ? FileInfo.Status.SUCCESS
                        : FileInfo.Status.FAILED);
                if (!result.isSuccess()) {
                    info.setErrorMessage(result.getErrorMessage());
                }
                fileListPanel.getFileJList().repaint();
                statusBar.hideProgress();
                setCompressingState(false);

                if (result.isSuccess()) {
                    statusBar.flashSuccess(info.getFileName() + " 压缩完成");
                    ToastNotification.success("压缩完成: " + info.getFileName());

                    // 更新预览
                    File outputFile = new File(result.getOutputPath());
                    if (outputFile.exists()) {
                        new Thread(() -> {
                            ImageIcon preview = ImageUtil.loadScaledIcon(outputFile, 1024, 768);
                            SwingUtilities.invokeLater(() -> previewPanel.showEffect(preview));
                        }).start();
                    }

                    // 更新对比数据
                    long originalSize = info.getOriginalSize();
                    long compressedSize = result.getOutputSize();
                    double ratio = originalSize > 0
                            ? (1.0 - (double) compressedSize / originalSize) * 100 : 0;
                    previewPanel.updateComparison(originalSize, compressedSize, ratio);
                } else {
                    statusBar.flashError("压缩失败: " + info.getFileName());
                    ToastNotification.error("压缩失败: "
                            + (result.getErrorMessage() != null
                                ? result.getErrorMessage() : "未知错误"));
                }
            });
        }).start();
    }

    // ==================== 拖拽导入 ====================

    /**
     * 绑定拖拽导入功能。
     */
    private void bindDragAndDrop() {
        TransferHandler transferHandler = new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                // 只接受文件拖放
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;

                try {
                    Transferable transferable = support.getTransferable();
                    List<File> files = (List<File>) transferable.getTransferData(
                            DataFlavor.javaFileListFlavor);
                    if (files != null && !files.isEmpty()) {
                        onDragImport(files);
                        return true;
                    }
                } catch (Exception e) {
                    LogUtil.error("[MainController] 拖拽导入失败: " + e.getMessage());
                }
                return false;
            }
        };

        // 在主窗口和文件列表面板上启用拖放
        mainFrame.setTransferHandler(transferHandler);
        fileListPanel.setTransferHandler(transferHandler);
        fileListPanel.getFileJList().setTransferHandler(transferHandler);
    }

    // ==================== 工具方法 ====================

    /**
     * 创建图片文件选择器。
     */
    private JFileChooser createImageFileChooser(boolean multiSelect) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择图片文件");
        chooser.setMultiSelectionEnabled(multiSelect);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        // 图片文件过滤器
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "图片文件 (JPG, PNG, BMP, GIF, TIFF, ICO)",
                "jpg", "jpeg", "png", "bmp", "gif", "tiff", "tif", "ico");
        chooser.setFileFilter(filter);

        // 恢复上次打开的目录
        if (lastOpenDir != null && lastOpenDir.exists()) {
            chooser.setCurrentDirectory(lastOpenDir);
        } else {
            String recentDir = appConfig.getRecentImportDir();
            if (recentDir != null && !recentDir.isEmpty()) {
                File dir = new File(recentDir);
                if (dir.exists()) {
                    chooser.setCurrentDirectory(dir);
                }
            }
        }

        return chooser;
    }

    // ==================== 视频模式（v2.0） ====================

    @Override
    public void onModeToggle(boolean videoMode) {
        if (videoMode) {
            // 切换到视频模式
            mainFrame.switchCompressMode("VIDEO");
            // 同步 fileListPanel：清空图片列表，加载视频列表
            fileListPanel.setFileList(videoFileList);
            // 检测 FFmpeg 可用性
            if (!VideoUtil.checkFfmpegAvailable()) {
                ToastNotification.warning("FFmpeg 未安装，视频压缩功能不可用。请先安装 FFmpeg 4.0+");
            }
            // 恢复视频参数
            restoreVideoParamsFromConfig();
            updateVideoCompressButtonState();
            statusBar.setStatus("视频模式 — 请导入视频文件", "ready");
        } else {
            // 切换回图片模式
            mainFrame.switchCompressMode("IMAGE");
            // 同步 fileListPanel：清空视频列表（图片列表由用户重新导入或已在内存中）
            fileListPanel.clearAllFiles();
            // 恢复图片参数
            restoreParamsFromConfig();
            updateCompressButtonState();
            statusBar.setStatus("图片模式 — 请导入图片文件", "ready");
        }
        appConfig.setCompressMode(videoMode ? "VIDEO" : "IMAGE");
        configService.saveConfig(appConfig);
    }

    /**
     * 导入视频文件。
     */
    private void onImportVideoFiles() {
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
     * 导入视频文件夹。
     */
    private void onImportVideoFolder() {
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

            // 扫描文件夹中的视频文件
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
     * 内部统一视频导入逻辑。
     */
    private void importVideoFiles(File[] files) {
        statusBar.setStatus("正在导入视频文件...", "working");

        new Thread(() -> {
            List<VideoFileInfo> imported = new ArrayList<>();
            for (File file : files) {
                if (!VideoUtil.isSupportedVideo(file)) continue;
                VideoFileInfo info = new VideoFileInfo(file);
                // 后台解析元数据
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

                // 同步到 FileListPanel 显示
                fileListPanel.setFileList(imported);

                // 更新 UI
                updateVideoCompressButtonState();
                statusBar.setStatus("已导入 " + imported.size() + " 个视频", "success");
                ToastNotification.success("成功导入 " + imported.size() + " 个视频文件");

                // 自动预览第一个
                if (!imported.isEmpty()) {
                    videoPreviewPanel.showVideoInfo(imported.get(0));
                }
            });
        }).start();
    }

    /**
     * 开始视频压缩。
     */
    private void onStartVideoCompress() {
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

        // 构建配置
        currentVideoConfig = videoParamPanel.buildConfig();
        currentVideoConfig.setOutputPath(getVideoOutputPath());

        // 更新 UI
        setCompressingState(true);
        statusBar.showProgress(0, "0/" + videoFileList.size(),
                "准备压缩 " + videoFileList.size() + " 个视频...");

        // 后台线程执行
        final int total = videoFileList.size();
        currentVideoThread = new Thread(() -> {
            List<CompressResult> results = new ArrayList<>();

            for (int i = 0; i < total; i++) {
                if (Thread.currentThread().isInterrupted()) break;

                final VideoFileInfo info = videoFileList.get(i);
                final int idx = i;

                SwingUtilities.invokeLater(() -> {
                    statusBar.showProgress(
                            (int) ((double) idx / total * 100),
                            (idx + 1) + "/" + total,
                            "正在压缩 " + info.getFileName() + "...");
                });

                final CompressResult result = videoCompressService.compress(info, currentVideoConfig);
                results.add(result);

                // 更新进度
                final int finalI = i;
                SwingUtilities.invokeLater(() -> {
                    statusBar.showProgress(
                            (int) ((double) (finalI + 1) / total * 100),
                            (finalI + 1) + "/" + total,
                            result.isSuccess()
                                    ? info.getFileName() + " 完成"
                                    : info.getFileName() + " 失败");
                });
            }

            // 完成回调
            SwingUtilities.invokeLater(() -> onVideoCompressComplete(results));
        }, "VideoCompress-Thread");
        currentVideoThread.start();
        LogUtil.info("[MainController] 视频压缩任务已启动，共 " + total + " 个文件");
    }

    /**
     * 视频压缩完成回调（在 EDT 中执行）。
     */
    private void onVideoCompressComplete(List<CompressResult> results) {
        int success = 0;
        int fail = 0;
        long totalSaved = 0;
        long totalElapsed = 0;

        for (CompressResult r : results) {
            if (r.isSuccess()) {
                success++;
                totalSaved += r.getSavedBytes();
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

        // 弹出结果弹窗
        String outputDir = currentVideoConfig != null
                ? currentVideoConfig.getOutputPath() : "";
        ResultDialog.show(mainFrame, results, outputDir, totalElapsed);

        // 显示第一个成功的对比
        for (CompressResult r : results) {
            if (r.isSuccess()) {
                File outputFile = new File(r.getOutputPath());
                if (outputFile.exists() && r.getVideoInputInfo() != null) {
                    videoPreviewPanel.showComparison(
                            r.getVideoInputInfo().getOriginalSize(),
                            r.getOutputSize(),
                            r.getCompressionRatio());
                }
                break;
            }
        }

        setCompressingState(false);
        currentVideoThread = null;
    }

    /**
     * 选择视频输出目录。
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
     * 获取视频输出路径。
     */
    private String getVideoOutputPath() {
        String path = appConfig.getLastVideoOutputPath();
        if (path != null && !path.isEmpty()) return path;
        return configService.getDefaultOutputDir();
    }

    /**
     * 更新视频压缩按钮状态。
     */
    private void updateVideoCompressButtonState() {
        boolean hasFiles = !fileListPanel.getFileList().isEmpty();
        videoParamPanel.getCompressButton().setEnabled(hasFiles);
        mainFrame.getCompressBtn().setEnabled(hasFiles);
    }

    /**
     * 从配置恢复视频压缩参数。
     */
    private void restoreVideoParamsFromConfig() {
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
                videoParamPanel.setFpsMode(
                        VideoCompressConfig.FpsMode.valueOf(fps));
            } catch (IllegalArgumentException ignored) {}
        }
        String audio = appConfig.getLastVideoAudio();
        if (audio != null) {
            try {
                videoParamPanel.setAudioMode(
                        VideoCompressConfig.AudioMode.valueOf(audio));
            } catch (IllegalArgumentException ignored) {}
        }
        String format = appConfig.getLastVideoFormat();
        if (format != null) {
            try {
                videoParamPanel.setOutputFormat(
                        VideoCompressConfig.VideoFormat.valueOf(format));
            } catch (IllegalArgumentException ignored) {}
        }

        updateVideoCompressButtonState();
    }

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
