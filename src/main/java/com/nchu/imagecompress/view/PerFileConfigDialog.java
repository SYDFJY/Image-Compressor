package com.nchu.imagecompress.view;

import com.nchu.imagecompress.model.ImageFileInfo;
import com.nchu.imagecompress.model.OutputFormat;
import com.nchu.imagecompress.model.PerFileCompressConfig;
import com.nchu.imagecompress.util.ThemeUtil;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * 逐文件压缩参数设置对话框（v2.5）。
 *
 * <p>允许用户为单个文件设置独立的压缩质量 / 输出格式，
 * 勾选"使用全局设置"即恢复默认（字段设为 null）。</p>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-22
 */
public class PerFileConfigDialog extends JDialog {

    private boolean confirmed = false;

    private JCheckBox useGlobalQuality;
    private JSlider qualitySlider;
    private JLabel qualityLabel;

    private JCheckBox useGlobalFormat;
    private JComboBox<String> formatCombo;

    private final ImageFileInfo fileInfo;
    private final PerFileCompressConfig current;

    /**
     * @param owner    父窗口
     * @param fileInfo 目标文件信息
     * @param current  当前覆盖配置（null = 全部使用全局）
     */
    public PerFileConfigDialog(Frame owner, ImageFileInfo fileInfo,
                               PerFileCompressConfig current) {
        super(owner, "单独设置: " + fileInfo.getFileName(), true);
        this.fileInfo = fileInfo;
        this.current = (current != null) ? current : new PerFileCompressConfig();

        setResizable(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout(0, 12));
        mainPanel.setBackground(ThemeUtil.BG_CARD);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        mainPanel.add(createFormPanel(), BorderLayout.CENTER);
        mainPanel.add(createButtonPanel(), BorderLayout.SOUTH);

        add(mainPanel);
        pack();
        setSize(400, getHeight());
        setLocationRelativeTo(owner);

        getRootPane().registerKeyboardAction(
                e -> dispose(),
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);

        loadCurrent();
    }

