package com.nchu.imagecompress.view;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.nchu.imagecompress.model.VideoCompressConfig;
import com.nchu.imagecompress.util.ThemeUtil;
import com.nchu.imagecompress.view.widget.GradientSliderUI;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

/**
 * 视频压缩参数面板 — 与 ParamPanel 风格一致。
 *
 * <p>提供 CRF 质量滑块、分辨率、帧率、音频、输出格式等参数设置，
 * 以及微信/B站/存档三个快捷预设按钮。</p>
 *
 * @author NCHU-Student
 * @version 2.0.0
 * @since 2026-07-11
 */
public class VideoParamPanel extends JPanel {

    // ==================== CRF ↔ 画质映射常量 ====================

    /** 显示画质范围 (0-100, 越高越好) */
    private static final int QUALITY_MIN = 0;
    private static final int QUALITY_MAX = 100;
    /** 默认显示画质值 (对应 CRF 23) */
    private static final int DEFAULT_QUALITY = 55;

    /** 滑块显示值 → 实际 CRF (0-51) */
    private static int sliderToCrf(int quality) {
        return (int) Math.round((QUALITY_MAX - quality) * 51.0 / QUALITY_MAX);
    }

    /** 实际 CRF → 滑块显示值 */
    private static int crfToSlider(int crf) {
        return QUALITY_MAX - (int) Math.round(crf * (double) QUALITY_MAX / 51.0);
    }

    private JSlider crfSlider;
    private JLabel crfValueLabel;
    private JComboBox<String> resolutionCombo;
    private JComboBox<String> fpsCombo;
    private JComboBox<String> audioCombo;
    private JComboBox<String> outputFormatCombo;
    private JButton compressButton;
    private JButton cancelButton;
    private JButton outputDirButton;
    private javax.swing.JTextField customNameField;
    private JCheckBox overwriteCheckBox;
    private JButton activePresetBtn;

    // ==================== 批量导出控件 ====================

    private JCheckBox batchModeCheckBox;
    private JPanel batchSectionPanel;
    private JPanel batchContentPanel;
    private JPanel variantListPanel;
    private JButton addVariantButton;
    private final List<VariantRow> variantRows = new ArrayList<>();
    private static final int MAX_VARIANTS = 20;

    /** 变体行变动回调（通知 MainController 刷新按钮文字） */
    private Runnable onVariantChanged;

    // ==================== 下拉选项映射 ====================

    private static final String[] RESOLUTION_OPTIONS = {"原始", "480p", "720p", "1080p", "4K"};
    private static final String[] FPS_OPTIONS = {"保持原始", "24 fps", "30 fps", "60 fps"};
    private static final String[] AUDIO_OPTIONS = {"保留音频", "移除音频"};
    private static final String[] FORMAT_OPTIONS = {"保持原格式", "MP4 (H.264)", "WebM (VP9)", "AVI", "MOV", "MKV"};

