package com.nchu.imagecompress.view;

import com.nchu.imagecompress.util.ThemeUtil;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.FlowLayout;
import java.awt.GridLayout;
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

    // 分段控件
    private final JPanel segmentedControl;
    private JLabel originalSegBtn;
    private JLabel effectSegBtn;
    private JLabel compareSegBtn;

    // 画布（非 final — JDK 8 限制：构造方法内匿名类引用必须先赋值）
    private PreviewCanvas canvas;

    // 数据浮层
    private final JPanel overlayBar;
    private JLabel origSizeLabel;
    private JLabel compSizeLabel;
    private JLabel ratioLabel;
    private JLabel dimsLabel;

    public PreviewPanel() {
        setLayout(new BorderLayout(0, 0));
        setBackground(ThemeUtil.BG_CARD);
        setBorder(BorderFactory.createEmptyBorder(
                ThemeUtil.SPACE_LG, ThemeUtil.SPACE_LG,
                ThemeUtil.SPACE_LG, ThemeUtil.SPACE_LG));

        // === 顶部：标题 + 缩放按钮 + 分段控件 ===
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);
        topPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, ThemeUtil.SPACE_SM, 0));

        JLabel titleLabel = new JLabel("预览");
        titleLabel.setFont(ThemeUtil.FONT_TITLE);
        titleLabel.setForeground(ThemeUtil.TEXT_PRIMARY);
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

        // === 中部：自适应预览画布 ===
        canvas = new PreviewCanvas();
        add(canvas, BorderLayout.CENTER);

        // === 底部：半透明数据浮层 ===
        overlayBar = createOverlayBar();
        overlayBar.setVisible(false);
        add(overlayBar, BorderLayout.SOUTH);
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

    // ==================== 分段控件 [原图 | 效果图 | 对比] ====================

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
        segment.setOpaque(false);
        segment.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        originalSegBtn = createSegBtn("原图", 0);
        segment.add(originalSegBtn);

        effectSegBtn = createSegBtn("效果图", 1);
        segment.add(effectSegBtn);

        compareSegBtn = createSegBtn("对比", 2);
        segment.add(compareSegBtn);

        container.add(segment);
        return container;
    }

    private JLabel createSegBtn(String text, int mode) {
        JLabel btn = new JLabel(text, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                if (currentPreviewMode == mode) {
                    g2.setColor(ThemeUtil.BG_CARD);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(),
                            ThemeUtil.ARC_TAG - 1, ThemeUtil.ARC_TAG - 1);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setOpaque(false);
        btn.setFont(ThemeUtil.FONT_SMALL);
        btn.setForeground(currentPreviewMode == mode ? ThemeUtil.PRIMARY : ThemeUtil.TEXT_SECONDARY);
        btn.setPreferredSize(new Dimension(54, 28));
        btn.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                setPreviewMode(mode);
            }
        });
        return btn;
    }

    private void setPreviewMode(int mode) {
        currentPreviewMode = mode;
        canvas.setCurrentMode(mode);
        updateSegStyle();
    }

    private void updateSegStyle() {
        originalSegBtn.setForeground(currentPreviewMode == 0 ? ThemeUtil.PRIMARY : ThemeUtil.TEXT_SECONDARY);
        effectSegBtn.setForeground(currentPreviewMode == 1 ? ThemeUtil.PRIMARY : ThemeUtil.TEXT_SECONDARY);
        compareSegBtn.setForeground(currentPreviewMode == 2 ? ThemeUtil.PRIMARY : ThemeUtil.TEXT_SECONDARY);
        segmentedControl.repaint();
    }

    // ==================== 半透明数据浮层 ====================

    private JPanel createOverlayBar() {
        JPanel bar = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(255, 255, 255, 220));
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
        lbl.setForeground(ThemeUtil.TEXT_TERTIARY);
        item.add(lbl, BorderLayout.NORTH);

        JLabel val = new JLabel(value, SwingConstants.CENTER);
        val.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        val.setForeground(ThemeUtil.TEXT_PRIMARY);
        item.add(val, BorderLayout.SOUTH);

        return item;
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
        origSizeLabel.setText(formatFileSize(originalSize));
        compSizeLabel.setText(formatFileSize(compressedSize));
        if (ratio >= 0) {
            ratioLabel.setText(String.format("−%.1f%%", ratio));
            ratioLabel.setForeground(ratio > 30 ? ThemeUtil.SUCCESS
                    : ratio > 10 ? ThemeUtil.PRIMARY : ThemeUtil.TEXT_TERTIARY);
        } else {
            ratioLabel.setText("—");
            ratioLabel.setForeground(ThemeUtil.TEXT_TERTIARY);
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
        updateSegStyle();
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

    private static String formatFileSize(long bytes) {
        if (bytes <= 0) return "—";
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        return String.format("%.1f MB", mb);
    }
}
