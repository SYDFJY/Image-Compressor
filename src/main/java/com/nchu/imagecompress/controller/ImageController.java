package com.nchu.imagecompress.controller;

import com.nchu.imagecompress.model.AppConfig;
import com.nchu.imagecompress.model.CompressConfig;
import com.nchu.imagecompress.model.CompressResult;
import com.nchu.imagecompress.model.FileInfo;
import com.nchu.imagecompress.model.ImageFileInfo;
import com.nchu.imagecompress.model.OutputFormat;
import com.nchu.imagecompress.model.ProgressChunk;
import com.nchu.imagecompress.service.BatchCompressService;
import com.nchu.imagecompress.service.CompressService;
import com.nchu.imagecompress.service.ConfigService;
import com.nchu.imagecompress.service.FileManagerService;
import com.nchu.imagecompress.service.SmartRecommendService;
import com.nchu.imagecompress.util.ImageExifUtil;
import com.nchu.imagecompress.util.ImageUtil;
import com.nchu.imagecompress.util.LogUtil;
import com.nchu.imagecompress.view.FileListPanel;
import com.nchu.imagecompress.view.MainFrame;
import com.nchu.imagecompress.view.ParamPanel;
import com.nchu.imagecompress.view.PreviewPanel;
import com.nchu.imagecompress.view.ResultDialog;
import com.nchu.imagecompress.view.StatusBar;
import com.nchu.imagecompress.view.ToastNotification;

import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 图片压缩子控制器 — 管理图片模式的完整生命周期。
 *
 * <p>从 MainController 拆分出来，负责图片导入、预览、参数配置、
 * 压缩执行与结果回调。不处理视频相关逻辑。</p>
 *
 * <p>通过 {@link ImageControllerCallback} 向 MainController 报告
 * 需要跨模式协调的状态变更（如工具栏按钮启用/禁用）。</p>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-17
 */
public class ImageController {

    // ==================== 依赖组件 ====================

    private final MainFrame mainFrame;
    private final FileListPanel fileListPanel;
    private final PreviewPanel previewPanel;
    private final ParamPanel paramPanel;
    private final StatusBar statusBar;

    // 共享配置（由 MainController 注入）
    private final AppConfig appConfig;
    private final ConfigService configService;
    private final FileManagerService fileManagerService;

    // 服务层
    private final CompressService compressService;
    private final BatchCompressService batchCompressService;
    private final SmartRecommendService smartRecommendService;

    // 回调桥梁
    private final ImageControllerCallback callback;

    // ==================== 状态 ====================

    /** 当前正在运行的压缩 Worker */
    private CompressWorker currentWorker;

    /** 当前压缩任务使用的配置（供完成回调使用） */
    private CompressConfig currentCompressConfig;

    /** 上次打开的目录（用于文件选择器记忆） */
    private File lastOpenDir;

    /** 当前选中文件的原始大小（用于预估输出大小），-1 表示无选中文件 */
    private long selectedOriginalSize = -1;

    /** 预览防抖定时器：质量滑块拖动停止 200ms 后刷新效果预览 */
    private final javax.swing.Timer previewDebounceTimer;

    // ==================== 构造与初始化 ====================

    /**
     * 构造图片控制器。
     *
     * @param mainFrame  主窗口
     * @param appConfig  应用配置（共享引用）
     * @param configService 配置持久化服务
     * @param fileManagerService 文件管理服务
     * @param callback   回调桥梁
     */
    public ImageController(MainFrame mainFrame,
                           AppConfig appConfig,
                           ConfigService configService,
                           FileManagerService fileManagerService,
                           ImageControllerCallback callback) {
        this.mainFrame = mainFrame;
        this.appConfig = appConfig;
        this.configService = configService;
        this.fileManagerService = fileManagerService;
        this.callback = callback;

        // 视图快捷引用
        this.fileListPanel = mainFrame.getFileListPanel();
        this.previewPanel = mainFrame.getPreviewPanel();
        this.paramPanel = mainFrame.getParamPanel();
        this.statusBar = mainFrame.getStatusBar();

        // 服务层
        this.compressService = new CompressService();
        this.batchCompressService = new BatchCompressService();
        this.smartRecommendService = new SmartRecommendService();

        // 预览防抖定时器
        this.previewDebounceTimer = new javax.swing.Timer(200, null);
        this.previewDebounceTimer.setRepeats(false);
        this.previewDebounceTimer.addActionListener(e -> refreshEffectPreview());
    }

