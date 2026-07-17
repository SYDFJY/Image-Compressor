package com.nchu.imagecompress.view;

import com.nchu.imagecompress.model.AppConfig;
import com.nchu.imagecompress.model.Theme;
import com.nchu.imagecompress.util.ThemeUtil;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;

/**
 * 设置对话框（v2 — 实用功能替代空壳弹窗）。
 *
 * <p>提供输出目录、FFmpeg 路径、主题选择和窗口默认值。</p>
 *
 * @author NCHU-Student
 * @version 2.0.0
 * @since 2026-07-17
 */
public class SettingsDialog extends JDialog {

    private JTextField outputDirField;
    private JTextField ffmpegPathField;
    private JComboBox<String> themeCombo;
    private boolean confirmed = false;

    /**
     * 显示设置对话框并返回修改后的配置。
     *
     * @param owner  父窗口
     * @param config 当前配置（会被修改）
     * @return true 表示用户点击了确定
     */
    public static boolean show(Frame owner, AppConfig config) {
        SettingsDialog dialog = new SettingsDialog(owner, config);
        dialog.setVisible(true);
        return dialog.confirmed;
    }

    private SettingsDialog(Frame owner, AppConfig config) {
        super(owner, "设置", true);
        setResizable(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout(0, 16));
        mainPanel.setBackground(ThemeUtil.BG_CARD);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));

        // --- 标题 ---
        JLabel titleLabel = new JLabel("应用设置");
        titleLabel.setFont(ThemeUtil.FONT_TITLE.deriveFont(16f));
        titleLabel.setForeground(ThemeUtil.TEXT_PRIMARY);
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        // --- 表单 ---
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 0, 6, ThemeUtil.SPACE_LG);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;

        // 输出目录
        addFormLabel(formPanel, gbc, "默认输出目录", row);
        JPanel dirPanel = new JPanel(new BorderLayout(ThemeUtil.SPACE_SM, 0));
        dirPanel.setOpaque(false);
        outputDirField = new JTextField(config.getLastOutputPath());
        outputDirField.setFont(ThemeUtil.FONT_SMALL);
        dirPanel.add(outputDirField, BorderLayout.CENTER);
        JButton browseBtn = new JButton("浏览...");
        browseBtn.setFont(ThemeUtil.FONT_SMALL);
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                outputDirField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        dirPanel.add(browseBtn, BorderLayout.EAST);
        addFormControl(formPanel, gbc, dirPanel, row);
        row++;

        // FFmpeg 路径
        addFormLabel(formPanel, gbc, "FFmpeg 路径", row);
        ffmpegPathField = new JTextField(
                System.getProperty("ffmpeg.bin.path", ""));
        ffmpegPathField.setFont(ThemeUtil.FONT_SMALL);
        ffmpegPathField.setToolTipText("留空则自动检测系统 PATH 中的 FFmpeg");
        addFormControl(formPanel, gbc, ffmpegPathField, row);
        row++;

        // 主题选择
        addFormLabel(formPanel, gbc, "默认主题", row);
        themeCombo = new JComboBox<>();
        for (Theme t : Theme.values()) {
            themeCombo.addItem(t.getDisplayName());
        }
        // 选中当前主题
        Theme current = ThemeUtil.getCurrentTheme();
        for (int i = 0; i < Theme.values().length; i++) {
            if (Theme.values()[i] == current) {
                themeCombo.setSelectedIndex(i);
                break;
            }
        }
        themeCombo.setFont(ThemeUtil.FONT_SMALL);
        addFormControl(formPanel, gbc, themeCombo, row);
        row++;

        mainPanel.add(formPanel, BorderLayout.CENTER);

        // --- 按钮 ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setOpaque(false);

        JButton cancelBtn = new JButton("取消");
        cancelBtn.setFont(ThemeUtil.FONT_BODY);
        cancelBtn.addActionListener(e -> dispose());
        buttonPanel.add(cancelBtn);

        JButton okBtn = new JButton("确定");
        okBtn.setFont(ThemeUtil.FONT_TITLE);
        okBtn.setForeground(java.awt.Color.WHITE);
        okBtn.setBackground(ThemeUtil.PRIMARY);
        okBtn.setFocusPainted(false);
        okBtn.setBorder(BorderFactory.createEmptyBorder(6, 20, 6, 20));
        okBtn.addActionListener(e -> {
            // 保存到 config
            String dir = outputDirField.getText().trim();
            if (!dir.isEmpty()) {
                config.setLastOutputPath(dir);
                config.setLastVideoOutputPath(dir);
            }
            String ffmpeg = ffmpegPathField.getText().trim();
            if (!ffmpeg.isEmpty()) {
                System.setProperty("ffmpeg.bin.path", ffmpeg);
            }
            int themeIdx = themeCombo.getSelectedIndex();
            if (themeIdx >= 0 && themeIdx < Theme.values().length) {
                Theme selected = Theme.values()[themeIdx];
                if (selected != ThemeUtil.getCurrentTheme()) {
                    ThemeUtil.switchTheme(selected);
                }
            }
            confirmed = true;
            dispose();
        });
        buttonPanel.add(okBtn);

        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
        pack();
        setSize(460, getHeight());
        setLocationRelativeTo(owner);

        // ESC 关闭
        getRootPane().registerKeyboardAction(
                e -> dispose(),
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private static void addFormLabel(JPanel panel, GridBagConstraints gbc, String text, int row) {
        JLabel label = new JLabel(text + ":");
        label.setFont(ThemeUtil.FONT_BODY);
        label.setForeground(ThemeUtil.TEXT_PRIMARY);
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        gbc.gridy = row; gbc.gridx = 0;
        gbc.weightx = 0;
        panel.add(label, gbc);
    }

    private static void addFormControl(JPanel panel, GridBagConstraints gbc,
                                       java.awt.Component comp, int row) {
        gbc.gridy = row; gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(comp, gbc);
    }
}
