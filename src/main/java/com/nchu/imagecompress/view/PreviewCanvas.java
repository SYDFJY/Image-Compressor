package com.nchu.imagecompress.view;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.nchu.imagecompress.util.ThemeUtil;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;

/**
 * 自适应预览画布 v2 — 缩放模式 + 蓝色描边 + 对比模式 + 1:1 拖拽平移。
 *
 * <p>v2 新增特性：
 * <ul>
 *   <li>缩放模式：FIT_INSIDE（默认）/ ACTUAL_PIXELS（1:1 + 拖拽平移）</li>
 *   <li>选中态蓝色描边：图片外围 2px PRIMARY 圆角边框</li>
 *   <li>对比模式：左右并排原图 + 效果图，4px 间隙</li>
 *   <li>棋盘格背景 + 空状态（保持 v1 特性）</li>
 * </ul>
 *
 * @author NCHU-Student
 * @version 2.0.0
 * @since 2026-07-08
 */
public class PreviewCanvas extends JPanel {

    // ==================== 缩放模式 ====================

    public enum ZoomMode { FIT_INSIDE, ACTUAL_PIXELS }

    // ==================== 图片数据 ====================

    private BufferedImage image;          // 原图
    private BufferedImage effectImage;    // 效果图（对比模式用）
    private int currentMode = 0;          // 0=原图, 1=效果图, 2=对比

    // ==================== 缩放与平移 ====================

    private ZoomMode zoomMode = ZoomMode.FIT_INSIDE;
    private int panOffsetX = 0;
    private int panOffsetY = 0;
    private Point dragStart;

    // ==================== 布局参数 ====================

    private int paddingTop = 0;
    private int paddingBottom = 0;
    private static final int PADDING = 12;
    private static final int GRID_SIZE = 16;

    /** 空状态相机图标（48×48） */
    private final FlatSVGIcon emptyIcon;

    // ==================== 构造 ====================