    /**
     * 绑定图片面板的 UI 事件监听器。
     */
    public void bindEvents() {
        // 参数面板按钮
        paramPanel.getCompressButton().addActionListener(e -> onStartCompress());
        paramPanel.getCancelButton().addActionListener(e -> onCancelCompress());
        paramPanel.getOutputDirButton().addActionListener(e -> onChooseOutputDir());

        // 质量滑块实时更新 + 预览刷新 + 预估大小
        paramPanel.getQualitySlider().addChangeListener(e -> {
            int quality = paramPanel.getQuality();
            paramPanel.setQualityDisplay(quality);
            if (!paramPanel.getQualitySlider().getValueIsAdjusting()) {
                onQualityChanged(quality);
                updateEstimatedImageSize();
            }
            previewDebounceTimer.restart();
        });

        // 输出格式变更时更新预估大小
        paramPanel.getOutputFormatCombo().addActionListener(e -> updateEstimatedImageSize());

        // 缩放模式/百分比变更时刷新预览 + 输出尺寸
        paramPanel.getScaleModeCombo().addActionListener(e -> {
            updateOutputDimLabelFromSelection();
            previewDebounceTimer.restart();
        });
        ((javax.swing.JSpinner.DefaultEditor) paramPanel.getScalePercentSpinner().getEditor())
                .getTextField().addFocusListener(new java.awt.event.FocusAdapter() {
                    @Override
                    public void focusLost(java.awt.event.FocusEvent e) {
                        updateOutputDimLabelFromSelection();
                        previewDebounceTimer.restart();
                    }
                });

        LogUtil.info("[ImageController] 事件绑定完成");
    }

    /**
     * 从配置恢复上次的图片压缩参数。
     */
    public void restoreParamsFromConfig() {
        paramPanel.getQualitySlider().setValue(appConfig.getLastQuality());
        paramPanel.setQualityDisplay(appConfig.getLastQuality());

        String format = appConfig.getLastOutputFormat();
        if (format != null) {
            switch (format) {
                case "JPEG": paramPanel.getOutputFormatCombo().setSelectedIndex(1); break;
                case "PNG":  paramPanel.getOutputFormatCombo().setSelectedIndex(2); break;
                case "BMP":  paramPanel.getOutputFormatCombo().setSelectedIndex(3); break;
                default:     paramPanel.getOutputFormatCombo().setSelectedIndex(0);
            }
        }

        String scale = appConfig.getLastScaleMode();
        if ("BY_PERCENT".equals(scale)) {
            paramPanel.getScaleModeCombo().setSelectedIndex(1);
        } else if ("BY_MAX_SIZE".equals(scale)) {
            paramPanel.getScaleModeCombo().setSelectedIndex(2);
        }

        String naming = appConfig.getLastNamingRule();
        if ("ADD_PREFIX".equals(naming)) {
            paramPanel.getNamingRuleCombo().setSelectedIndex(1);
        } else if ("KEEP_ORIGINAL".equals(naming)) {
            paramPanel.getNamingRuleCombo().setSelectedIndex(2);
        } else if ("CUSTOM".equals(naming)) {
            paramPanel.getNamingRuleCombo().setSelectedIndex(3);
        }
        if (appConfig.getLastCustomName() != null) {
            paramPanel.setCustomFileName(appConfig.getLastCustomName());
        }

        if (appConfig.getLastTargetSizeKB() > 0) {
            paramPanel.setTargetSizeKB(appConfig.getLastTargetSizeKB());
        }

        LogUtil.info("[ImageController] 图片参数恢复完成");
    }

