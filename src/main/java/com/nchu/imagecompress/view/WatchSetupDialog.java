package com.nchu.imagecompress.view;

import com.nchu.imagecompress.model.FolderWatchConfig;
import com.nchu.imagecompress.util.ThemeUtil;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.function.Consumer;

/**
 * 文件夹监控设置对话框 — 配置源/目标文件夹、压缩参数并启动/停止监控。
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-22
 */
public class WatchSetupDialog extends JDialog {

    private JTextField watchFolderField;
    private JTextField outputFolderField;
    private JSlider qualitySlider;
    private JLabel qualityLabel;
    private JCheckBox compressImagesCheck;
    private JCheckBox compressVideosCheck;
    private JCheckBox deleteOriginalCheck;
    private JButton toggleButton;
    private JLabel statusLabel;
    private JPanel statusPanel;

    private boolean monitoring = false;
    private boolean confirmed = false;

    private final FolderWatchConfig config;
    private final Consumer<FolderWatchConfig> onSave;
    private final Consumer<FolderWatchConfig> onStart;
    private final Runnable onStop;

    public WatchSetupDialog(Frame owner, FolderWatchConfig config,
                            Consumer<FolderWatchConfig> onSave,
                            Consumer<FolderWatchConfig> onStart,
                            Runnable onStop) {
        super(owner, "文件夹监控设置", true);
        this.config = config;
        this.onSave = onSave;
        this.onStart = onStart;
        this.onStop = onStop;

        setResizable(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout(0, 12));
        mainPanel.setBackground(ThemeUtil.BG_CARD);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        mainPanel.add(createFormPanel(), BorderLayout.CENTER);
        mainPanel.add(createButtonPanel(), BorderLayout.SOUTH);

        add(mainPanel);
        pack();
        setSize(500, getHeight());
        setLocationRelativeTo(owner);

        getRootPane().registerKeyboardAction(
                e -> dispose(),
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);

        // 从 config 恢复
        loadFromConfig();
    }

