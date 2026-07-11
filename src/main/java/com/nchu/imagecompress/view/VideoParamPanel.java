package com.nchu.imagecompress.view;

import com.nchu.imagecompress.model.VideoCompressConfig;
import com.nchu.imagecompress.util.ThemeUtil;

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
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

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

    // ==================== 控件 ====================

    private JSlider crfSlider;
    private JLabel crfValueLabel;
    private JComboBox<String> resolutionCombo;
    private JComboBox<String> fpsCombo;
    private JComboBox<String> audioCombo;
    private JComboBox<String> outputFormatCombo;
    private JButton compressButton;
    private JButton cancelButton;
    private JButton outputDirButton;
    private JCheckBox overwriteCheckBox;
    private JButton activePresetBtn;

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

        // === 中部：参数表单 ===
        add(createParamForm(), BorderLayout.CENTER);

        // === 底部：操作按钮 ===
        add(createButtonPanel(), BorderLayout.SOUTH);
    }

    /**
     * 创建参数表单（GridBagLayout 对齐）。
     */
    private JPanel createParamForm() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(ThemeUtil.SPACE_SM, 0, ThemeUtil.SPACE_SM, 0);
        int row = 0;

        // --- CRF 质量 ---
        addFormLabel(panel, gbc, "CRF 质量", row);

        JPanel crfPanel = new JPanel(new BorderLayout(ThemeUtil.SPACE_SM, 0));
        crfPanel.setOpaque(false);

        crfSlider = new JSlider(0, 51, 23);
        crfSlider.setMajorTickSpacing(10);
        crfSlider.setMinorTickSpacing(1);
        crfSlider.setPaintTicks(true);
        crfSlider.setToolTipText("CRF (Constant Rate Factor)：0=无损，23=默认，51=最低质量");
        crfPanel.add(crfSlider, BorderLayout.CENTER);

        crfValueLabel = new JLabel("23", SwingConstants.RIGHT);
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

        // --- 输出目录 ---
        addFormLabel(panel, gbc, "输出目录", row);
        outputDirButton = new JButton("选择目录...");
        outputDirButton.setFont(ThemeUtil.FONT_BODY);
        addFormControl(panel, gbc, outputDirButton, row);
        row++;

        // --- 覆盖设置 ---
        addFormLabel(panel, gbc, "覆盖设置", row);
        overwriteCheckBox = new JCheckBox("覆盖原文件");
        overwriteCheckBox.setFont(ThemeUtil.FONT_SMALL);
        overwriteCheckBox.setForeground(ThemeUtil.TEXT_SECONDARY);
        overwriteCheckBox.setOpaque(false);
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

        compressButton = new JButton("▶  开始压缩");
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
            crfSlider.setValue(preset.getCrf());
            setCrfDisplay(preset.getCrf());
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

    private static void addFormLabel(JPanel panel, GridBagConstraints gbc, String text, int row) {
        JLabel label = new JLabel(text);
        label.setFont(ThemeUtil.FONT_BODY);
        label.setForeground(ThemeUtil.TEXT_SECONDARY);
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        gbc.gridy = row; gbc.gridx = 0;
        gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.LINE_END;
        gbc.insets = new Insets(ThemeUtil.SPACE_SM, 0, ThemeUtil.SPACE_SM, ThemeUtil.SPACE_LG);
        panel.add(label, gbc);
    }

    private static void addFormControl(JPanel panel, GridBagConstraints gbc,
                                       java.awt.Component comp, int row) {
        gbc.gridy = row; gbc.gridx = 1;
        gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.insets = new Insets(ThemeUtil.SPACE_SM, 0, ThemeUtil.SPACE_SM, 0);
        panel.add(comp, gbc);
    }

    // ==================== 参数读取 ====================

    /** 获取当前 CRF 值 */
    public int getCrf() { return crfSlider.getValue(); }

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

        config.setOverwrite(isOverwrite());
        return config;
    }

    // ==================== 参数恢复 ====================

    /** 恢复 CRF 值 */
    public void setCrf(int crf) {
        crfSlider.setValue(Math.max(0, Math.min(51, crf)));
        setCrfDisplay(crf);
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
}