    // ==================== 文件导入 ====================

    /**
     * 弹出图片文件选择器并导入。
     */
    public void onImportFiles() {
        JFileChooser chooser = createImageFileChooser(true);
        int result = chooser.showOpenDialog(mainFrame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File[] files = chooser.getSelectedFiles();
            lastOpenDir = chooser.getCurrentDirectory();
            importFilesInternal(files);
        }
    }

    /**
     * 弹出图片文件夹选择器并导入。
     */
    public void onImportFolder() {
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

    /**
     * 处理拖拽导入的图片文件。
     */
    public void onDragImport(List<File> files) {
        if (files == null || files.isEmpty()) return;
        importFilesInternal(files.toArray(new File[0]));
    }

    /**
     * 内部统一导入逻辑（后台线程读取尺寸，EDT 更新列表）。
     */
    private void importFilesInternal(File[] files) {
        statusBar.setStatus("正在导入文件...", "working");

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

        if (existing.size() + newFiles.size() > 500) {
            ToastNotification.warning("文件列表已达到上限 (500)，部分文件未导入");
        }

        List<ImageFileInfo> deduped = fileManagerService.deduplicate(existing, newFiles);
        int skipped = newFiles.size() - deduped.size();

        for (ImageFileInfo info : deduped) {
            fileListPanel.addFile(info);
        }

        // 按文件修改时间倒序排列（最新的在前）
        if (!deduped.isEmpty()) {
            List<FileInfo> allFiles = fileListPanel.getFileList();
            java.util.Collections.sort(allFiles, new java.util.Comparator<FileInfo>() {
                @Override
                public int compare(FileInfo a, FileInfo b) {
                    long t1 = getLastModifiedSafe(a);
                    long t2 = getLastModifiedSafe(b);
                    return Long.compare(t2, t1); // 降序
                }
            });
            fileListPanel.setFileList(allFiles);
        }

        updateCompressButtonState();

        if (skipped > 0) {
            ToastNotification.info("已跳过 " + skipped + " 个重复文件");
        }
        if (!deduped.isEmpty()) {
            ToastNotification.success("成功导入 " + deduped.size() + " 个文件");
        }
    }

    /** 安全获取文件的最后修改时间（避免 null/不存在的文件抛异常） */
    private static long getLastModifiedSafe(FileInfo info) {
        try {
            if (info instanceof ImageFileInfo) {
                File f = ((ImageFileInfo) info).getSourceFile();
                return f != null ? f.lastModified() : 0L;
            }
        } catch (Exception ignored) { }
        return 0L;
    }

    // ==================== 预览 ====================

    /**
     * 选中图片文件时生成预览图和 EXIF 信息。
     */
    public void onFileSelected(int index) {
        if (index < 0) {
            previewPanel.clearPreview();
            paramPanel.showGifControls(false);
            paramPanel.hideEstimatedSize();
            paramPanel.hideOutputDimLabel();
            paramPanel.hideScaleHintLabel();
            selectedOriginalSize = -1;
            return;
        }

        FileInfo selected = fileListPanel.getFileList().get(index);
        if (!(selected instanceof ImageFileInfo)) return;
        final ImageFileInfo info = (ImageFileInfo) selected;
        if (info.getSourceFile() == null) return;
        final File sourceFile = info.getSourceFile();

        // 保存原始大小用于预估输出大小
        selectedOriginalSize = info.getOriginalSize();
        updateEstimatedImageSize();
        updateOutputDimLabel(info.getWidth(), info.getHeight());

        new Thread(() -> {
            ImageIcon preview = ImageUtil.loadScaledIcon(sourceFile, 1024, 768);
            ImageExifUtil.ExifData exif = ImageExifUtil.readExif(sourceFile);
            info.setExifData(exif);
            SwingUtilities.invokeLater(() -> {
                previewPanel.showOriginal(preview);
                previewPanel.showImageInfo(info);
                // 检测文件格式，显示/隐藏 GIF 控件
                String ext = getFileExtension(sourceFile);
                paramPanel.showGifControls("gif".equalsIgnoreCase(ext));
            });
        }).start();
    }

    /**
     * 清除图片预览。
     */
    public void clearPreview() {
        previewPanel.clearPreview();
    }

    /**
     * 根据当前质量/格式/原始大小更新预估输出大小标签。
     */
    private void updateEstimatedImageSize() {
        if (selectedOriginalSize <= 0) {
            paramPanel.hideEstimatedSize();
            return;
        }
        int quality = paramPanel.getQuality();
        int formatIdx = paramPanel.getOutputFormatIndex();
        double factor;
        switch (formatIdx) {
            case 1: factor = 0.30; break;  // JPEG
            case 2: factor = 1.00; break;  // PNG
            case 3: factor = 3.00; break;  // BMP
            case 4: factor = 0.25; break;  // WebP
            default: factor = 0.30; break; // 保持原格式（按 JPEG 估算）
        }
        long estimatedBytes = (long) (selectedOriginalSize * (quality / 100.0) * factor);
        if (estimatedBytes < 1024) estimatedBytes = 1024;
        String sizeText = formatFileSize(estimatedBytes);
        paramPanel.setEstimatedSize("预估输出: 约 " + sizeText + (quality < 30 ? " (画质较低)" : ""));
    }

    /** 文件大小格式化（内部使用） */
    private static String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        return String.format("%.1f MB", mb);
    }

