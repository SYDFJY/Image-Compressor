package com.nchu.imagecompress.view;

import com.nchu.imagecompress.util.ThemeUtil;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.image.BufferedImage;

/**
 * 鼠标悬停预览弹窗 — 无边框半透明 JWindow，在光标旁展示缩略图。
 *
 * <p>JWindow 不获取焦点、无标题栏，适合瞬时预览场景。
 * 创建后复用（show/hide），避免反复创建窗口的开销。</p>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-22
 */
public class HoverPreviewPopup {

    /** 预览图尺寸（像素） */
    private static final int PREVIEW_SIZE = 200;

    /** 弹窗相对光标的水平偏移 */
    private static final int OFFSET_X = 10;

    /** 弹窗相对光标的垂直偏移 */
    private static final int OFFSET_Y = 20;

    private final JWindow window;
    private final JLabel imageLabel;

    public HoverPreviewPopup() {
        window = new JWindow();
        window.setAlwaysOnTop(true);

        imageLabel = new JLabel();
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imageLabel.setVerticalAlignment(SwingConstants.CENTER);
        imageLabel.setPreferredSize(new Dimension(PREVIEW_SIZE, PREVIEW_SIZE));
        imageLabel.setBackground(ThemeUtil.BG_CARD);
        imageLabel.setOpaque(true);

        // 主题色 1px 边框
        javax.swing.border.Border lineBorder = BorderFactory.createLineBorder(
                ThemeUtil.PRIMARY, 1);
        // 内侧留白
        javax.swing.border.Border padding = BorderFactory.createEmptyBorder(4, 4, 4, 4);
        imageLabel.setBorder(BorderFactory.createCompoundBorder(lineBorder, padding));

        window.getContentPane().add(imageLabel);
        window.pack();
    }

    /**
     * 在指定屏幕坐标旁显示预览图。
     *
     * @param screenLocation 光标屏幕坐标
     * @param image          预览图像（null 时隐藏弹窗）
     */
    public void show(Point screenLocation, BufferedImage image) {
        if (image == null) {
            hide();
            return;
        }

        // 缩放图像到预览尺寸
        BufferedImage scaled = scaleToFit(image, PREVIEW_SIZE, PREVIEW_SIZE);
        ImageIcon icon = new ImageIcon(scaled);
        imageLabel.setIcon(icon);

        // 定位：光标右下方偏移
        int x = screenLocation.x + OFFSET_X;
        int y = screenLocation.y + OFFSET_Y;
        window.setLocation(x, y);

        if (!window.isVisible()) {
            window.setVisible(true);
        }
    }

    /** 隐藏弹窗 */
    public void hide() {
        window.setVisible(false);
    }

    /** 销毁弹窗并释放资源 */
    public void dispose() {
        window.dispose();
    }

    /**
     * 等比例缩放图像到目标尺寸内。
     */
    private static BufferedImage scaleToFit(BufferedImage source, int maxW, int maxH) {
        if (source == null) return null;

        int w = source.getWidth();
        int h = source.getHeight();
        if (w <= 0 || h <= 0) return source;

        double scale = Math.min((double) maxW / w, (double) maxH / h);
        if (scale >= 1.0 && w <= maxW && h <= maxH) {
            return source; // 已经足够小，无需缩放
        }

        int newW = Math.max(1, (int) (w * scale));
        int newH = Math.max(1, (int) (h * scale));

        java.awt.Image scaledInstance = source.getScaledInstance(
                newW, newH, java.awt.Image.SCALE_SMOOTH);
        BufferedImage result = new BufferedImage(
                newW, newH, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2 = result.createGraphics();
        g2.drawImage(scaledInstance, 0, 0, null);
        g2.dispose();
        return result;
    }
}
