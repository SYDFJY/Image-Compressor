package com.nchu.imagecompress.controller;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.nchu.imagecompress.model.AppConfig;
import com.nchu.imagecompress.model.FileInfo;
import com.nchu.imagecompress.model.ImageFileInfo;
import com.nchu.imagecompress.model.Theme;
import com.nchu.imagecompress.model.VideoFileInfo;
import com.nchu.imagecompress.service.ConfigService;
import com.nchu.imagecompress.service.FileManagerService;
import com.nchu.imagecompress.util.LogUtil;
import com.nchu.imagecompress.util.ThemeUtil;
import com.nchu.imagecompress.util.VideoUtil;
import com.nchu.imagecompress.view.FileListPanel;
import com.nchu.imagecompress.view.MainFrame;
import com.nchu.imagecompress.view.SettingsDialog;
import com.nchu.imagecompress.view.StatusBar;
import com.nchu.imagecompress.view.ToastNotification;

import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
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
 * 主控制器 — 应用核心路由器。
 *
 * <p>负责：</p>
 * <ul>
 *   <li>应用初始化与生命周期管理</li>
 *   <li>主题切换、设置、快捷键、撤销等共享操作</li>
 *   <li>图片/视频模式切换调度</li>
 *   <li>将图片压缩委托给 {@link ImageController}</li>
 *   <li>将视频压缩委托给 {@link VideoController}</li>
 * </ul>
 *
 * <p>实现 {@link MainControllerCallback} 接收 View 事件，
 * 根据当前模式路由到对应的子控制器执行。</p>
 *
 * @author NCHU-Student
 * @version 2.0.0
 * @since 2026-07-08
 */