    /**
     * 更新输出尺寸预览标签（图片选中时调用）。
     */
    /** 缩放模式对应的通俗说明 */
    private static final String[] SCALE_HINTS = {
        "缩放说明：输出图片保持原始分辨率，像素尺寸不变，仅通过压缩质量减小文件体积",
        "缩放说明：按百分比缩小输出图片的宽高，像素总数减少 → 文件自然变小",
        "缩放说明：限制输出图片不超过 1920×1080，超出自动等比缩小；未超出则保持原尺寸",
    };

    private void updateOutputDimLabel(int origW, int origH) {
        if (origW <= 0 || origH <= 0) {
            paramPanel.hideOutputDimLabel();
            paramPanel.hideScaleHintLabel();
            return;
        }
        int mode = paramPanel.getScaleModeIndex();
        int outW = origW, outH = origH;
        String desc;
        if (mode == 0) { // 不缩放
            desc = origW + " × " + origH + "（原尺寸）";
        } else if (mode == 1) { // 按百分比
            int pct = paramPanel.getScalePercent();
            outW = Math.max(1, origW * pct / 100);
            outH = Math.max(1, origH * pct / 100);
            desc = outW + " × " + outH + "（" + pct + "%）";
        } else { // 按最大尺寸 1920×1080
            if (origW <= 1920 && origH <= 1080) {
                desc = origW + " × " + origH + "（未超出限制）";
            } else {
                double ratio = Math.min(1920.0 / origW, 1080.0 / origH);
                outW = (int) (origW * ratio);
                outH = (int) (origH * ratio);
                desc = outW + " × " + outH + "（等比缩放至 1920×1080 内）";
            }
        }
        paramPanel.setOutputDimLabel("输出尺寸：" + desc);
        paramPanel.setScaleHintLabel(mode >= 0 && mode < SCALE_HINTS.length
                ? SCALE_HINTS[mode] : " ");
    }

    /** 从当前选中文件读取尺寸并更新输出尺寸标签 */
    private void updateOutputDimLabelFromSelection() {
        int index = fileListPanel.getSelectedIndex();
        if (index < 0) {
            paramPanel.hideOutputDimLabel();
            paramPanel.hideScaleHintLabel();
            return;
        }
        FileInfo selected = fileListPanel.getFileList().get(index);
        if (selected instanceof ImageFileInfo) {
            ImageFileInfo info = (ImageFileInfo) selected;
            updateOutputDimLabel(info.getWidth(), info.getHeight());
        }
    }

    // ==================== 压缩 ====================