    private JPanel createFormPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 0, 6, 12);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;

        // v2.5: 首次使用引导说明（仅在未设置过文件夹时显示）
        if (config.getWatchFolderPath() == null || config.getWatchFolderPath().isEmpty()) {
            gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
            gbc.insets = new Insets(4, 0, 10, 0);
            JLabel guideLabel = new JLabel(
                    "<html><div style='color:#888; font-size:11px;'>"
                    + "💡 选择一个文件夹，应用将自动压缩放入其中的新图片和视频。<br>"
                    + "设置完成后点击「启动监控」即可开始。</div></html>");
            guideLabel.setFont(ThemeUtil.FONT_SMALL);
            panel.add(guideLabel, gbc);
            gbc.gridwidth = 1;  // 恢复默认
            gbc.insets = new Insets(6, 0, 6, 12);
            row++;
        }

        // --- 源文件夹 ---
        addLabel(panel, gbc, "监控文件夹:", row);
        watchFolderField = new JTextField(30);
        watchFolderField.setFont(ThemeUtil.FONT_SMALL);
        watchFolderField.setBorder(ThemeUtil.createDynamicLineBorder());
        watchFolderField.setEditable(false);
        addFolderRow(panel, gbc, watchFolderField, row);
        row++;

        // --- 输出文件夹 ---
        addLabel(panel, gbc, "输出文件夹:", row);
        outputFolderField = new JTextField(30);
        outputFolderField.setFont(ThemeUtil.FONT_SMALL);
        outputFolderField.setBorder(ThemeUtil.createDynamicLineBorder());
        outputFolderField.setEditable(false);
        addFolderRow(panel, gbc, outputFolderField, row);
        row++;

        // --- 图片质量 ---
        addLabel(panel, gbc, "图片质量:", row);
        JPanel qualityPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        qualityPanel.setOpaque(false);
        qualitySlider = new JSlider(1, 100, 80);
        qualitySlider.setPreferredSize(new Dimension(200, 24));
        qualitySlider.setOpaque(false);
        qualitySlider.addChangeListener(e -> qualityLabel.setText(qualitySlider.getValue() + "%"));
        qualityPanel.add(qualitySlider);
        qualityLabel = new JLabel("80%");
        qualityLabel.setFont(ThemeUtil.FONT_SMALL);
        qualityLabel.setForeground(ThemeUtil.TEXT_PRIMARY);
        qualityPanel.add(qualityLabel);
        gbc.gridy = row; gbc.gridx = 1;
        panel.add(qualityPanel, gbc);
        row++;

        // --- 选项 ---
        addLabel(panel, gbc, "选项:", row);
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        optionsPanel.setOpaque(false);
        compressImagesCheck = new JCheckBox("压缩图片", true);
        compressImagesCheck.setFont(ThemeUtil.FONT_SMALL);
        compressImagesCheck.setOpaque(false);
        optionsPanel.add(compressImagesCheck);
        compressVideosCheck = new JCheckBox("压缩视频", true);
        compressVideosCheck.setFont(ThemeUtil.FONT_SMALL);
        compressVideosCheck.setOpaque(false);
        optionsPanel.add(compressVideosCheck);
        deleteOriginalCheck = new JCheckBox("压缩后删除原文件");
        deleteOriginalCheck.setFont(ThemeUtil.FONT_SMALL);
        deleteOriginalCheck.setForeground(ThemeUtil.ERROR);
        deleteOriginalCheck.setOpaque(false);
        deleteOriginalCheck.setToolTipText("危险操作：原文件删除后无法恢复");
        optionsPanel.add(deleteOriginalCheck);
        gbc.gridy = row; gbc.gridx = 1;
        panel.add(optionsPanel, gbc);
        row++;

        // --- 状态 ---
        addLabel(panel, gbc, "状态:", row);
        statusPanel = new JPanel(new CardLayout());
        statusPanel.setOpaque(false);
        statusLabel = new JLabel("未启动");
        statusLabel.setFont(ThemeUtil.FONT_SMALL);
        statusLabel.setForeground(ThemeUtil.TEXT_SECONDARY);
        statusPanel.add(statusLabel, "stopped");
        JLabel runningLabel = new JLabel("● 监控中");
        runningLabel.setFont(ThemeUtil.FONT_SMALL);
        runningLabel.setForeground(ThemeUtil.SUCCESS);
        statusPanel.add(runningLabel, "running");
        gbc.gridy = row; gbc.gridx = 1;
        panel.add(statusPanel, gbc);

        return panel;
    }

    private void addFolderRow(JPanel panel, GridBagConstraints gbc,
                               JTextField field, int row) {
        JPanel rowPanel = new JPanel(new BorderLayout(4, 0));
        rowPanel.setOpaque(false);
        rowPanel.add(field, BorderLayout.CENTER);
        JButton browseBtn = new JButton("浏览...");
        browseBtn.setFont(ThemeUtil.FONT_SMALL);
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                field.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        rowPanel.add(browseBtn, BorderLayout.EAST);
        gbc.gridy = row; gbc.gridx = 1;
        panel.add(rowPanel, gbc);
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        JButton saveBtn = new JButton("保存设置");
        saveBtn.setFont(ThemeUtil.FONT_BODY);
        saveBtn.addActionListener(e -> saveToConfig());
        panel.add(saveBtn);

        toggleButton = new JButton("启动监控");
        toggleButton.setFont(ThemeUtil.FONT_TITLE);
        toggleButton.setForeground(java.awt.Color.WHITE);
        toggleButton.setBackground(ThemeUtil.PRIMARY);
        toggleButton.setFocusPainted(false);
        toggleButton.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
        toggleButton.addActionListener(e -> toggleMonitoring());
        panel.add(toggleButton);

        JButton closeBtn = new JButton("关闭");
        closeBtn.setFont(ThemeUtil.FONT_BODY);
        closeBtn.addActionListener(e -> dispose());
        panel.add(closeBtn);

        return panel;
    }

    private void loadFromConfig() {
        if (config.getWatchFolderPath() != null) {
            watchFolderField.setText(config.getWatchFolderPath());
        }
        if (config.getOutputFolderPath() != null) {
            outputFolderField.setText(config.getOutputFolderPath());
        }
        qualitySlider.setValue(config.getImageQuality());
        qualityLabel.setText(config.getImageQuality() + "%");
        compressImagesCheck.setSelected(config.isCompressImages());
        compressVideosCheck.setSelected(config.isCompressVideos());
        deleteOriginalCheck.setSelected(config.isDeleteOriginal());
    }

    private void saveToConfig() {
        config.setWatchFolderPath(watchFolderField.getText().trim());
        config.setOutputFolderPath(outputFolderField.getText().trim());
        config.setImageQuality(qualitySlider.getValue());
        config.setCompressImages(compressImagesCheck.isSelected());
        config.setCompressVideos(compressVideosCheck.isSelected());
        config.setDeleteOriginal(deleteOriginalCheck.isSelected());

        if (onSave != null) onSave.accept(config);
        confirmed = true;
        ToastNotification.success("监控设置已保存");
    }

    private void toggleMonitoring() {
        String watchPath = watchFolderField.getText().trim();
        String outPath = outputFolderField.getText().trim();

        if (!monitoring) {
            // 启动
            if (watchPath.isEmpty() || outPath.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "请先设置监控文件夹和输出文件夹",
                        "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            File watchDir = new File(watchPath);
            if (!watchDir.exists() || !watchDir.isDirectory()) {
                JOptionPane.showMessageDialog(this,
                        "监控文件夹不存在: " + watchPath,
                        "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            File outDir = new File(outPath);
            if (!outDir.exists()) outDir.mkdirs();

            // 保存并启动
            saveToConfig();
            config.setEnabled(true);

            try {
                if (onStart != null) onStart.accept(config);
                monitoring = true;
                toggleButton.setText("停止监控");
                toggleButton.setBackground(ThemeUtil.ERROR);
                ((CardLayout) statusPanel.getLayout()).show(statusPanel, "running");
                ToastNotification.success("文件夹监控已启动");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "启动监控失败: " + ex.getMessage(),
                        "错误", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            // 停止
            if (onStop != null) onStop.run();
            monitoring = false;
            config.setEnabled(false);
            toggleButton.setText("启动监控");
            toggleButton.setBackground(ThemeUtil.PRIMARY);
            ((CardLayout) statusPanel.getLayout()).show(statusPanel, "stopped");
            ToastNotification.success("文件夹监控已停止");
        }
    }

    /** 外部设置监控状态（MainController 恢复时同步） */
    public void setMonitoring(boolean active) {
        this.monitoring = active;
        if (active) {
            toggleButton.setText("停止监控");
            toggleButton.setBackground(ThemeUtil.ERROR);
            ((CardLayout) statusPanel.getLayout()).show(statusPanel, "running");
        }
    }

    private static void addLabel(JPanel panel, GridBagConstraints gbc, String text, int row) {
        JLabel label = new JLabel(text);
        label.setFont(ThemeUtil.FONT_SMALL);
        label.setForeground(ThemeUtil.TEXT_PRIMARY);
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        panel.add(label, gbc);
    }
}