    public VideoParamPanel() {
        setLayout(new BorderLayout(0, 0));
        setBackground(ThemeUtil.BG_CARD);
        setBorder(BorderFactory.createEmptyBorder(ThemeUtil.SPACE_LG, ThemeUtil.SPACE_LG,
                ThemeUtil.SPACE_LG, ThemeUtil.SPACE_LG));

        // === 顶部：标题 ===
        JLabel titleLabel = new JLabel("视频压缩参数");
        titleLabel.setFont(ThemeUtil.FONT_TITLE);
        titleLabel.setForeground(ThemeUtil.TEXT_PRIMARY);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, ThemeUtil.SPACE_SM, 0));
        add(titleLabel, BorderLayout.NORTH);

        // === 中部：参数表单 + 批量导出区块 ===
        JPanel centerWrapper = new JPanel(new BorderLayout(0, 0));
        centerWrapper.setOpaque(false);
        centerWrapper.add(createParamForm(), BorderLayout.NORTH);
        centerWrapper.add(createBatchSection(), BorderLayout.CENTER);
        add(centerWrapper, BorderLayout.CENTER);

        // === 底部：操作按钮 ===
        add(createButtonPanel(), BorderLayout.SOUTH);
    }

    /**
     * 创建参数表单（GridBagLayout 对齐，含视觉分组）。
     */
    private JPanel createParamForm() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(ThemeUtil.SPACE_SM, 0, ThemeUtil.SPACE_SM, 0);
        int row = 0;

        // ==================== 画质预设组 ====================
        addGroupHeader(panel, gbc, "🎯 画质预设", row++);

        // --- 视频画质 ---
        addFormLabel(panel, gbc, "视频画质", row);

        JPanel crfPanel = new JPanel(new BorderLayout(ThemeUtil.SPACE_SM, 0));
        crfPanel.setOpaque(false);

        crfSlider = new JSlider(QUALITY_MIN, QUALITY_MAX, DEFAULT_QUALITY);
        crfSlider.setUI(new GradientSliderUI());
        crfSlider.setMajorTickSpacing(25);
        crfSlider.setMinorTickSpacing(5);
        crfSlider.setPaintTicks(true);
        crfSlider.setToolTipText("视频画质：100=无损，55=标准(CRF23)，0=极限压缩(CRF51)");
        crfPanel.add(crfSlider, BorderLayout.CENTER);

        crfValueLabel = new JLabel(String.valueOf(DEFAULT_QUALITY), SwingConstants.RIGHT);
        crfValueLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        crfValueLabel.setForeground(ThemeUtil.PRIMARY);
        crfValueLabel.setPreferredSize(new java.awt.Dimension(40, 24));
        crfPanel.add(crfValueLabel, BorderLayout.EAST);

        addFormControl(panel, gbc, crfPanel, row);
        row++;

        // 快捷预设按钮
        JPanel presetPanel = new JPanel(new java.awt.FlowLayout(
                java.awt.FlowLayout.LEFT, ThemeUtil.SPACE_SM, 0));
        presetPanel.setOpaque(false);
        presetPanel.add(createPresetBtn("微信小视频", VideoCompressConfig.Preset.WECHAT));
        presetPanel.add(createPresetBtn("B站投稿", VideoCompressConfig.Preset.BILIBILI));
        presetPanel.add(createPresetBtn("存档原画", VideoCompressConfig.Preset.ARCHIVE));

        JPanel presetWrapper = new JPanel(new BorderLayout());
        presetWrapper.setOpaque(false);
        presetWrapper.add(presetPanel, BorderLayout.WEST);
        addFormControl(panel, gbc, presetWrapper, row);
        row++;

        // ==================== 画面参数组 ====================
        addGroupHeader(panel, gbc, "画面参数", row++);

        // --- 分辨率 ---
        addFormLabel(panel, gbc, "分辨率", row);
        resolutionCombo = new JComboBox<>(RESOLUTION_OPTIONS);
        addFormControl(panel, gbc, resolutionCombo, row);
        row++;

        // --- 帧率 ---
        addFormLabel(panel, gbc, "帧率", row);
        fpsCombo = new JComboBox<>(FPS_OPTIONS);
        addFormControl(panel, gbc, fpsCombo, row);
        row++;

        // ==================== 音视频格式组 ====================
        addGroupHeader(panel, gbc, "音视频格式", row++);

        // --- 音频 ---
        addFormLabel(panel, gbc, "音频", row);
        audioCombo = new JComboBox<>(AUDIO_OPTIONS);
        addFormControl(panel, gbc, audioCombo, row);
        row++;

        // --- 输出格式 ---
        addFormLabel(panel, gbc, "输出格式", row);
        outputFormatCombo = new JComboBox<>(FORMAT_OPTIONS);
        addFormControl(panel, gbc, outputFormatCombo, row);
        row++;

        // ==================== 输出设置组 ====================
        addGroupHeader(panel, gbc, "📂 输出设置", row++);

        // --- 自定义文件名 ---
        addFormLabel(panel, gbc, "输出文件名", row);
        customNameField = new javax.swing.JTextField(20);
        customNameField.setFont(ThemeUtil.FONT_SMALL);
        customNameField.setToolTipText("输入自定义文件名（不含扩展名），留空则自动生成");
        addFormControl(panel, gbc, customNameField, row);
        row++;

        // --- 输出目录 ---
        addFormLabel(panel, gbc, "输出目录", row);
        outputDirButton = new JButton("选择目录...");
        outputDirButton.setFont(ThemeUtil.FONT_BODY);
        addFormControl(panel, gbc, outputDirButton, row);
        row++;

        // --- 覆盖设置 ---
        addFormLabel(panel, gbc, "覆盖设置", row);
        overwriteCheckBox = new JCheckBox("覆盖已存在的输出文件");
        overwriteCheckBox.setFont(ThemeUtil.FONT_SMALL);
        overwriteCheckBox.setForeground(ThemeUtil.TEXT_SECONDARY);
        overwriteCheckBox.setOpaque(false);
        overwriteCheckBox.setSelected(true); // 默认覆盖，方便重复压缩
        addFormControl(panel, gbc, overwriteCheckBox, row);
        row++;

        // 弹性填充
        gbc.gridy = row; gbc.gridx = 0; gbc.gridwidth = 2;
        gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH;
        panel.add(new JLabel(), gbc);

        return panel;
    }

    /**
     * 创建底部操作按钮面板。
     */
    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new BorderLayout(ThemeUtil.SPACE_MD, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(ThemeUtil.SPACE_LG, 0, 0, 0));

        JPanel rightBtns = new JPanel(new java.awt.FlowLayout(
                java.awt.FlowLayout.RIGHT, ThemeUtil.SPACE_SM, 0));
        rightBtns.setOpaque(false);

        cancelButton = new JButton("取消");
        cancelButton.setFont(ThemeUtil.FONT_BODY);
        cancelButton.setEnabled(false);
        cancelButton.setFocusPainted(false);
        cancelButton.setForeground(ThemeUtil.TEXT_SECONDARY);
        cancelButton.setBackground(ThemeUtil.BG_CARD);
        cancelButton.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        cancelButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cancelButton.setBorderPainted(false);
        rightBtns.add(cancelButton);

        compressButton = new JButton("开始压缩", new FlatSVGIcon("icons/play.svg"));
        compressButton.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        compressButton.setEnabled(false);
        compressButton.setFocusPainted(false);
        compressButton.setBackground(ThemeUtil.PRIMARY);
        compressButton.setForeground(java.awt.Color.WHITE);
        compressButton.setBorder(BorderFactory.createEmptyBorder(12, 28, 12, 28));
        compressButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        rightBtns.add(compressButton);

        buttonPanel.add(rightBtns, BorderLayout.EAST);
        return buttonPanel;
    }

    // ==================== 预设按钮 ====================

    private JButton createPresetBtn(String text, VideoCompressConfig.Preset preset) {
        JButton btn = new JButton(text);
        btn.setFont(ThemeUtil.FONT_SMALL);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(4, 14, 4, 14));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        applyPresetUnselected(btn);

        btn.addActionListener(e -> {
            if (activePresetBtn != null && activePresetBtn != btn) {
                applyPresetUnselected(activePresetBtn);
            }
            if (activePresetBtn == btn) {
                activePresetBtn = null;
                applyPresetUnselected(btn);
            } else {
                activePresetBtn = btn;
                applyPresetSelected(btn);
            }
            // 应用预设参数
            int displayVal = crfToSlider(preset.getCrf());
            crfSlider.setValue(displayVal);
            setCrfDisplay(displayVal);
            resolutionCombo.setSelectedIndex(preset.getResolution().ordinal());
            fpsCombo.setSelectedIndex(preset.getFps().ordinal());
            audioCombo.setSelectedIndex(preset.getAudio().ordinal());
        });
        return btn;
    }

    private void applyPresetSelected(JButton btn) {
        btn.setBackground(ThemeUtil.PRIMARY);
        btn.setForeground(java.awt.Color.WHITE);
    }

    private void applyPresetUnselected(JButton btn) {
        btn.setBackground(ThemeUtil.BG_HOVER);
        btn.setForeground(ThemeUtil.TEXT_SECONDARY);
    }

    // ==================== 表单辅助方法 ====================

    /** 添加分组标题行（加粗标题 + 底部 2px 主题色分隔线，跨 2 列） */
    private static void addGroupHeader(JPanel panel, GridBagConstraints gbc, String title, int row) {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0, ThemeUtil.PRIMARY),
                BorderFactory.createEmptyBorder(ThemeUtil.SPACE_BLOCK, 0, 4, 0)));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(ThemeUtil.FONT_TITLE);
        titleLabel.setForeground(ThemeUtil.PRIMARY);
        headerPanel.add(titleLabel, BorderLayout.WEST);

        gbc.gridy = row; gbc.gridx = 0; gbc.gridwidth = 2;
        gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, ThemeUtil.SPACE_SM, 0);
        gbc.anchor = GridBagConstraints.LINE_START;
        panel.add(headerPanel, gbc);
        gbc.insets = new Insets(ThemeUtil.SPACE_ROW, 0, ThemeUtil.SPACE_ROW,
                ThemeUtil.SPACE_LABEL_GAP);
    }

    private static void addFormLabel(JPanel panel, GridBagConstraints gbc, String text, int row) {
        JLabel label = new JLabel(text);
        label.setFont(ThemeUtil.FONT_TITLE);
        label.setForeground(ThemeUtil.TEXT_PRIMARY);
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        gbc.gridy = row; gbc.gridx = 0;
        gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.LINE_END;
        gbc.insets = new Insets(ThemeUtil.SPACE_ROW, 0, ThemeUtil.SPACE_ROW,
                ThemeUtil.SPACE_LABEL_GAP);
        panel.add(label, gbc);
    }

    private static void addFormControl(JPanel panel, GridBagConstraints gbc,
                                       java.awt.Component comp, int row) {
        gbc.gridy = row; gbc.gridx = 1;
        gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.insets = new Insets(ThemeUtil.SPACE_ROW, 0, ThemeUtil.SPACE_ROW, 0);
        panel.add(comp, gbc);
    }

    // ==================== 参数读取 ====================

    /** 获取实际 CRF 值（滑块显示值 → CRF 0-51） */
    public int getCrf() { return sliderToCrf(crfSlider.getValue()); }

    /** 获取滑块显示画质值 (0-100) */
    public int getQualityDisplay() { return crfSlider.getValue(); }

    /** 获取分辨率模式索引 */
    public int getResolutionIndex() { return resolutionCombo.getSelectedIndex(); }

    /** 获取帧率模式索引 */
    public int getFpsIndex() { return fpsCombo.getSelectedIndex(); }

    /** 获取音频模式索引 */
    public int getAudioIndex() { return audioCombo.getSelectedIndex(); }

    /** 获取输出格式索引 */
    public int getOutputFormatIndex() { return outputFormatCombo.getSelectedIndex(); }

    /** 是否允许覆盖 */
    public boolean isOverwrite() { return overwriteCheckBox.isSelected(); }
    public String getCustomFileName() { return customNameField.getText(); }
    public void setCustomFileName(String name) { customNameField.setText(name != null ? name : ""); }

    // ==================== 构建配置 ====================

    /**
     * 从面板参数构建 VideoCompressConfig。
     */
    public VideoCompressConfig buildConfig() {
        VideoCompressConfig config = new VideoCompressConfig();
        config.setCrf(getCrf());

        // 分辨率
        VideoCompressConfig.ResolutionMode[] resolutions =
                VideoCompressConfig.ResolutionMode.values();
        config.setResolutionMode(resolutions[getResolutionIndex()]);

        // 帧率
        VideoCompressConfig.FpsMode[] fpsModes = VideoCompressConfig.FpsMode.values();
        config.setFpsMode(fpsModes[getFpsIndex()]);

        // 音频
        config.setAudioMode(getAudioIndex() == 1
                ? VideoCompressConfig.AudioMode.REMOVE
                : VideoCompressConfig.AudioMode.KEEP);

        // 输出格式
        VideoCompressConfig.VideoFormat[] formats = VideoCompressConfig.VideoFormat.values();
        config.setOutputFormat(formats[getOutputFormatIndex()]);

        config.setSuffix(config.buildDynamicSuffix());
        config.setCustomName(getCustomFileName());
        config.setOverwrite(isOverwrite());
        return config;
    }

    // ==================== 参数恢复 ====================

    /** 恢复 CRF 值（实际 CRF → 滑块显示值） */
    public void setCrf(int crf) {
        int displayVal = crfToSlider(crf);
        crfSlider.setValue(Math.max(QUALITY_MIN, Math.min(QUALITY_MAX, displayVal)));
        setCrfDisplay(displayVal);
    }

    /** 恢复分辨率模式 */
    public void setResolutionMode(VideoCompressConfig.ResolutionMode mode) {
        resolutionCombo.setSelectedIndex(mode.ordinal());
    }

    /** 恢复帧率模式 */
    public void setFpsMode(VideoCompressConfig.FpsMode mode) {
        fpsCombo.setSelectedIndex(mode.ordinal());
    }

    /** 恢复音频模式 */
    public void setAudioMode(VideoCompressConfig.AudioMode mode) {
        audioCombo.setSelectedIndex(mode == VideoCompressConfig.AudioMode.REMOVE ? 1 : 0);
    }

    /** 恢复输出格式 */
    public void setOutputFormat(VideoCompressConfig.VideoFormat format) {
        outputFormatCombo.setSelectedIndex(format.ordinal());
    }

    /** 更新 CRF 显示标签 */
    public void setCrfDisplay(int crf) {
        crfValueLabel.setText(String.valueOf(crf));
    }

    // ==================== 控件访问器 ====================

    public JSlider getCrfSlider() { return crfSlider; }
    public JButton getCompressButton() { return compressButton; }
    public JButton getCancelButton() { return cancelButton; }
    public JButton getOutputDirButton() { return outputDirButton; }
    public JComboBox<String> getResolutionCombo() { return resolutionCombo; }
    public JComboBox<String> getFpsCombo() { return fpsCombo; }
    public JComboBox<String> getAudioCombo() { return audioCombo; }
    public JComboBox<String> getOutputFormatCombo() { return outputFormatCombo; }
    public JCheckBox getOverwriteCheckBox() { return overwriteCheckBox; }

    /** 批量模式复选框（供 Controller 绑定事件） */
    public JCheckBox getBatchModeCheckBox() { return batchModeCheckBox; }

    /** 设置变体行变动回调（Controller 用于刷新按钮文字） */
    public void setOnVariantChanged(Runnable callback) { this.onVariantChanged = callback; }

    // ==================== 批量导出 API ====================

    /** 批量模式开关 */
    public boolean isBatchMode() {
        return batchModeCheckBox.isSelected();
    }

    /** 获取所有变体（批量模式下调用） */
    public List<VideoCompressConfig.VariantPreset> getBatchVariants() {
        List<VideoCompressConfig.VariantPreset> presets = new ArrayList<>();
        for (VariantRow row : variantRows) {
            presets.add(row.buildPreset());
        }
        return presets;
    }

    /** 变体数量（用于按钮文案动态更新） */
    public int getBatchVariantCount() {
        return variantRows.size();
    }

    /** 更新压缩按钮文案（批量模式 ↔ 单版本模式） */
    public void updateCompressButtonText(int fileCount) {
        if (isBatchMode()) {
            int total = fileCount * variantRows.size();
            compressButton.setText("批量导出 " + total + " 个版本");
        } else {
            compressButton.setText("开始压缩");
        }
    }

    // ==================== 批量导出 UI 构建 ====================

    /**
     * 创建批量导出区块（复选框始终可见，变体列表可折叠）。
     */
    private JPanel createBatchSection() {
        // 外层容器 — 始终可见
        batchSectionPanel = new JPanel(new BorderLayout(0, ThemeUtil.SPACE_SM));
        batchSectionPanel.setOpaque(false);
        batchSectionPanel.setBorder(BorderFactory.createEmptyBorder(
                ThemeUtil.SPACE_SM, 0, ThemeUtil.SPACE_SM, 0));

        // --- 复选框（始终可见） ---
        batchModeCheckBox = new JCheckBox("启用多版本批量导出");
        batchModeCheckBox.setFont(ThemeUtil.FONT_BODY);
        batchModeCheckBox.setForeground(ThemeUtil.TEXT_PRIMARY);
        batchModeCheckBox.setOpaque(false);
        batchModeCheckBox.addActionListener(e -> {
            boolean on = batchModeCheckBox.isSelected();
            batchContentPanel.setVisible(on);
            if (on && variantRows.isEmpty()) {
                addVariant(); // 默认添加一行
            }
            // 按钮文字由 MainController.updateVideoCompressButtonState() 负责更新
            // （MainController 在 batchModeCheckBox 上注册了第二个监听器，持有实际文件数）
        });
        batchSectionPanel.add(batchModeCheckBox, BorderLayout.NORTH);

        // --- 变体内容区（可折叠） ---
        batchContentPanel = new JPanel(new BorderLayout(0, ThemeUtil.SPACE_SM));
        batchContentPanel.setOpaque(false);
        batchContentPanel.setVisible(false); // 默认折叠

        variantListPanel = new JPanel();
        variantListPanel.setLayout(new javax.swing.BoxLayout(
                variantListPanel, javax.swing.BoxLayout.Y_AXIS));
        variantListPanel.setOpaque(false);

        JPanel scrollWrapper = new JPanel(new BorderLayout());
        scrollWrapper.setOpaque(false);
        scrollWrapper.add(variantListPanel, BorderLayout.NORTH);

        javax.swing.JScrollPane scrollPane = new javax.swing.JScrollPane(scrollWrapper);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setPreferredSize(new Dimension(400, 180));
        scrollPane.setHorizontalScrollBarPolicy(
                javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        batchContentPanel.add(scrollPane, BorderLayout.CENTER);

        addVariantButton = new JButton("＋ 添加变体");
        addVariantButton.setFont(ThemeUtil.FONT_SMALL);
        addVariantButton.setFocusPainted(false);
        addVariantButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addVariantButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createDashedBorder(
                        ThemeUtil.TEXT_TERTIARY, 2.0f, 4.0f, 2.0f, true),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)));
        addVariantButton.setBackground(ThemeUtil.BG_CARD);
        addVariantButton.setForeground(ThemeUtil.TEXT_SECONDARY);
        addVariantButton.addActionListener(e -> addVariant());
        batchContentPanel.add(addVariantButton, BorderLayout.SOUTH);

        batchSectionPanel.add(batchContentPanel, BorderLayout.CENTER);

        return batchSectionPanel;
    }

    /** 添加一个新变体行 */
    private void addVariant() {
        if (variantRows.size() >= MAX_VARIANTS) {
            ToastNotification.warning("最多添加 " + MAX_VARIANTS + " 个变体");
            return;
        }
        VariantRow row = new VariantRow(getQualityDisplay());
        row.setOnDelete(() -> removeVariant(row));
        variantRows.add(row);
        variantListPanel.add(row);
        variantListPanel.revalidate();
        variantListPanel.repaint();
        if (onVariantChanged != null) onVariantChanged.run();
    }

    /** 移除指定变体行 */
    private void removeVariant(VariantRow row) {
        variantRows.remove(row);
        variantListPanel.remove(row);
        variantListPanel.revalidate();
        variantListPanel.repaint();
        if (onVariantChanged != null) onVariantChanged.run();
    }

    // ==================== 变体行内部类 ====================

    /**
     * 单个变体配置行。
     *
     * <p>水平布局：{@code [Q: slider] [分辨率] [帧率] [音频] [格式] [✕]}</p>
     */
    private static class VariantRow extends JPanel {

        private final JSlider crfSlider;
        private final JLabel crfLabel;
        private final JComboBox<String> resolutionCombo;
        private final JComboBox<String> fpsCombo;
        private final JComboBox<String> audioCombo;
        private final JComboBox<String> formatCombo;
        private final JButton deleteBtn;
        private Runnable onDelete;

        private static final String[] RES_ITEMS = {"原始", "480p", "720p", "1080p", "4K"};
        private static final VideoCompressConfig.ResolutionMode[] RES_MODES = {
            VideoCompressConfig.ResolutionMode.ORIGINAL,
            VideoCompressConfig.ResolutionMode.R480P,
            VideoCompressConfig.ResolutionMode.R720P,
            VideoCompressConfig.ResolutionMode.R1080P,
            VideoCompressConfig.ResolutionMode.R4K,
        };
        private static final String[] FPS_ITEMS = {"保持原始", "24 fps", "30 fps", "60 fps"};
        private static final VideoCompressConfig.FpsMode[] FPS_MODES = {
            VideoCompressConfig.FpsMode.ORIGINAL,
            VideoCompressConfig.FpsMode.FPS_24,
            VideoCompressConfig.FpsMode.FPS_30,
            VideoCompressConfig.FpsMode.FPS_60,
        };
        private static final String[] AUDIO_ITEMS = {"保留音频", "移除音频"};
        private static final VideoCompressConfig.AudioMode[] AUDIO_MODES = {
            VideoCompressConfig.AudioMode.KEEP,
            VideoCompressConfig.AudioMode.REMOVE,
        };
        private static final String[] FMT_ITEMS = {"保持原格式", "MP4", "WebM", "AVI", "MOV", "MKV"};
        private static final VideoCompressConfig.VideoFormat[] FMT_MODES = {
            VideoCompressConfig.VideoFormat.ORIGINAL,
            VideoCompressConfig.VideoFormat.MP4,
            VideoCompressConfig.VideoFormat.WEBM,
            VideoCompressConfig.VideoFormat.AVI,
            VideoCompressConfig.VideoFormat.MOV,
            VideoCompressConfig.VideoFormat.MKV,
        };

        VariantRow(int defaultQuality) {
            setLayout(new FlowLayout(FlowLayout.LEFT, 4, 2));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

            // --- 画质滑块 + 标签 ---
            JPanel crfPanel = new JPanel(new BorderLayout(2, 0));
            crfPanel.setOpaque(false);
            JLabel ql = new JLabel("Q:");
            ql.setFont(ThemeUtil.FONT_SMALL);
            ql.setForeground(ThemeUtil.TEXT_SECONDARY);
            crfPanel.add(ql, BorderLayout.WEST);

            crfSlider = new JSlider(QUALITY_MIN, QUALITY_MAX, defaultQuality);
            crfSlider.setOpaque(false);
            crfSlider.setPreferredSize(new Dimension(70, 24));
            crfSlider.setPaintTicks(false);

            crfLabel = new JLabel(String.valueOf(defaultQuality));
            crfLabel.setFont(ThemeUtil.FONT_SMALL);
            crfLabel.setForeground(ThemeUtil.TEXT_PRIMARY);
            crfLabel.setPreferredSize(new Dimension(22, 24));
            crfLabel.setHorizontalAlignment(SwingConstants.CENTER);

            crfSlider.addChangeListener(e -> crfLabel.setText(String.valueOf(crfSlider.getValue())));
            crfPanel.add(crfSlider, BorderLayout.CENTER);
            crfPanel.add(crfLabel, BorderLayout.EAST);
            add(crfPanel);

            // --- 分辨率 ---
            JPanel resPanel = labeledCombo("分辨率:", RES_ITEMS);
            resolutionCombo = (JComboBox<String>) resPanel.getComponent(1);
            add(resPanel);

            // --- 帧率 ---
            JPanel fpsPanel = labeledCombo("帧率:", FPS_ITEMS);
            fpsCombo = (JComboBox<String>) fpsPanel.getComponent(1);
            add(fpsPanel);

            // --- 音频 ---
            JPanel audPanel = labeledCombo("音频:", AUDIO_ITEMS);
            audioCombo = (JComboBox<String>) audPanel.getComponent(1);
            add(audPanel);

            // --- 格式 ---
            JPanel fmtPanel = labeledCombo("格式:", FMT_ITEMS);
            formatCombo = (JComboBox<String>) fmtPanel.getComponent(1);
            add(fmtPanel);

            // --- 删除按钮 ---
            deleteBtn = new JButton("✕");
            deleteBtn.setFont(new Font("Dialog", Font.PLAIN, 10));
            deleteBtn.setFocusPainted(false);
            deleteBtn.setPreferredSize(new Dimension(24, 24));
            deleteBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            deleteBtn.setForeground(ThemeUtil.ERROR);
            deleteBtn.setBorder(BorderFactory.createEmptyBorder());
            deleteBtn.setContentAreaFilled(false);
            deleteBtn.addActionListener(e -> {
                if (onDelete != null) onDelete.run();
            });
            add(deleteBtn);
        }

        /** 辅助：创建 {label + combo} 水平面板 */
        private static JPanel labeledCombo(String label, String[] items) {
            JPanel p = new JPanel(new BorderLayout(2, 0));
            p.setOpaque(false);
            JLabel l = new JLabel(label);
            l.setFont(ThemeUtil.FONT_SMALL);
            l.setForeground(ThemeUtil.TEXT_SECONDARY);
            p.add(l, BorderLayout.WEST);
            JComboBox<String> c = new JComboBox<>(items);
            c.setFont(ThemeUtil.FONT_SMALL);
            p.add(c, BorderLayout.CENTER);
            return p;
        }

        /** 设置删除回调（在 VariantRow 添加到列表后由外部调用） */
        void setOnDelete(Runnable onDelete) {
            this.onDelete = onDelete;
        }

        /** 从 UI 控件构建 VariantPreset */
        VideoCompressConfig.VariantPreset buildPreset() {
            VideoCompressConfig.VariantPreset preset = new VideoCompressConfig.VariantPreset();
            preset.setCrf(sliderToCrf(crfSlider.getValue()));
            preset.setResolutionMode(RES_MODES[resolutionCombo.getSelectedIndex()]);
            preset.setFpsMode(FPS_MODES[fpsCombo.getSelectedIndex()]);
            preset.setAudioMode(AUDIO_MODES[audioCombo.getSelectedIndex()]);
            preset.setOutputFormat(FMT_MODES[formatCombo.getSelectedIndex()]);
            return preset;
        }
    }
}