public class MainController implements MainControllerCallback,
        ImageController.ImageControllerCallback,
        VideoController.VideoControllerCallback {

    // ==================== 依赖组件 ====================

    private final MainFrame mainFrame;

    // 服务层（共享）
    private final ConfigService configService;
    private final FileManagerService fileManagerService;

    // 子控制器（在 initialize() 中延迟创建，因为需要先加载 appConfig）
    private ImageController imageController;
    private VideoController videoController;

    // 视图面板（快捷引用）
    private final FileListPanel fileListPanel;
    private final StatusBar statusBar;

    // ==================== 状态 ====================

    /** 当前应用配置（内存中） */
    private AppConfig appConfig;

    /** 窗口是否正在关闭 */
    private boolean shuttingDown = false;

    /** 切换到视频模式前保存的图片文件列表（切回图片模式时恢复，修复清空 Bug） */
    private List<FileInfo> savedImageFileList = null;

    // 撤销支持
    private FileInfo undoRemovedFile = null;
    private int undoRemovedIndex = -1;
    private List<FileInfo> undoClearAllFiles = null;
    private final javax.swing.Timer undoTimer = new javax.swing.Timer(5000, e -> clearUndoState());

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

        // 快捷引用
        this.fileListPanel = mainFrame.getFileListPanel();
        this.statusBar = mainFrame.getStatusBar();

        // 子控制器在 initialize() 中延迟创建（需要先加载 appConfig）
    }

    /**
     * 初始化应用：加载配置 → 应用主题 → 恢复参数 → 绑定事件。
     */
    public void initialize() {
        // ① 加载配置
        appConfig = configService.loadConfig();
        LogUtil.info("[MainController] 配置已加载: " + appConfig);

        // ② 注入配置到子控制器
        imageController = new ImageController(
                mainFrame, appConfig, configService, fileManagerService, this);
        videoController = new VideoController(
                mainFrame, appConfig, configService, this);

        // ③ 恢复上次的压缩模式
        if ("VIDEO".equals(appConfig.getCompressMode())) {
            mainFrame.switchCompressMode("VIDEO");
            videoController.restoreParamsFromConfig();
        } else {
            imageController.restoreParamsFromConfig();
        }

        // ④ 恢复窗口位置
        if (appConfig.getWindowX() >= 0 && appConfig.getWindowY() >= 0) {
            mainFrame.setLocation(appConfig.getWindowX(), appConfig.getWindowY());
        }
        if (appConfig.getWindowWidth() > 0 && appConfig.getWindowHeight() > 0) {
            mainFrame.setSize(appConfig.getWindowWidth(), appConfig.getWindowHeight());
        }
        if (appConfig.isMaximized()) {
            mainFrame.setExtendedState(java.awt.Frame.MAXIMIZED_BOTH);
        }

        // ⑤ 绑定所有事件监听器
        bindEvents();

        // ⑥ 全局快捷键
        bindGlobalShortcuts();

        // ⑦ 窗口关闭行为
        bindWindowClose();

        // ⑧ 状态栏就绪
        statusBar.setStatus("就绪，请导入图片开始", "ready");

        // ⑨ 后台预检测 FFmpeg/FFplay
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ffmpegOk = VideoUtil.checkFfmpegAvailable();
                boolean ffplayOk = VideoUtil.checkFfplayAvailable();
                LogUtil.info("[MainController] 后台环境检测完成 — FFmpeg="
                        + ffmpegOk + ", FFplay=" + ffplayOk);
            }
        }, "FFmpeg-Detection-Thread").start();

        LogUtil.info("[MainController] 初始化完成");
    }

    // ==================== 事件绑定 ====================

    /**
     * 绑定所有 UI 事件监听器（菜单、工具栏、模式切换、文件列表）。
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
        mainFrame.getThemeBtn().addActionListener(e -> onToggleTheme());

        // ---- 模式切换分段控件 ----
        mainFrame.getImageModeBtn().addActionListener(e -> {
            if (mainFrame.getImageModeBtn().isSelected()) onModeToggle(false);
        });
        mainFrame.getVideoModeBtn().addActionListener(e -> {
            if (mainFrame.getVideoModeBtn().isSelected()) onModeToggle(true);
        });

        // ---- 子控制器事件绑定 ----
        imageController.bindEvents();
        videoController.bindEvents();

        // ---- 文件列表选中事件 ----
        fileListPanel.getFileJList().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onFileSelected(fileListPanel.getSelectedIndex());
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

        // ---- 预览面板操作按钮回调 ----
        mainFrame.getPreviewPanel().setOnOpenOriginalFile(() -> {
            FileInfo selected = fileListPanel.getSelectedFile();
            if (selected == null) {
                ToastNotification.info("请先在文件列表中选择一个文件");
                return;
            }
            File sourceFile;
            if (selected instanceof ImageFileInfo) {
                sourceFile = ((ImageFileInfo) selected).getSourceFile();
            } else if (selected instanceof VideoFileInfo) {
                sourceFile = ((VideoFileInfo) selected).getSourceFile();
            } else {
                return;
            }
            if (sourceFile != null && sourceFile.exists()) {
                try {
                    Desktop.getDesktop().open(sourceFile);
                } catch (IOException ex) {
                    ToastNotification.warning("无法打开文件: " + ex.getMessage());
                }
            }
        });
        mainFrame.getPreviewPanel().setOnOpenOutputDir(() -> {
            try {
                String outputDir = imageController.getCurrentOutputDir();
                if (outputDir == null || outputDir.isEmpty()) {
                    outputDir = configService.getDefaultOutputDir();
                }
                File dir = new File(outputDir);
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                Desktop.getDesktop().open(dir);
            } catch (IOException ex) {
                ToastNotification.warning("无法打开输出目录: " + ex.getMessage());
            }
        });

        LogUtil.info("[MainController] 事件绑定完成（含子控制器、右键菜单、快捷键）");
    }

    /**
     * 绑定全局键盘快捷键。
     * Ctrl+Enter 压缩、Escape 取消、Ctrl+A 全选、Ctrl+Z 撤销。
     */
    private void bindGlobalShortcuts() {
        javax.swing.JRootPane rootPane = mainFrame.getRootPane();

        rootPane.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(javax.swing.KeyStroke.getKeyStroke(
                        java.awt.event.KeyEvent.VK_ENTER,
                        java.awt.event.InputEvent.CTRL_DOWN_MASK), "startCompress");
        rootPane.getActionMap().put("startCompress", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                onStartCompress();
            }
        });

        rootPane.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(javax.swing.KeyStroke.getKeyStroke(
                        java.awt.event.KeyEvent.VK_ESCAPE, 0), "cancelCompress");
        rootPane.getActionMap().put("cancelCompress", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                onCancelCompress();
            }
        });

        rootPane.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(javax.swing.KeyStroke.getKeyStroke(
                        java.awt.event.KeyEvent.VK_A,
                        java.awt.event.InputEvent.CTRL_DOWN_MASK), "selectAllFiles");
        rootPane.getActionMap().put("selectAllFiles", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                javax.swing.JList<FileInfo> list = fileListPanel.getFileJList();
                if (list.getModel().getSize() > 0) {
                    list.setSelectionInterval(0, list.getModel().getSize() - 1);
                    list.requestFocusInWindow();
                }
            }
        });

        rootPane.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(javax.swing.KeyStroke.getKeyStroke(
                        java.awt.event.KeyEvent.VK_Z,
                        java.awt.event.InputEvent.CTRL_DOWN_MASK), "undo");
        rootPane.getActionMap().put("undo", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                onUndo();
            }
        });

        LogUtil.info("[MainController] 全局快捷键已注册: Ctrl+Enter/Escape/Ctrl+A/Ctrl+Z");
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

    // ==================== 文件导入（路由） ====================

    @Override
    public void onImportFiles() {
        if (mainFrame.isVideoMode()) {
            videoController.onImportFiles();
        } else {
            imageController.onImportFiles();
        }
    }

    @Override
    public void onImportFolder() {
        if (mainFrame.isVideoMode()) {
            videoController.onImportFolder();
        } else {
            imageController.onImportFolder();
        }
    }

    @Override
    public void onDragImport(List<File> files) {
        if (files == null || files.isEmpty()) return;
        if (mainFrame.isVideoMode()) {
            videoController.onDragImport(files);
        } else {
            imageController.onDragImport(files);
        }
    }

    // ==================== 文件移除与清空（共享） ====================

    @Override
    public void onRemoveFile(int index) {
        FileInfo removed = fileListPanel.getSelectedFile();
        if (removed == null && index >= 0 && index < fileListPanel.getFileList().size()) {
            removed = fileListPanel.getFileList().get(index);
        }
        fileListPanel.removeFile(index);
        clearUndoState();
        if (removed != null) {
            undoRemovedFile = removed;
            undoRemovedIndex = Math.min(index, fileListPanel.getFileList().size());
            undoTimer.restart();
            statusBar.setStatus("已移除 " + removed.getFileName() + " — 按 Ctrl+Z 撤销");
        } else {
            statusBar.setStatus("已移除文件");
        }
        updateCompressButtonState();
        if (fileListPanel.getFileList().isEmpty()) {
            clearPreviews();
        }
    }

    /**
     * 打开批量重命名对话框。
     */
    public void onBatchRename() {
        List<FileInfo> files = fileListPanel.getFileList();
        if (files == null || files.isEmpty()) {
            ToastNotification.warning("文件列表为空，无法重命名");
            return;
        }
        // 构建可修改的列表副本
        List<FileInfo> modifiableList = new ArrayList<>(files);
        new com.nchu.imagecompress.view.BatchRenameDialog(mainFrame, modifiableList,
                updatedList -> {
                    // 刷新缩略图缓存（路径可能已变）
                    fileListPanel.clearAllFiles();
                    clearPreviews();
                    // 重新导入（使用更新后的 FileInfo）
                    for (FileInfo info : updatedList) {
                        fileListPanel.addFile(info);
                        if (info.getFileType() == FileInfo.FileType.IMAGE) {
                            fileListPanel.loadThumbnail(info);
                        }
                    }
                });
    }

    @Override
    public void onClearAllFiles() {
        if (imageController.isCompressing()) {
            ToastNotification.warning("压缩任务进行中，请先取消或等待完成");
            return;
        }
        if (videoController.isCompressing()) {
            ToastNotification.warning("视频压缩任务进行中，请先取消或等待完成");
            return;
        }

        List<FileInfo> allFiles = new ArrayList<>(fileListPanel.getFileList());
        if (allFiles.isEmpty()) return;

        int count = fileListPanel.getFileList().size();
        if (count == 0) return;

        if (mainFrame.isVideoMode()) {
            videoController.getVideoFileList().clear();
        }

        fileListPanel.clearAllFiles();
        clearPreviews();
        updateCompressButtonState();
        clearUndoState();
        undoClearAllFiles = allFiles;
        undoTimer.restart();
        statusBar.setStatus("已清空 " + count + " 个文件 — 按 Ctrl+Z 撤销");
    }

    /**
     * 清除当前模式下的预览。
     */
    private void clearPreviews() {
        if (mainFrame.isVideoMode()) {
            videoController.clearPreview();
        } else {
            imageController.clearPreview();
        }
    }

    /**
     * 清除撤销状态。
     */
    private void clearUndoState() {
        undoTimer.stop();
        undoRemovedFile = null;
        undoRemovedIndex = -1;
        undoClearAllFiles = null;
    }

    /**
     * Ctrl+Z 撤销最近的文件移除或清空操作。
     */
    private void onUndo() {
        if (undoClearAllFiles != null) {
            List<FileInfo> restored = new ArrayList<>(undoClearAllFiles);
            clearUndoState();
            fileListPanel.setFileList(restored);
            updateCompressButtonState();
            statusBar.flashSuccess("已撤销清空，恢复 " + restored.size() + " 个文件");
            LogUtil.info("[MainController] 撤销清空列表，恢复 " + restored.size() + " 个文件");
        } else if (undoRemovedFile != null) {
            FileInfo restored = undoRemovedFile;
            int index = undoRemovedIndex;
            clearUndoState();
            List<FileInfo> current = new ArrayList<>(fileListPanel.getFileList());
            current.add(Math.min(index, current.size()), restored);
            fileListPanel.setFileList(current);
            updateCompressButtonState();
            statusBar.flashSuccess("已撤销移除: " + restored.getFileName());
            LogUtil.info("[MainController] 撤销移除文件: " + restored.getFileName());
        }
    }

    // ==================== 预览（路由） ====================

    @Override
    public void onFileSelected(int index) {
        if (mainFrame.isVideoMode()) {
            videoController.onFileSelected(index);
        } else {
            imageController.onFileSelected(index);
        }
    }

    // ==================== 压缩（路由） ====================

    @Override
    public void onStartCompress() {
        if (mainFrame.isVideoMode()) {
            videoController.onStartCompress();
        } else {
            imageController.onStartCompress();
        }
    }

    @Override
    public void onCancelCompress() {
        if (mainFrame.isVideoMode()) {
            videoController.onCancelCompress();
        } else {
            imageController.onCancelCompress();
        }
    }

    /**
     * 更新压缩按钮启用状态（根据当前模式）。
     */
    private void updateCompressButtonState() {
        if (mainFrame.isVideoMode()) {
            videoController.updateVideoCompressButtonState();
        } else {
            imageController.updateCompressButtonState();
        }
    }

    // ==================== 回调实现：setCompressingState ====================

    /**
     * {@inheritDoc}
     * <p>切换工具栏按钮和面板控件的压缩/空闲状态。</p>
     */
    @Override
    public void setCompressingState(boolean compressing) {
        if (mainFrame.isVideoMode()) {
            videoController.getVideoParamPanel().getCompressButton().setEnabled(!compressing);
            videoController.getVideoParamPanel().getCancelButton().setEnabled(compressing);
        } else {
            // ImageController 持有 paramPanel，这里直接操作 MainFrame 获取
            mainFrame.getParamPanel().getCompressButton().setEnabled(!compressing);
            mainFrame.getParamPanel().getCancelButton().setEnabled(compressing);
        }
        mainFrame.getImportBtn().setEnabled(!compressing);
        mainFrame.getImportFolderBtn().setEnabled(!compressing);
        mainFrame.getClearBtn().setEnabled(!compressing);

        if (!compressing) {
            statusBar.hideProgress();
        }
    }

    // ==================== 主题切换 ====================

    @Override
    public void onToggleTheme() {
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

        mainFrame.repaint();
        mainFrame.updateThemeButtonText(theme);

        ToastNotification.info("已切换至 " + theme.getDisplayName());
        LogUtil.info("[MainController] 主题已切换: " + theme.getDisplayName());
    }

    // ==================== 设置 ====================

    @Override
    public void onOpenSettings() {
        boolean changed = SettingsDialog.show(mainFrame, appConfig);
        if (changed) {
            configService.saveConfigImmediately();
            statusBar.flashSuccess("设置已保存");
        }
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
        imageController.onQualityChanged(quality);
    }

    @Override
    public void onOutputDirSelected(File dir) {
        imageController.onOutputDirSelected(dir);
    }

    // ==================== 窗口生命周期 ====================

    @Override
    public void onWindowClosing() {
        if (shuttingDown) return;

        // 关闭 ffplay 播放进程
        videoController.killFfplayProcess();

        // 释放 VLCJ 内嵌播放器资源
        videoController.release();

        // 检查是否有运行中的任务
        if (imageController.isCompressing() || videoController.isCompressing()) {
            String task = videoController.isCompressing() ? "视频" : "图片";
            int choice = JOptionPane.showConfirmDialog(mainFrame,
                    task + "压缩任务正在进行中，确定要退出吗？",
                    "确认退出",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
            // 取消子控制器的任务由各自的 isCompressing() 判断后在 finally 中清理
        }

        shuttingDown = true;

        // 保存窗口状态
        saveWindowState();

        // 强制保存配置
        configService.shutdown();

        LogUtil.info("[MainController] 应用退出");

        // 释放悬停预览弹窗资源
        try {
            fileListPanel.getHoverPopup().dispose();
        } catch (Exception ignored) {
            // 悬停预览未初始化时忽略
        }

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

        if (mainFrame.getMainSplitPane() != null) {
            appConfig.setSplitPaneLeftWidth(
                    mainFrame.getMainSplitPane().getDividerLocation());
        }
        if (mainFrame.getRightSplitPane() != null) {
            appConfig.setSplitPaneRightDivider(
                    mainFrame.getRightSplitPane().getDividerLocation());
        }

        // 保存最近导入目录
        File lastOpenDir = imageController.getLastOpenDir();
        if (lastOpenDir != null) {
            appConfig.setRecentImportDir(lastOpenDir.getAbsolutePath());
        }
        File lastVideoOpenDir = videoController.getLastVideoOpenDir();
        if (lastVideoOpenDir != null) {
            appConfig.setRecentVideoImportDir(lastVideoOpenDir.getAbsolutePath());
        }

        // 保存视频压缩参数
        videoController.saveParamsToConfig();

        configService.saveConfig(appConfig);
    }

    // ==================== 模式切换 ====================

    @Override
    public void onModeToggle(boolean videoMode) {
        if (videoMode) {
            // 保存当前图片文件列表（用于切回图片模式时恢复）
            savedImageFileList = new ArrayList<>(fileListPanel.getFileList());

            mainFrame.switchCompressMode("VIDEO");
            fileListPanel.setFileList(new ArrayList<>(videoController.getVideoFileList()));
            if (!VideoUtil.checkFfmpegAvailable()) {
                ToastNotification.warning("FFmpeg 未安装，视频压缩功能不可用。请先安装 FFmpeg 4.0+");
            }
            videoController.restoreParamsFromConfig();
            videoController.updateVideoCompressButtonState();
            statusBar.setStatus("视频模式 — 请导入视频文件", "ready");
        } else {
            videoController.clearPreview();
            mainFrame.switchCompressMode("IMAGE");
            // 恢复之前保存的图片文件列表（修复切换模式清空图片列表的 Bug）
            if (savedImageFileList != null && !savedImageFileList.isEmpty()) {
                fileListPanel.setFileList(new ArrayList<>(savedImageFileList));
            } else {
                fileListPanel.clearAllFiles();
            }
            imageController.restoreParamsFromConfig();
            imageController.updateCompressButtonState();
            statusBar.setStatus("图片模式 — 请导入图片文件", "ready");
        }
        appConfig.setCompressMode(videoMode ? "VIDEO" : "IMAGE");
        configService.saveConfig(appConfig);
    }

    // ==================== 右键菜单 ====================

    /**
     * 绑定文件列表的右键弹出菜单。
     */
    private void bindFileListPopupMenu() {
        JPopupMenu popupMenu = new JPopupMenu();

        JMenuItem previewItem = new JMenuItem("预览", new FlatSVGIcon("icons/clipboard.svg"));
        previewItem.addActionListener(e -> {
            int index = fileListPanel.getSelectedIndex();
            if (index >= 0) onFileSelected(index);
        });
        popupMenu.add(previewItem);

        JMenuItem openOriginalItem = new JMenuItem("打开原图", new FlatSVGIcon("icons/image.svg"));
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

        JMenuItem openFolderItem = new JMenuItem("打开所在文件夹", new FlatSVGIcon("icons/folder.svg"));
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

        JMenuItem playVideoItem = new JMenuItem("播放视频", new FlatSVGIcon("icons/play.svg"));
        playVideoItem.addActionListener(e -> {
            FileInfo selected = fileListPanel.getSelectedFile();
            if (selected instanceof VideoFileInfo) {
                videoController.onPlayOriginalVideo((VideoFileInfo) selected);
            } else {
                ToastNotification.info("仅视频文件支持播放");
            }
        });
        popupMenu.add(playVideoItem);

        popupMenu.addSeparator();

        JMenuItem renameItem = new JMenuItem("批量重命名", new FlatSVGIcon("icons/clipboard.svg"));
        renameItem.addActionListener(e -> onBatchRename());
        popupMenu.add(renameItem);

        JMenuItem compressSingleItem = new JMenuItem("单独压缩此文件", new FlatSVGIcon("icons/play.svg"));
        compressSingleItem.addActionListener(e -> {
            FileInfo selected = fileListPanel.getSelectedFile();
            if (selected instanceof ImageFileInfo) {
                imageController.compressSingleFile((ImageFileInfo) selected);
            }
        });
        popupMenu.add(compressSingleItem);

        popupMenu.addSeparator();

        JMenuItem removeItem = new JMenuItem("从列表移除", new FlatSVGIcon("icons/delete.svg"));
        removeItem.addActionListener(e -> {
            int index = fileListPanel.getSelectedIndex();
            if (index >= 0) onRemoveFile(index);
        });
        popupMenu.add(removeItem);

        JMenuItem clearAllItem = new JMenuItem("清空列表");
        clearAllItem.addActionListener(e -> onClearAllFiles());
        popupMenu.add(clearAllItem);

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

    // ==================== 拖拽导入 ====================

    /**
     * 绑定拖拽导入功能。
     */
    private void bindDragAndDrop() {
        TransferHandler transferHandler = new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
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

        mainFrame.setTransferHandler(transferHandler);
        fileListPanel.setTransferHandler(transferHandler);
        fileListPanel.getFileJList().setTransferHandler(transferHandler);
    }
}
