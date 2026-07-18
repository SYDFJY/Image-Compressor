package com.nchu.imagecompress.view;

import com.nchu.imagecompress.util.ThemeUtil;
import com.nchu.imagecompress.view.widget.GradientSliderUI;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * 参数设置面板（UI 升级版 — 强对齐表单 + 精致控件）。
 *
 * <p>升级要点：
 * <ul>
 *   <li>标签右对齐 + 控件左对齐，形成垂直视觉参考线</li>
 *   <li>滑块：4px 轨道 + 16px 圆形把手 + 右侧百分比</li>
 *   <li>预设按钮：「标准」「高效」「高清」一键切换质量</li>
 *   <li>选项卡极简：底部下划线指示条，无框无背景</li>
 *   <li>操作按钮：取消(文字) + 开始压缩(主按钮 42px)</li>
 * </ul>
 *
 * @author NCHU-Student
 * @version 2.0.0
 * @since 2026-07-08
 */
public class ParamPanel extends JPanel {

    private JSlider qualitySlider;
    private JLabel qualityValueLabel;
    private JComboBox<String> scaleModeCombo;
    private JSpinner scalePercentSpinner;
    private JComboBox<String> outputFormatCombo;
    private JComboBox<String> namingRuleCombo;
    private javax.swing.JTextField customNameField;
    private JButton compressButton;
    private JButton cancelButton;
    private JButton outputDirButton;
    private JCheckBox overwriteCheckBox;
    private JCheckBox preserveMetadataCheckBox;
    private javax.swing.JCheckBox targetSizeCheckBox;
    private javax.swing.JSpinner targetSizeSpinner;
    private JButton activePresetBtn;