    /**
     * 开始图片压缩（批量或单文件）。
     */
    public void onStartCompress() {
        List<ImageFileInfo> fileList = fileListPanel.getImageFileList();
        if (fileList.isEmpty()) {
            ToastNotification.warning("请先导入图片文件");
            return;
        }

        if (currentWorker != null && !currentWorker.isDone()) {
            ToastNotification.warning("压缩任务正在进行中");
            return;
        }

        currentCompressConfig = buildCompressConfig();
        final CompressConfig config = currentCompressConfig;

        // 检查无效压缩
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

        // 更新 UI 状态
        callback.setCompressingState(true);

        for (ImageFileInfo info : fileList) {
            info.setFileInfoStatus(FileInfo.Status.PENDING);
        }
        fileListPanel.getFileJList().repaint();

        statusBar.showProgress(0, "0/" + fileList.size(), "准备压缩 " + fileList.size() + " 个文件...");

        // 创建并启动 Worker
        currentWorker = new CompressWorker(
                batchCompressService,
                new ArrayList<>(fileList),
                config,
                null,
                this::onCompressComplete,
                this::onCompressCancelled
        ) {
            @Override
            protected void process(List<ProgressChunk> chunks) {
                if (chunks == null || chunks.isEmpty()) return;
                ProgressChunk latest = chunks.get(chunks.size() - 1);

                int overallPercent = (int) (latest.getOverallProgress() * 100);
                String detail = (latest.getCompletedCount() + latest.getFailedCount())
                        + "/" + latest.getTotalFiles();
                statusBar.showProgress(overallPercent, detail, latest.getStatusText());

                if (latest.getCurrentIndex() >= 0
                        && latest.getCurrentIndex() < fileListPanel.getFileList().size()) {
                    fileListPanel.getFileJList().repaint();
                }
            }
        };

        currentWorker.execute();
        LogUtil.info("[ImageController] 压缩任务已启动，共 " + fileList.size() + " 个文件");
    }

    /**
     * 取消当前图片压缩任务。
     */
    public void onCancelCompress() {
        if (currentWorker != null && !currentWorker.isDone()) {
            LogUtil.info("[ImageController] 用户取消压缩任务");
            currentWorker.cancel(true);
            statusBar.setStatus("正在取消...", "working");
            paramPanel.getCancelButton().setEnabled(false);
        }
    }