    private JPanel createFormPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 0, 6, 12);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;

        // --- 全局使用说明 ---
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        JLabel hintLabel = new JLabel("勾选「使用全局设置」则跟随主面板参数");
        hintLabel.setFont(ThemeUtil.FONT_TINY);
        hintLabel.setForeground(ThemeUtil.TEXT_TERTIARY);
        panel.add(hintLabel, gbc);
        gbc.gridwidth = 1;
        row++;

        // --- 质量覆盖 ---
        JLabel qLabel = new JLabel("压缩质量:");
        qLabel.setFont(ThemeUtil.FONT_SMALL);
        qLabel.setForeground(ThemeUtil.TEXT_PRIMARY);
        gbc.gridx = 0; gbc.gridy = row;
        panel.add(qLabel, gbc);

        useGlobalQuality = new JCheckBox("使用全局设置");
        useGlobalQuality.setFont(ThemeUtil.FONT_SMALL);
        useGlobalQuality.setOpaque(false);
        useGlobalQuality.addActionListener(e -> {
            boolean custom = !useGlobalQuality.isSelected();
            qualitySlider.setEnabled(custom);
            qualityLabel.setEnabled(custom);
        });
        gbc.gridx = 1; gbc.gridy = row;
        panel.add(useGlobalQuality, gbc);
        row++;

        JPanel qualityPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        qualityPanel.setOpaque(false);
        qualitySlider = new JSlider(1, 100, 80);
        qualitySlider.setPreferredSize(new java.awt.Dimension(180, 24));
        qualitySlider.setOpaque(false);
        qualitySlider.addChangeListener(e -> qualityLabel.setText(qualitySlider.getValue() + "%"));
        qualityPanel.add(qualitySlider);
        qualityLabel = new JLabel("80%");
        qualityLabel.setFont(ThemeUtil.FONT_SMALL);
        qualityLabel.setForeground(ThemeUtil.TEXT_PRIMARY);
        qualityPanel.add(qualityLabel);
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        panel.add(qualityPanel, gbc);
        gbc.gridwidth = 1;
        row++;

        // --- 格式覆盖 ---
        JLabel fLabel = new JLabel("输出格式:");
        fLabel.setFont(ThemeUtil.FONT_SMALL);
        fLabel.setForeground(ThemeUtil.TEXT_PRIMARY);
        gbc.gridx = 0; gbc.gridy = row;
        panel.add(fLabel, gbc);

        useGlobalFormat = new JCheckBox("使用全局设置");
        useGlobalFormat.setFont(ThemeUtil.FONT_SMALL);
        useGlobalFormat.setOpaque(false);
        useGlobalFormat.addActionListener(e -> formatCombo.setEnabled(!useGlobalFormat.isSelected()));
        gbc.gridx = 1; gbc.gridy = row;
        panel.add(useGlobalFormat, gbc);
        row++;

        formatCombo = new JComboBox<>(new String[]{
                "保持原格式", "JPEG", "PNG", "BMP", "WebP"
        });
        formatCombo.setFont(ThemeUtil.FONT_SMALL);
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        panel.add(formatCombo, gbc);
        gbc.gridwidth = 1;
        row++;

        return panel;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        // 清除按钮
        JButton clearBtn = new JButton("恢复全局参数");
        clearBtn.setFont(ThemeUtil.FONT_SMALL);
        clearBtn.setForeground(ThemeUtil.ERROR);
        clearBtn.setOpaque(false);
        clearBtn.setFocusPainted(false);
        clearBtn.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        clearBtn.addActionListener(e -> {
            useGlobalQuality.setSelected(true);
            useGlobalFormat.setSelected(true);
            qualitySlider.setValue(80);
            qualitySlider.setEnabled(false);
            qualityLabel.setEnabled(false);
            formatCombo.setEnabled(false);
            formatCombo.setSelectedIndex(0);
        });
        panel.add(clearBtn);

        JButton cancelBtn = new JButton("取消");
        cancelBtn.setFont(ThemeUtil.FONT_BODY);
        cancelBtn.addActionListener(e -> dispose());
        panel.add(cancelBtn);

        JButton okBtn = new JButton("确定");
        okBtn.setFont(ThemeUtil.FONT_TITLE);
        okBtn.setForeground(java.awt.Color.WHITE);
        okBtn.setBackground(ThemeUtil.PRIMARY);
        okBtn.setFocusPainted(false);
        okBtn.setBorder(BorderFactory.createEmptyBorder(6, 20, 6, 20));
        okBtn.addActionListener(e -> {
            confirmed = true;
            dispose();
        });
        panel.add(okBtn);

        return panel;
    }

    private void loadCurrent() {
        if (current.getQuality() != null) {
            useGlobalQuality.setSelected(false);
            qualitySlider.setValue(current.getQuality());
            qualityLabel.setText(current.getQuality() + "%");
        } else {
            useGlobalQuality.setSelected(true);
            qualitySlider.setEnabled(false);
            qualityLabel.setEnabled(false);
        }

        if (current.getOutputFormat() != null) {
            useGlobalFormat.setSelected(false);
            switch (current.getOutputFormat()) {
                case ORIGINAL: formatCombo.setSelectedIndex(0); break;
                case JPEG:     formatCombo.setSelectedIndex(1); break;
                case PNG:      formatCombo.setSelectedIndex(2); break;
                case BMP:      formatCombo.setSelectedIndex(3); break;
                case WEBP:     formatCombo.setSelectedIndex(4); break;
            }
        } else {
            useGlobalFormat.setSelected(true);
            formatCombo.setEnabled(false);
        }
    }

    // ==================== 对外接口 ====================

    public boolean isConfirmed() { return confirmed; }

    /**
     * 获取用户设置的结果。
     * 如果没有任何自定义项，返回空的 PerFileCompressConfig（hasAnyOverride() = false）。
     */
    public PerFileCompressConfig getResult() {
        PerFileCompressConfig cfg = new PerFileCompressConfig();
        if (!useGlobalQuality.isSelected()) {
            cfg.setQuality(qualitySlider.getValue());
        }
        if (!useGlobalFormat.isSelected()) {
            switch (formatCombo.getSelectedIndex()) {
                case 0: cfg.setOutputFormat(OutputFormat.ORIGINAL); break;
                case 1: cfg.setOutputFormat(OutputFormat.JPEG); break;
                case 2: cfg.setOutputFormat(OutputFormat.PNG); break;
                case 3: cfg.setOutputFormat(OutputFormat.BMP); break;
                case 4: cfg.setOutputFormat(OutputFormat.WEBP); break;
            }
        }
        return cfg;
    }
}