    public ParamPanel() {
        setLayout(new BorderLayout(0, 0));
        setBackground(ThemeUtil.BG_CARD);
        setBorder(BorderFactory.createEmptyBorder(ThemeUtil.SPACE_LG, ThemeUtil.SPACE_LG,
                ThemeUtil.SPACE_LG, ThemeUtil.SPACE_LG));

        // === 顶部：标题（参考蓝韵音乐 section-title 样式） ===
        JLabel titleLabel = new JLabel("压 缩 参 数");
        titleLabel.setFont(ThemeUtil.FONT_SMALL.deriveFont(Font.BOLD));
        titleLabel.setForeground(ThemeUtil.TEXT_TERTIARY);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, ThemeUtil.SPACE_SM, 0));
        add(titleLabel, BorderLayout.NORTH);

        // === 中部：选项卡（基础 / 高级）下划线样式 ===
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(ThemeUtil.FONT_BODY);
        tabbedPane.putClientProperty("JTabbedPane.tabType", "underlined");
        tabbedPane.addTab("基础设置", createBasicTab());
        tabbedPane.addTab("高级设置", createAdvancedTab());

        // 选项卡切换时加粗选中标签
        tabbedPane.addChangeListener(e -> {
            int idx = tabbedPane.getSelectedIndex();
            for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                java.awt.Component tabComp = tabbedPane.getTabComponentAt(i);
                if (tabComp instanceof JLabel) {
                    JLabel tabLabel = (JLabel) tabComp;
                    tabLabel.setFont(i == idx ? ThemeUtil.FONT_TITLE : ThemeUtil.FONT_BODY);
                }
            }
        });

        add(tabbedPane, BorderLayout.CENTER);

        // === 底部：操作按钮（纯净右侧） ===
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

        compressButton = new JButton("开始压缩", new com.formdev.flatlaf.extras.FlatSVGIcon("icons/play.svg"));
        compressButton.setFont(new java.awt.Font("Microsoft YaHei", java.awt.Font.BOLD, 14));
        compressButton.setEnabled(false);
        compressButton.setFocusPainted(false);
        compressButton.setBackground(ThemeUtil.PRIMARY);
        compressButton.setForeground(java.awt.Color.WHITE);
        compressButton.setBorder(BorderFactory.createEmptyBorder(12, 28, 12, 28));
        compressButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        rightBtns.add(compressButton);

        buttonPanel.add(rightBtns, BorderLayout.EAST);
        add(buttonPanel, BorderLayout.SOUTH);

        // 主题切换时刷新显式背景色
        ThemeUtil.addThemeChangeListener(() -> {
            setBackground(ThemeUtil.BG_CARD);
            cancelButton.setBackground(ThemeUtil.BG_CARD);
            compressButton.setBackground(ThemeUtil.PRIMARY);
            repaint();
        });
    }

    private JPanel createBasicTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(ThemeUtil.SPACE_SM, 0, ThemeUtil.SPACE_SM, 0);
        int row = 0;

        // --- 压缩质量 ---
        addFormLabel(panel, gbc, "压缩质量", row);

        JPanel qualityPanel = new JPanel(new BorderLayout(ThemeUtil.SPACE_SM, 0));
        qualityPanel.setOpaque(false);

        qualitySlider = new JSlider(0, 100, 80);
        qualitySlider.setUI(new GradientSliderUI());
        qualityPanel.add(qualitySlider, BorderLayout.CENTER);

        qualityValueLabel = new JLabel("80%", SwingConstants.RIGHT);
        qualityValueLabel.setFont(new java.awt.Font("Microsoft YaHei", java.awt.Font.BOLD, 14));
        qualityValueLabel.setForeground(ThemeUtil.PRIMARY);
        qualityValueLabel.setPreferredSize(new java.awt.Dimension(40, 24));
        qualityPanel.add(qualityValueLabel, BorderLayout.EAST);

        addFormControl(panel, gbc, qualityPanel, row);
        row++;

        // 快捷预设按钮
        JPanel presetPanel = new JPanel(new java.awt.FlowLayout(
                java.awt.FlowLayout.LEFT, ThemeUtil.SPACE_SM, 0));
        presetPanel.setOpaque(false);
        presetPanel.add(createPresetBtn("标准", 80));
        presetPanel.add(createPresetBtn("高效", 60));
        presetPanel.add(createPresetBtn("高清", 90));

        JPanel presetWrapper = new JPanel(new BorderLayout());
        presetWrapper.setOpaque(false);
        presetWrapper.add(presetPanel, BorderLayout.WEST);
        addFormControl(panel, gbc, presetWrapper, row);
        row++;

        // --- 缩放模式 ---
        addFormLabel(panel, gbc, "缩放模式", row);
        scaleModeCombo = new JComboBox<>(new String[]{"不缩放", "按百分比", "按最大尺寸"});
        addFormControl(panel, gbc, scaleModeCombo, row);
        row++;

        // --- 缩放百分比 ---
        addFormLabel(panel, gbc, "百分比", row);
        scalePercentSpinner = new JSpinner(new SpinnerNumberModel(100, 1, 100, 5));
        addFormControl(panel, gbc, scalePercentSpinner, row);
        row++;

        // --- 输出格式 ---
        addFormLabel(panel, gbc, "输出格式", row);
        outputFormatCombo = new JComboBox<>(new String[]{"保持原格式", "JPEG", "PNG", "BMP", "WebP"});
        addFormControl(panel, gbc, outputFormatCombo, row);
        row++;

        // 弹性填充
        gbc.gridy = row; gbc.gridx = 0; gbc.gridwidth = 2;
        gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH;
        panel.add(new JLabel(), gbc);

        return panel;
    }

    private JPanel createAdvancedTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(ThemeUtil.SPACE_SM, 0, ThemeUtil.SPACE_SM, 0);
        int row = 0;

        addFormLabel(panel, gbc, "文件命名", row);
        namingRuleCombo = new JComboBox<>(new String[]{"添加后缀 _compressed", "添加前缀 compressed_", "保持原名", "自定义文件名"});
        namingRuleCombo.setFont(ThemeUtil.FONT_SMALL);
        addFormControl(panel, gbc, namingRuleCombo, row);
        row++;

        // 自定义文件名输入框（仅当选择"自定义文件名"时可见）
        addFormLabel(panel, gbc, "", row);
        customNameField = new javax.swing.JTextField(20);
        customNameField.setFont(ThemeUtil.FONT_SMALL);
        customNameField.setToolTipText("输入自定义文件名（不含扩展名），留空则使用默认命名");
        customNameField.setVisible(false);
        addFormControl(panel, gbc, customNameField, row);
        row++;

        // 下拉框切换时显示/隐藏自定义文件名输入框
        namingRuleCombo.addItemListener(e -> {
            if (e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
                boolean isCustom = namingRuleCombo.getSelectedIndex() == 3;
                customNameField.setVisible(isCustom);
                panel.revalidate();
                panel.repaint();
            }
        });

        addFormLabel(panel, gbc, "输出目录", row);
        outputDirButton = new JButton("选择目录...");
        outputDirButton.setFont(ThemeUtil.FONT_BODY);
        addFormControl(panel, gbc, outputDirButton, row);
        row++;

        // 覆盖原文件（从底部移至此处）
        addFormLabel(panel, gbc, "覆盖设置", row);
        overwriteCheckBox = new JCheckBox("覆盖原文件");
        overwriteCheckBox.setFont(ThemeUtil.FONT_SMALL);
        overwriteCheckBox.setForeground(ThemeUtil.TEXT_SECONDARY);
        overwriteCheckBox.setOpaque(false);
        addFormControl(panel, gbc, overwriteCheckBox, row);
        row++;

        // EXIF 元数据保留
        addFormLabel(panel, gbc, "EXIF信息", row);
        preserveMetadataCheckBox = new JCheckBox("保留照片EXIF/XMP信息（文件略大）");
        preserveMetadataCheckBox.setFont(ThemeUtil.FONT_SMALL);
        preserveMetadataCheckBox.setForeground(ThemeUtil.TEXT_SECONDARY);
        preserveMetadataCheckBox.setOpaque(false);
        addFormControl(panel, gbc, preserveMetadataCheckBox, row);
        row++;

        // v2: 目标大小压缩
        addFormLabel(panel, gbc, "目标大小", row);
        JPanel targetSizePanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        targetSizePanel.setOpaque(false);
        targetSizeCheckBox = new javax.swing.JCheckBox("压缩到指定大小");
        targetSizeCheckBox.setFont(ThemeUtil.FONT_SMALL);
        targetSizeCheckBox.setForeground(ThemeUtil.TEXT_SECONDARY);
        targetSizeCheckBox.setOpaque(false);
        targetSizePanel.add(targetSizeCheckBox);
        targetSizeSpinner = new javax.swing.JSpinner(new javax.swing.SpinnerNumberModel(500, 10, 50000, 10));
        targetSizeSpinner.setFont(ThemeUtil.FONT_SMALL);
        targetSizeSpinner.setPreferredSize(new java.awt.Dimension(80, 24));
        targetSizeSpinner.setEnabled(false);
        targetSizePanel.add(targetSizeSpinner);
        javax.swing.JLabel kbLabel = new javax.swing.JLabel("KB");
        kbLabel.setFont(ThemeUtil.FONT_SMALL);
        kbLabel.setForeground(ThemeUtil.TEXT_SECONDARY);
        targetSizePanel.add(kbLabel);
        targetSizeCheckBox.addActionListener(e -> targetSizeSpinner.setEnabled(targetSizeCheckBox.isSelected()));
        addFormControl(panel, gbc, targetSizePanel, row);
        row++;

        gbc.gridy = row; gbc.gridx = 0; gbc.gridwidth = 2;
        gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH;
        panel.add(new JLabel(), gbc);

        return panel;
    }

    /** 创建标签式预设按钮（toggle 行为：选中主色填充+白字，未选中灰底灰字） */
    private JButton createPresetBtn(String text, int quality) {
        JButton btn = new JButton(text);
        btn.setFont(ThemeUtil.FONT_SMALL);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(4, 14, 4, 14));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        applyPresetUnselected(btn);

        btn.addActionListener(e -> {
            // 取消所有预设选中
            if (activePresetBtn != null && activePresetBtn != btn) {
                applyPresetUnselected(activePresetBtn);
            }
            // 切换当前按钮
            if (activePresetBtn == btn) {
                activePresetBtn = null;
                applyPresetUnselected(btn);
            } else {
                activePresetBtn = btn;
                applyPresetSelected(btn);
            }
            qualitySlider.setValue(quality);
            setQualityDisplay(quality);
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

    /** 表单标签（右对齐，加粗，主题色） */
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

    /** 表单控件（左对齐） */
    private static void addFormControl(JPanel panel, GridBagConstraints gbc,
                                       java.awt.Component comp, int row) {
        gbc.gridy = row; gbc.gridx = 1;
        gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.insets = new Insets(ThemeUtil.SPACE_ROW, 0, ThemeUtil.SPACE_ROW, 0);
        panel.add(comp, gbc);
    }

    // ==================== API 兼容 ====================

    public int getQuality() { return qualitySlider.getValue(); }
    public int getScaleModeIndex() { return scaleModeCombo.getSelectedIndex(); }
    public int getScalePercent() { return (int) scalePercentSpinner.getValue(); }
    public int getOutputFormatIndex() { return outputFormatCombo.getSelectedIndex(); }
    public int getNamingRuleIndex() { return namingRuleCombo.getSelectedIndex(); }
    public String getCustomFileName() { return customNameField.getText(); }
    public void setCustomFileName(String name) { customNameField.setText(name != null ? name : ""); }
    public boolean isOverwrite() { return overwriteCheckBox.isSelected(); }
    public boolean isPreserveMetadata() { return preserveMetadataCheckBox.isSelected(); }
    public boolean isTargetSizeEnabled() { return targetSizeCheckBox != null && targetSizeCheckBox.isSelected(); }
    public int getTargetSizeKB() { return targetSizeCheckBox != null && targetSizeCheckBox.isSelected() ? (int) targetSizeSpinner.getValue() : 0; }
    public void setTargetSizeKB(int kb) {
        if (targetSizeCheckBox != null && targetSizeSpinner != null) {
            if (kb > 0) { targetSizeCheckBox.setSelected(true); targetSizeSpinner.setEnabled(true); targetSizeSpinner.setValue(kb); }
            else { targetSizeCheckBox.setSelected(false); targetSizeSpinner.setEnabled(false); }
        }
    }

    public JButton getCompressButton() { return compressButton; }
    public JButton getCancelButton() { return cancelButton; }
    public JButton getOutputDirButton() { return outputDirButton; }
    public JSlider getQualitySlider() { return qualitySlider; }
    public JComboBox<String> getScaleModeCombo() { return scaleModeCombo; }
    public JComboBox<String> getOutputFormatCombo() { return outputFormatCombo; }
    public JComboBox<String> getNamingRuleCombo() { return namingRuleCombo; }
    public JCheckBox getOverwriteCheckBox() { return overwriteCheckBox; }

    public void setQualityDisplay(int quality) {
        qualityValueLabel.setText(quality + "%");
    }
}