    public PreviewCanvas() {
        setOpaque(false);
        setDoubleBuffered(true);
        emptyIcon = new FlatSVGIcon("icons/camera.svg", 48, 48);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                repaint();
            }
        });

        // 实际像素模式下的拖拽平移
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (zoomMode == ZoomMode.ACTUAL_PIXELS && image != null) {
                    dragStart = e.getPoint();
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                dragStart = null;
                setCursor(Cursor.getDefaultCursor());
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart != null) {
                    panOffsetX += e.getX() - dragStart.x;
                    panOffsetY += e.getY() - dragStart.y;
                    dragStart = e.getPoint();
                    repaint();
                }
            }
        });
    }

    // ==================== API ====================

    public void setImage(BufferedImage image) {
        this.image = image;
        this.panOffsetX = 0;
        this.panOffsetY = 0;
        repaint();
    }

    public void clearImage() {
        this.image = null;
        this.effectImage = null;
        this.panOffsetX = 0;
        this.panOffsetY = 0;
        repaint();
    }

    public void setEffectImage(BufferedImage effectImage) {
        this.effectImage = effectImage;
        repaint();
    }

    public void setCurrentMode(int mode) {
        this.currentMode = mode;
        this.panOffsetX = 0;
        this.panOffsetY = 0;
        repaint();
    }

    public int getCurrentMode() { return currentMode; }

    public void setPaddingTop(int pt) { this.paddingTop = pt; repaint(); }
    public void setPaddingBottom(int pb) { this.paddingBottom = pb; repaint(); }
    public boolean hasImage() { return image != null; }

    // ==================== 缩放模式 ====================

    public void setZoomMode(ZoomMode mode) {
        this.zoomMode = mode;
        this.panOffsetX = 0;
        this.panOffsetY = 0;
        repaint();
    }

    public ZoomMode getZoomMode() { return zoomMode; }

    // ==================== 绘制 ====================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        drawCheckerboard(g, w, h);

        if (image == null) {
            drawEmptyState(g, w, h);
            return;
        }

        int availW = w - PADDING * 2;
        int availH = h - paddingTop - paddingBottom - PADDING * 2;
        if (availW <= 0 || availH <= 0) return;

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);

        // 对比模式
        if (currentMode == 2 && effectImage != null) {
            drawComparison(g2d, w, h, availW, availH);
            g2d.dispose();
            return;
        }

        BufferedImage toDraw = (currentMode == 1 && effectImage != null) ? effectImage : image;

        if (zoomMode == ZoomMode.ACTUAL_PIXELS) {
            drawActualPixels(g2d, toDraw);
        } else {
            drawFitInside(g2d, toDraw, availW, availH);
        }

        g2d.dispose();
    }

    // ==================== FIT_INSIDE ====================

    private void drawFitInside(Graphics2D g2d, BufferedImage img, int availW, int availH) {
        int imgW = img.getWidth();
        int imgH = img.getHeight();
        double scale = Math.min((double) availW / imgW, (double) availH / imgH);

        int scaledW = (int) (imgW * scale);
        int scaledH = (int) (imgH * scale);
        int x = PADDING + (availW - scaledW) / 2;
        int y = paddingTop + PADDING + (availH - scaledH) / 2;

        drawImageBorder(g2d, x, y, scaledW, scaledH);
        g2d.drawImage(img, x, y, scaledW, scaledH, null);
    }

    // ==================== ACTUAL_PIXELS ====================

    private void drawActualPixels(Graphics2D g2d, BufferedImage img) {
        int imgW = img.getWidth();
        int imgH = img.getHeight();
        int x = PADDING + panOffsetX;
        int y = paddingTop + PADDING + panOffsetY;

        // 裁剪区域
        java.awt.Shape clip = g2d.getClip();
        g2d.setClip(PADDING, paddingTop + PADDING,
                getWidth() - PADDING * 2,
                getHeight() - paddingTop - paddingBottom - PADDING * 2);

        drawImageBorder(g2d, x, y, imgW, imgH);
        g2d.drawImage(img, x, y, null);

        g2d.setClip(clip);
    }

    // ==================== 对比模式 ====================

    private void drawComparison(Graphics2D g2d, int w, int h, int availW, int availH) {
        int gap = 4;
        int halfW = (availW - gap) / 2;
        if (halfW <= 0) return;

        // 统一缩放比：取左右两侧能容纳的最小缩放值，保证两侧同步缩放
        double scaleLeft = Math.min((double) halfW / image.getWidth(),
                (double) availH / image.getHeight());
        double scaleRight = Math.min((double) halfW / effectImage.getWidth(),
                (double) availH / effectImage.getHeight());
        double unifiedScale = Math.min(scaleLeft, scaleRight);

        // 左侧：原图（居中于左半区域）
        int lw = (int) (image.getWidth() * unifiedScale);
        int lh = (int) (image.getHeight() * unifiedScale);
        int leftX = PADDING + (halfW - lw) / 2;
        int ly = paddingTop + PADDING + (availH - lh) / 2;
        drawImageBorder(g2d, leftX, ly, lw, lh);
        g2d.drawImage(image, leftX, ly, lw, lh, null);

        // 标签：原图 + 尺寸（左上角半透明）
        drawComparisonLabel(g2d, leftX, ly,
                "原图 " + image.getWidth() + "×" + image.getHeight());

        // 右侧：效果图（居中于右半区域）
        int rw = (int) (effectImage.getWidth() * unifiedScale);
        int rh = (int) (effectImage.getHeight() * unifiedScale);
        int rightX = PADDING + halfW + gap + (halfW - rw) / 2;
        int ry = paddingTop + PADDING + (availH - rh) / 2;
        drawImageBorder(g2d, rightX, ry, rw, rh);
        g2d.drawImage(effectImage, rightX, ry, rw, rh, null);

        // 标签：效果 + 尺寸（左上角半透明）
        drawComparisonLabel(g2d, rightX, ry,
                "效果 " + effectImage.getWidth() + "×" + effectImage.getHeight());

        // 中间分隔线
        int dividerX = PADDING + halfW + gap / 2 - 1;
        g2d.setColor(new Color(0, 0, 0, 80));
        g2d.fillRoundRect(dividerX, paddingTop + PADDING + 8, 2,
                getHeight() - paddingTop - paddingBottom - PADDING * 2 - 16, 1, 1);
    }

    /**
     * 在对比模式图片左上角绘制半透明标签。
     */
    private void drawComparisonLabel(Graphics2D g2d, int imgX, int imgY, String text) {
        g2d.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
        FontMetrics fm = g2d.getFontMetrics();
        int labelW = fm.stringWidth(text) + 10;
        int labelH = fm.getHeight() + 4;
        g2d.setColor(new Color(0, 0, 0, 100));
        g2d.fillRoundRect(imgX + 3, imgY + 3, labelW, labelH, 4, 4);
        g2d.setColor(Color.WHITE);
        g2d.drawString(text, imgX + 8, imgY + 3 + fm.getAscent() + 2);
    }

    // ==================== 蓝色描边 ====================

    private void drawImageBorder(Graphics2D g2d, int x, int y, int w, int h) {
        g2d.setColor(ThemeUtil.PRIMARY);
        g2d.setStroke(new BasicStroke(2f));
        g2d.drawRoundRect(x - 1, y - 1, w + 2, h + 2, 6, 6);
        g2d.setStroke(new BasicStroke(1f));
    }

    // ==================== 棋盘格背景 ====================

    private void drawCheckerboard(Graphics g, int w, int h) {
        g.setColor(ThemeUtil.BG_CARD);
        g.fillRect(0, 0, w, h);

        // 主题感知棋盘格颜色 — 跟随主题切换自动变化
        Color lightCell = ThemeUtil.BG_CARD;
        Color darkCell = ThemeUtil.BG_HOVER;
        int cols = w / GRID_SIZE + 1;
        int rows = h / GRID_SIZE + 1;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                g.setColor((r + c) % 2 == 0 ? lightCell : darkCell);
                g.fillRect(c * GRID_SIZE, r * GRID_SIZE, GRID_SIZE, GRID_SIZE);
            }
        }
    }

    // ==================== 空状态 ====================

    private void drawEmptyState(Graphics g, int w, int h) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        // 相机图标（48×48，居中）
        int iconW = emptyIcon.getIconWidth();
        int iconH = emptyIcon.getIconHeight();
        int iconX = (w - iconW) / 2;
        int iconY = h / 2 - 52;
        emptyIcon.paintIcon(this, g2d, iconX, iconY);

        // 标题文字
        g2d.setFont(ThemeUtil.FONT_BODY);
        g2d.setColor(ThemeUtil.TEXT_SECONDARY);
        String title = "选择图片以预览";
        FontMetrics fmTitle = g2d.getFontMetrics();
        int titleY = iconY + iconH + 12 + fmTitle.getAscent();
        g2d.drawString(title, (w - fmTitle.stringWidth(title)) / 2, titleY);

        // 副标题
        g2d.setFont(ThemeUtil.FONT_SMALL);
        g2d.setColor(ThemeUtil.TEXT_TERTIARY);
        String subtitle = "从左侧文件列表选中图片查看效果";
        g2d.drawString(subtitle, (w - g2d.getFontMetrics().stringWidth(subtitle)) / 2,
                titleY + 22);

        g2d.dispose();
    }
}
