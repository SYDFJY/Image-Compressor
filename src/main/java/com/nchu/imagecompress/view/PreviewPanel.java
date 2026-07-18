package com.nchu.imagecompress.view;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.nchu.imagecompress.util.ThemeUtil;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import com.nchu.imagecompress.util.FileUtil;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;

/**
 * 预览面板（P1 升级版 — 三模式分段控件 + 缩放按钮 + 蓝色描边）。
 *
 * <p>布局结构（BorderLayout，三层不叠加）：
 * <pre>
 * 预览卡片
 * ├─ NORTH  — 标题 + 缩放按钮 ⊡/1:1 + Segmented Control [原图 | 效果图 | 对比]
 * ├─ CENTER — PreviewCanvas（缩放模式 + 蓝色描边 + 对比模式 + 棋盘格）
 * └─ SOUTH  — 半透明数据浮层
 * </pre>
 *
 * @author NCHU-Student
 * @version 4.0.0
 * @since 2026-07-08
 */
public class PreviewPanel extends JPanel {

    /** 当前预览模式：0=原图, 1=效果图, 2=对比 */
    private int currentPreviewMode = 0;

    // 分段控件（v2 — JToggleButton 替代 JLabel，支持键盘导航）
    private final JPanel segmentedControl;
    private JToggleButton originalSegBtn;
    private JToggleButton effectSegBtn;
    private JToggleButton compareSegBtn;

    // 画布（非 final — JDK 8 限制：构造方法内匿名类引用必须先赋值）
    private PreviewCanvas canvas;

    // 数据浮层
    private final JPanel overlayBar;
    private JLabel origSizeLabel;
    private JLabel compSizeLabel;
    private JLabel ratioLabel;
    private JLabel dimsLabel;

    // ==================== 双 Tab：预览 + 信息 ====================

    private JTabbedPane tabbedPane;
    private JPanel infoCardPanel;
    private CardLayout infoCardLayout;
    private JPanel infoEmptyPanel;
    private JPanel infoDetailPanel;

    // 信息 Tab 表单控件
    private JLabel infoFileNameLabel;
    private JLabel infoFileSizeLabel;
    private JLabel infoMakeModelLabel;
    private JLabel infoDateTimeLabel;
    private JLabel infoExposureLabel;
    private JLabel infoFNumberLabel;
    private JLabel infoISOLabel;
    private JLabel infoFocalLengthLabel;
    private JLabel infoFlashLabel;
    private JLabel infoDimensionsLabel;
    private JLabel infoGPSLabel;
    private JLabel infoSoftwareLabel;
    private JLabel infoCopyrightLabel;

    private static final String INFO_EMPTY = "INFO_EMPTY";
    private static final String INFO_DETAIL = "INFO_DETAIL";