    /**
     * 图片压缩完成回调（在 EDT 中执行）。
     */
    private void onCompressComplete() {
        try {
            List<CompressResult> results = currentWorker.get();
            int success = 0;
            int fail = 0;

            for (CompressResult r : results) {
                if (r.isSuccess()) success++;
                else fail++;
            }

            statusBar.hideProgress();
            String msg = String.format("完成 — 成功 %d, 失败 %d", success, fail);
            if (success > 0) {
                statusBar.flashSuccess(msg);
            } else {
                statusBar.flashError(msg);
            }

            String outputDir = currentCompressConfig != null
                    ? currentCompressConfig.getOutputPath() : null;
            if (outputDir == null || outputDir.isEmpty()) {
                outputDir = configService.getDefaultOutputDir();
            }
            long totalElapsed = 0;
            for (CompressResult r : results) {
                totalElapsed += r.getElapsedMs();
            }
            ResultDialog.show(mainFrame, results, outputDir, totalElapsed);

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
            LogUtil.error("[ImageController] 获取压缩结果异常: " + e.getMessage());
            statusBar.flashError("压缩过程出现异常");
        } finally {
            callback.setCompressingState(false);
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
        callback.setCompressingState(false);
        currentWorker = null;
    }

    // ==================== 参数配置 ====================

    /**
     * 从 ParamPanel 构建 CompressConfig。
     */
    private CompressConfig buildCompressConfig() {
        CompressConfig config = new CompressConfig();

        config.setQuality(paramPanel.getQuality());

        switch (paramPanel.getScaleModeIndex()) {
            case 0: config.setScaleMode(CompressConfig.ScaleMode.NONE); break;
            case 1:
                config.setScaleMode(CompressConfig.ScaleMode.BY_PERCENT);
                config.setScalePercent(paramPanel.getScalePercent());
                break;
            case 2: config.setScaleMode(CompressConfig.ScaleMode.BY_MAX_SIZE); break;
            default: config.setScaleMode(CompressConfig.ScaleMode.NONE);
        }

        switch (paramPanel.getOutputFormatIndex()) {
            case 0: config.setOutputFormat(OutputFormat.ORIGINAL); break;
            case 1: config.setOutputFormat(OutputFormat.JPEG); break;
            case 2: config.setOutputFormat(OutputFormat.PNG); break;
            case 3: config.setOutputFormat(OutputFormat.BMP); break;
            case 4: config.setOutputFormat(OutputFormat.WEBP); break;
            default: config.setOutputFormat(OutputFormat.ORIGINAL);
        }

        switch (paramPanel.getNamingRuleIndex()) {
            case 0: config.setNamingRule(CompressConfig.NamingRule.ADD_SUFFIX); break;
            case 1: config.setNamingRule(CompressConfig.NamingRule.ADD_PREFIX); break;
            case 2: config.setNamingRule(CompressConfig.NamingRule.KEEP_ORIGINAL); break;
            case 3: config.setNamingRule(CompressConfig.NamingRule.CUSTOM); break;
            default: config.setNamingRule(CompressConfig.NamingRule.ADD_SUFFIX);
        }
        config.setCustomName(paramPanel.getCustomFileName());
        config.setTargetSizeKB(paramPanel.getTargetSizeKB());

        appConfig.setLastNamingRule(config.getNamingRule().name());
        appConfig.setLastCustomName(config.getCustomName());
        appConfig.setLastTargetSizeKB(config.getTargetSizeKB());

        config.setOverwrite(paramPanel.isOverwrite());
        config.setPreserveMetadata(paramPanel.isPreserveMetadata());
        config.setGifMaxColors(paramPanel.getGifMaxColors());

        String outputPath = appConfig.getRecentOutputDir();
        if (outputPath != null && !outputPath.isEmpty()) {
            config.setOutputPath(outputPath);
        } else {
            config.setOutputPath(configService.getDefaultOutputDir());
        }

        return config;
    }

    /**
     * 质量滑块变化时保存配置。
     */
    public void onQualityChanged(int quality) {
        appConfig.setLastQuality(quality);
        configService.saveConfig(appConfig);
    }

    /**
     * 用户选择输出目录后保存。
     */
    public void onOutputDirSelected(File dir) {
        if (dir != null && dir.isDirectory()) {
            appConfig.setRecentOutputDir(dir.getAbsolutePath());
            configService.saveConfig(appConfig);
            ToastNotification.info("输出目录: " + dir.getName());
        }
    }

    /**
     * 弹出输出目录选择器。
     */
    private void onChooseOutputDir() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择输出目录");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

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

    // ==================== 单文件压缩 ====================

    /**
     * 右键菜单"单独压缩此文件"。
     */
    public void compressSingleFile(ImageFileInfo info) {
        CompressConfig config = buildCompressConfig();
        callback.setCompressingState(true);
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
                callback.setCompressingState(false);

                if (result.isSuccess()) {
                    statusBar.flashSuccess(info.getFileName() + " 压缩完成");
                    ToastNotification.success("压缩完成: " + info.getFileName());

                    File outputFile = new File(result.getOutputPath());
                    if (outputFile.exists()) {
                        new Thread(() -> {
                            ImageIcon preview = ImageUtil.loadScaledIcon(outputFile, 1024, 768);
                            SwingUtilities.invokeLater(() -> previewPanel.showEffect(preview));
                        }).start();
                    }

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

    // ==================== 预览刷新 ====================

    /**
     * 实时刷新效果预览图（质量滑块拖动防抖后触发）。
     */
    private void refreshEffectPreview() {
        int index = fileListPanel.getSelectedIndex();
        if (index < 0) return;
        FileInfo selected = fileListPanel.getFileList().get(index);
        if (!(selected instanceof ImageFileInfo)) return;
        final File sourceFile = ((ImageFileInfo) selected).getSourceFile();
        if (sourceFile == null) return;
        final double quality = paramPanel.getQuality() / 100.0;
        // 计算实际缩放百分比（按百分比 / 按最大尺寸 均需反映在预览中）
        final int scalePercent = computeEffectScalePercent(
                ((ImageFileInfo) selected).getWidth(),
                ((ImageFileInfo) selected).getHeight());

        new Thread(() -> {
            BufferedImage effect = ImageUtil.generateEffectPreview(
                    sourceFile, quality,
                    ImageUtil.PREVIEW_MAX_WIDTH, ImageUtil.PREVIEW_MAX_HEIGHT,
                    scalePercent);
            SwingUtilities.invokeLater(() -> {
                if (effect != null) {
                    previewPanel.showEffect(ImageUtil.toImageIcon(effect));
                }
            });
        }).start();
    }

    // ==================== 辅助方法 ====================

    /**
     * 根据当前缩放模式计算效果预览应使用的缩放百分比。
     *
     * <p>「不缩放」→ 100%，「按百分比」→ 用户指定值，
     * 「按最大尺寸」→ 根据原图与 1920×1080 的比值折算。</p>
     */
    private int computeEffectScalePercent(int origW, int origH) {
        int mode = paramPanel.getScaleModeIndex();
        if (mode == 1) { // 按百分比
            return paramPanel.getScalePercent();
        }
        if (mode == 2 && origW > 0 && origH > 0) { // 按最大尺寸 1920×1080
            int maxW = 1920, maxH = 1080;
            if (origW <= maxW && origH <= maxH) return 100;
            double ratio = Math.min((double) maxW / origW, (double) maxH / origH);
            return (int) (ratio * 100);
        }
        return 100; // 不缩放
    }

    /**
     * 获取当前输出目录路径。
     * 优先使用当前压缩配置中的目录，其次使用全局默认目录。
     */
    public String getCurrentOutputDir() {
        if (currentCompressConfig != null
                && currentCompressConfig.getOutputPath() != null
                && !currentCompressConfig.getOutputPath().isEmpty()) {
            return currentCompressConfig.getOutputPath();
        }
        return configService.getDefaultOutputDir();
    }

    // ==================== 按钮状态 ====================

    /**
     * 更新图片压缩按钮的启用状态。
     */
    public void updateCompressButtonState() {
        boolean hasFiles = !fileListPanel.getFileList().isEmpty();
        paramPanel.getCompressButton().setEnabled(hasFiles);
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

        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "图片文件 (JPG, PNG, BMP, GIF, TIFF, ICO, WebP)",
                "jpg", "jpeg", "png", "bmp", "gif", "tiff", "tif", "ico", "webp");
        chooser.setFileFilter(filter);

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

    /**
     * 从文件名提取小写扩展名（不含点）。
     */
    private static String getFileExtension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return (dot < 0 || dot == name.length() - 1) ? "" : name.substring(dot + 1).toLowerCase();
    }

    /**
     * 获取上次打开的目录（供 MainController 保存窗口状态使用）。
     */
    public File getLastOpenDir() {
        return lastOpenDir;
    }

    /**
     * 是否有正在进行的压缩任务。
     */
    public boolean isCompressing() {
        return currentWorker != null && !currentWorker.isDone();
    }

    /**
     * 获取图片文件列表大小（供压缩按钮文案更新）。
     */
    public int getFileCount() {
        return fileListPanel.getImageFileList().size();
    }

    // ==================== 回调接口 ====================

    /**
     * 图片控制器向 MainController 的回调桥梁。
     */
    public interface ImageControllerCallback {
        /**
         * 切换工具栏和面板的压缩/空闲状态。
         *
         * @param compressing true 表示进入压缩中状态
         */
        void setCompressingState(boolean compressing);
    }
}