    public PreviewPanel() {
        setLayout(new BorderLayout(0, 0));
        ThemeUtil.setDynamicBackground(this, () -> ThemeUtil.BG_CARD);
        setBorder(BorderFactory.createEmptyBorder(
                ThemeUtil.SPACE_LG, ThemeUtil.SPACE_LG,
                ThemeUtil.SPACE_LG, ThemeUtil.SPACE_LG));

        // === 顶部：标题 + 缩放按钮 + 分段控件 ===
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);
        topPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, ThemeUtil.SPACE_SM, 0));

        JLabel titleLabel = new JLabel("预 览");
        titleLabel.setFont(ThemeUtil.FONT_SMALL.deriveFont(Font.BOLD));
        ThemeUtil.setDynamicForeground(titleLabel, () -> ThemeUtil.TEXT_TERTIARY);
        topPanel.add(titleLabel, BorderLayout.WEST);

        // 缩放按钮：⊡ 适合窗口 / 1:1 实际像素
        JPanel zoomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 0));
        zoomPanel.setOpaque(false);

        JButton fitBtn = createZoomBtn("⊡", "适合窗口");
        fitBtn.addActionListener(e -> canvas.setZoomMode(PreviewCanvas.ZoomMode.FIT_INSIDE));
        zoomPanel.add(fitBtn);

        JButton actualBtn = createZoomBtn("1:1", "实际像素");
        actualBtn.addActionListener(e -> canvas.setZoomMode(PreviewCanvas.ZoomMode.ACTUAL_PIXELS));
        zoomPanel.add(actualBtn);

        topPanel.add(zoomPanel, BorderLayout.CENTER);

        segmentedControl = createSegmentedControl();
        topPanel.add(segmentedControl, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);

        // === 中部：双 Tab（预览 + 信息） ===
        canvas = new PreviewCanvas();

        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setFont(ThemeUtil.FONT_SMALL);
        ThemeUtil.setDynamicBackground(tabbedPane, () -> ThemeUtil.BG_CARD);
        ThemeUtil.setDynamicForeground(tabbedPane, () -> ThemeUtil.TEXT_SECONDARY);

        // Tab 1: 图片预览
        JPanel previewTabContent = new JPanel(new BorderLayout());
        previewTabContent.setOpaque(true);
        ThemeUtil.setDynamicBackground(previewTabContent, () -> ThemeUtil.BG_CARD);
        previewTabContent.add(canvas, BorderLayout.CENTER);
        tabbedPane.addTab("图片预览", new FlatSVGIcon("icons/camera.svg"), previewTabContent);

        // Tab 2: 图片信息
        infoCardLayout = new CardLayout();
        infoCardPanel = new JPanel(infoCardLayout);
        infoCardPanel.setOpaque(false);

        infoEmptyPanel = createInfoEmptyPanel();
        infoDetailPanel = createInfoDetailPanel();

        infoCardPanel.add(infoEmptyPanel, INFO_EMPTY);
        infoCardPanel.add(infoDetailPanel, INFO_DETAIL);
        infoCardLayout.show(infoCardPanel, INFO_EMPTY);

        tabbedPane.addTab("图片信息", new FlatSVGIcon("icons/clipboard.svg"), infoCardPanel);

        add(tabbedPane, BorderLayout.CENTER);

        // === 底部：半透明数据浮层 ===
        overlayBar = createOverlayBar();
        overlayBar.setVisible(false);
        add(overlayBar, BorderLayout.SOUTH);

        // 主题切换时刷新显式背景色
        ThemeUtil.addThemeChangeListener(() -> {
            setBackground(ThemeUtil.BG_CARD);
            tabbedPane.setBackground(ThemeUtil.BG_CARD);
            previewTabContent.setBackground(ThemeUtil.BG_CARD);
            overlayBar.repaint();
            repaint();
        });
    }

    // ==================== 缩放按钮 ====================

    private JButton createZoomBtn(String text, String tooltip) {
        JButton btn = new JButton(text);
        btn.setFont(ThemeUtil.FONT_SMALL);
        btn.setToolTipText(tooltip);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        btn.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        return btn;
    }

    // ==================== 分段控件 [原图 | 效果图 | 对比] v2 ====================

    private JPanel createSegmentedControl() {
        JPanel container = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        container.setOpaque(false);

        JPanel segment = new JPanel(new GridLayout(1, 3, 0, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(ThemeUtil.BG_HOVER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(),
                        ThemeUtil.ARC_TAG, ThemeUtil.ARC_TAG);
                g2.dispose();
            }
        };
        segment.setOpaque(true);  // 绘制了完整不透明背景，告知 Swing 可优化
        segment.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        ButtonGroup group = new ButtonGroup();

        originalSegBtn = createSegToggle("原图", 0);
        group.add(originalSegBtn);
        segment.add(originalSegBtn);

        effectSegBtn = createSegToggle("效果图", 1);
        group.add(effectSegBtn);
        segment.add(effectSegBtn);

        compareSegBtn = createSegToggle("对比", 2);
        group.add(compareSegBtn);
        segment.add(compareSegBtn);

        originalSegBtn.setSelected(true);

        container.add(segment);
        return container;
    }

    private JToggleButton createSegToggle(String text, int mode) {
        JToggleButton btn = new JToggleButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                // 背景：选中时 BG_CARD（凸起卡片），否则透明（由父面板 BG_HOVER 提供）
                if (isSelected()) {
                    g2.setColor(ThemeUtil.BG_CARD);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(),
                            ThemeUtil.ARC_TAG - 1, ThemeUtil.ARC_TAG - 1);
                }

                // 文字：始终手动绘制，绕过 FlatLaf ButtonUI 在 opaque=false + contentAreaFilled=false 时不渲染文字的问题
                Color fg = isSelected() ? ThemeUtil.PRIMARY : ThemeUtil.TEXT_SECONDARY;
                g2.setColor(fg);
                g2.setFont(getFont());
                java.awt.FontMetrics fm = g2.getFontMetrics();
                String t = getText();
                if (t != null && fm != null) {
                    int tw = fm.stringWidth(t);
                    int th = fm.getAscent();
                    g2.drawString(t, (getWidth() - tw) / 2, (getHeight() + th) / 2 - 1);
                }

                g2.dispose();
            }
        };
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFont(ThemeUtil.FONT_SMALL);
        btn.setFocusPainted(false);
        btn.setPreferredSize(new Dimension(54, 28));
        btn.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        btn.addActionListener(e -> {
            if (btn.isSelected()) {
                setPreviewMode(mode);
            }
        });
        return btn;
    }

    private void setPreviewMode(int mode) {
        currentPreviewMode = mode;
        canvas.setCurrentMode(mode);
        // 同步 ToggleButton 状态
        if (mode == 0) originalSegBtn.setSelected(true);
        else if (mode == 1) effectSegBtn.setSelected(true);
        else if (mode == 2) compareSegBtn.setSelected(true);
    }

    // ==================== 半透明数据浮层 ====================

    private JPanel createOverlayBar() {
        JPanel bar = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                Color bg = ThemeUtil.BG_CARD;
                g2.setColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 220));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(),
                        ThemeUtil.ARC_BUTTON, ThemeUtil.ARC_BUTTON);
                g2.dispose();
            }
        };
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(
                ThemeUtil.SPACE_MD, ThemeUtil.SPACE_LG,
                ThemeUtil.SPACE_MD, ThemeUtil.SPACE_LG));
        bar.setPreferredSize(new Dimension(0, 56));

        JPanel dataRow = new JPanel(new java.awt.GridLayout(1, 4, 16, 0));
        dataRow.setOpaque(false);

        dataRow.add(createDataItem("原始大小", "—"));
        origSizeLabel = (JLabel) ((JPanel) dataRow.getComponent(0)).getComponent(1);

        dataRow.add(createDataItem("压缩后", "—"));
        compSizeLabel = (JLabel) ((JPanel) dataRow.getComponent(1)).getComponent(1);

        dataRow.add(createDataItem("压缩率", "—"));
        ratioLabel = (JLabel) ((JPanel) dataRow.getComponent(2)).getComponent(1);

        dataRow.add(createDataItem("尺寸", "—"));
        dimsLabel = (JLabel) ((JPanel) dataRow.getComponent(3)).getComponent(1);

        bar.add(dataRow, BorderLayout.CENTER);
        return bar;
    }

    private JPanel createDataItem(String label, String value) {
        JPanel item = new JPanel(new BorderLayout(0, 2));
        item.setOpaque(false);

        JLabel lbl = new JLabel(label, SwingConstants.CENTER);
        lbl.setFont(ThemeUtil.FONT_SMALL);
        ThemeUtil.setDynamicForeground(lbl, () -> ThemeUtil.TEXT_TERTIARY);
        item.add(lbl, BorderLayout.NORTH);

        JLabel val = new JLabel(value, SwingConstants.CENTER);
        val.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        ThemeUtil.setDynamicForeground(val, () -> ThemeUtil.TEXT_PRIMARY);
        item.add(val, BorderLayout.SOUTH);

        return item;
    }

    // ==================== 图片信息 Tab ====================

    /**
     * 信息 Tab 空状态面板。
     */
    private JPanel createInfoEmptyPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(30, 20, 30, 20));

        JPanel centerPanel = new JPanel(new BorderLayout(0, ThemeUtil.SPACE_LG));
        centerPanel.setOpaque(false);

        JLabel iconLabel = new JLabel(new FlatSVGIcon("icons/clipboard.svg", 48, 48));
        centerPanel.add(iconLabel, BorderLayout.NORTH);

        JLabel guideLabel = new JLabel("选择图片文件查看拍摄信息", SwingConstants.CENTER);
        guideLabel.setFont(ThemeUtil.FONT_BODY);
        ThemeUtil.setDynamicForeground(guideLabel, () -> ThemeUtil.TEXT_SECONDARY);
        centerPanel.add(guideLabel, BorderLayout.CENTER);

        panel.add(centerPanel, BorderLayout.CENTER);
        return panel;
    }

    /**
     * 信息 Tab 详情面板（EXIF 元数据表单）。
     */
    private JPanel createInfoDetailPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, ThemeUtil.SPACE_MD));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(
                ThemeUtil.SPACE_MD, ThemeUtil.SPACE_LG,
                ThemeUtil.SPACE_MD, ThemeUtil.SPACE_LG));

        // --- 头部：相机图标 + 文件名 ---
        JPanel headerPanel = new JPanel(new BorderLayout(ThemeUtil.SPACE_SM, 0));
        headerPanel.setOpaque(false);

        JLabel cameraIcon = new JLabel(new FlatSVGIcon("icons/camera.svg", 20, 20));
        headerPanel.add(cameraIcon, BorderLayout.WEST);

        infoFileNameLabel = new JLabel();
        infoFileNameLabel.setFont(ThemeUtil.FONT_TITLE);
        ThemeUtil.setDynamicForeground(infoFileNameLabel, () -> ThemeUtil.TEXT_PRIMARY);
        headerPanel.add(infoFileNameLabel, BorderLayout.CENTER);

        infoFileSizeLabel = new JLabel();
        infoFileSizeLabel.setFont(ThemeUtil.FONT_SMALL);
        ThemeUtil.setDynamicForeground(infoFileSizeLabel, () -> ThemeUtil.TEXT_SECONDARY);
        headerPanel.add(infoFileSizeLabel, BorderLayout.EAST);

        panel.add(headerPanel, BorderLayout.NORTH);

        // --- 元数据表单 ---
        JPanel metaPanel = new JPanel(new GridBagLayout());
        metaPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(ThemeUtil.SPACE_SM, 0, ThemeUtil.SPACE_SM, ThemeUtil.SPACE_LG);

        int row = 0;
        infoMakeModelLabel = createMetaRow(metaPanel, gbc, "相机", row++);
        infoDateTimeLabel = createMetaRow(metaPanel, gbc, "拍摄时间", row++);
        infoExposureLabel = createMetaRow(metaPanel, gbc, "曝光时间", row++);
        infoFNumberLabel = createMetaRow(metaPanel, gbc, "光圈值", row++);
        infoISOLabel = createMetaRow(metaPanel, gbc, "ISO", row++);
        infoFocalLengthLabel = createMetaRow(metaPanel, gbc, "焦距", row++);
        infoFlashLabel = createMetaRow(metaPanel, gbc, "闪光灯", row++);
        infoDimensionsLabel = createMetaRow(metaPanel, gbc, "图像尺寸", row++);
        infoGPSLabel = createMetaRow(metaPanel, gbc, "GPS 位置", row++);
        infoSoftwareLabel = createMetaRow(metaPanel, gbc, "处理软件", row++);
        infoCopyrightLabel = createMetaRow(metaPanel, gbc, "版权信息", row++);

        // 弹性填充
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        metaPanel.add(new JLabel(), gbc);

        panel.add(metaPanel, BorderLayout.CENTER);

        return panel;
    }

    /**
     * 创建元数据行（标签 + 值）。
     */
    private JLabel createMetaRow(JPanel panel, GridBagConstraints gbc, String label, int row) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;

        JLabel labelComp = new JLabel(label + ":");
        labelComp.setFont(ThemeUtil.FONT_SMALL);
        ThemeUtil.setDynamicForeground(labelComp, () -> ThemeUtil.TEXT_SECONDARY);
        panel.add(labelComp, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel valueComp = new JLabel("—");
        valueComp.setFont(ThemeUtil.FONT_BODY);
        ThemeUtil.setDynamicForeground(valueComp, () -> ThemeUtil.TEXT_PRIMARY);
        panel.add(valueComp, gbc);

        return valueComp;
    }

    /**
     * 显示图片 EXIF 信息（在"图片信息"Tab 中）。
     * 由 Controller 在选中文件时调用。
     */
    public void showImageInfo(com.nchu.imagecompress.model.ImageFileInfo info) {
        if (info == null) {
            infoCardLayout.show(infoCardPanel, INFO_EMPTY);
            return;
        }

        // 文件名和大小（始终可用）
        infoFileNameLabel.setText(info.getFileName());
        infoFileSizeLabel.setText(info.getFormattedSize());

        // 图像尺寸（优先用 ImageFileInfo 已有的宽高）
        if (info.getWidth() > 0 && info.getHeight() > 0) {
            infoDimensionsLabel.setText(info.getWidth() + " × " + info.getHeight() + " px");
        } else {
            infoDimensionsLabel.setText("—");
        }

        // EXIF 数据
        com.nchu.imagecompress.util.ImageExifUtil.ExifData exif = info.getExifData();
        if (exif != null && exif.hasAnyData()) {
            // 相机品牌 + 型号
            String makeModel = joinNonEmpty(" ", exif.make, exif.model);
            infoMakeModelLabel.setText(makeModel.isEmpty() ? "—" : makeModel);

            infoDateTimeLabel.setText(nullToDash(exif.dateTaken));
            infoExposureLabel.setText(nullToDash(formatExposureText(exif.exposureTime)));
            infoFNumberLabel.setText(exif.fNumber != null ? "f/" + exif.fNumber : "—");
            infoISOLabel.setText(nullToDash(exif.iso));
            infoFocalLengthLabel.setText(nullToDash(exif.focalLength));
            infoFlashLabel.setText(nullToDash(exif.flash));

            // EXIF 中的尺寸（如果 ImageFileInfo 没有）
            if (info.getWidth() <= 0 && exif.imageWidth > 0 && exif.imageHeight > 0) {
                infoDimensionsLabel.setText(exif.imageWidth + " × " + exif.imageHeight + " px");
            }

            // GPS
            String gps = joinNonEmpty(", ", exif.gpsLatitude, exif.gpsLongitude);
            infoGPSLabel.setText(gps.isEmpty() ? "—" : gps);
            if (!gps.isEmpty() && exif.gpsAltitude != null) {
                infoGPSLabel.setText(gps + "  " + exif.gpsAltitude);
            }

            infoSoftwareLabel.setText(nullToDash(exif.software));
            infoCopyrightLabel.setText(nullToDash(exif.copyright));
        } else {
            // 无 EXIF — 全部显示 "—"
            infoMakeModelLabel.setText("—");
            infoDateTimeLabel.setText("—");
            infoExposureLabel.setText("—");
            infoFNumberLabel.setText("—");
            infoISOLabel.setText("—");
            infoFocalLengthLabel.setText("—");
            infoFlashLabel.setText("—");
            infoGPSLabel.setText("—");
            infoSoftwareLabel.setText("—");
            infoCopyrightLabel.setText("—");
        }

        infoCardLayout.show(infoCardPanel, INFO_DETAIL);
    }

    /**
     * 格式化曝光时间文本（如 "1/250 sec"）。
     */
    private static String formatExposureText(String raw) {
        if (raw == null) return null;
        // metadata-extractor 可能返回 "1/250 sec" 格式
        return raw;
    }

    /** null → "—" */
    private static String nullToDash(String s) {
        return (s == null || s.isEmpty()) ? "—" : s;
    }

    /** 用分隔符拼接非空字符串 */
    private static String joinNonEmpty(String delimiter, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String s : parts) {
            if (s != null && !s.isEmpty()) {
                if (sb.length() > 0) sb.append(delimiter);
                sb.append(s);
            }
        }
        return sb.toString();
    }

    // ==================== 预览更新 ====================

    public void showOriginal(ImageIcon icon) {
        if (icon != null) {
            canvas.setImage(toBufferedImage(icon));
        } else {
            canvas.clearImage();
        }
        setPreviewMode(0);
    }

    public void showEffect(ImageIcon icon) {
        if (icon != null) {
            canvas.setEffectImage(toBufferedImage(icon));
        } else {
            canvas.clearImage();
        }
        setPreviewMode(1);
    }

    public void updateComparison(long originalSize, long compressedSize, double ratio) {
        overlayBar.setVisible(true);
        origSizeLabel.setText(FileUtil.formatFileSize(originalSize));
        compSizeLabel.setText(FileUtil.formatFileSize(compressedSize));
        if (ratio >= 0) {
            ratioLabel.setText(String.format("−%.1f%%", ratio));
            ThemeUtil.setDynamicForeground(ratioLabel, () -> ratio > 30 ? ThemeUtil.SUCCESS
                    : ratio > 10 ? ThemeUtil.PRIMARY : ThemeUtil.TEXT_TERTIARY);
        } else {
            ratioLabel.setText("—");
            ThemeUtil.setDynamicForeground(ratioLabel, () -> ThemeUtil.TEXT_TERTIARY);
        }
    }

    public void clearPreview() {
        canvas.clearImage();
        overlayBar.setVisible(false);
        origSizeLabel.setText("—");
        compSizeLabel.setText("—");
        ratioLabel.setText("—");
        dimsLabel.setText("—");
        currentPreviewMode = 0;
        originalSegBtn.setSelected(true);
        infoCardLayout.show(infoCardPanel, INFO_EMPTY);
    }

    // ==================== 工具方法 ====================

    private static BufferedImage toBufferedImage(ImageIcon icon) {
        if (icon == null || icon.getIconWidth() <= 0) return null;
        BufferedImage bi = new BufferedImage(
                icon.getIconWidth(), icon.getIconHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = bi.createGraphics();
        icon.paintIcon(null, g2, 0, 0);
        g2.dispose();
        return bi;
    }

}
