package com.nchu.imagecompress.view.widget;

import com.nchu.imagecompress.util.ThemeUtil;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.RoundRectangle2D;

/**
 * 现代化进度条组件 — 圆角轨道 + 主题色渐变填充 + 百分比文字叠加。
 *
 * <pre>
 * ┌────────────────────────────────────────────┐
 * │ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░░░░░░░ │ ← 圆角轨道
 * │        45%              📄 照片.jpg        │ ← 百分比 + 详情
 * └────────────────────────────────────────────┘
 * </pre>
 *
 * <h3>特性</h3>
 * <ul>
 *   <li>圆角轨道（6px），抗锯齿渲染</li>
 *   <li>进度填充使用主题 PRIMARY 色，100% 时切换为 SUCCESS 色</li>
 *   <li>百分比文字居中叠加在进度条上</li>
 *   <li>详情文字（文件名/计数）右对齐</li>
 *   <li>进度变化时 150ms 平滑过渡动画</li>
 *   <li>通过 {@link ThemeUtil#addThemeChangeListener} 自适应主题切换</li>
 * </ul>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-23
 */
public class ModernProgressBar extends JComponent {

    private static final int BAR_HEIGHT = 18;
    private static final int ARC = 8;
    private static final int TEXT_PADDING = 8;

    /** 目标进度 (0-100) */
    private double targetProgress = 0;
    /** 当前动画进度 (0-100) */
    private double currentProgress = 0;
    /** 动画 Timer */
    private final Timer animTimer;
    /** 动画步长（每 tick 增量） */
    private double animStep = 0;
    /** 详情文字 */
    private String detailText = "";

    private Color trackColor;
    private Color fillColor;
    private Color textColor;
    private Color detailColor;
    private Font font;

    public ModernProgressBar() {
        // 从当前主题读取颜色
        refreshColors();

        setPreferredSize(new Dimension(200, BAR_HEIGHT + 4));
        setMinimumSize(new Dimension(80, BAR_HEIGHT + 4));
        setOpaque(false);

        // 动画 Timer（16ms ≈ 60fps，平滑过渡）
        animTimer = new Timer(16, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (Math.abs(currentProgress - targetProgress) < 0.5) {
                    currentProgress = targetProgress;
                    animTimer.stop();
                } else {
                    // ease-out 缓动
                    currentProgress += (targetProgress - currentProgress) * 0.2;
                }
                repaint();
            }
        });

        // 主题切换时刷新颜色
        ThemeUtil.addThemeChangeListener(this::refreshColors);
    }

    private void refreshColors() {
        trackColor = new Color(ThemeUtil.BORDER.getRed(),
                ThemeUtil.BORDER.getGreen(),
                ThemeUtil.BORDER.getBlue(), 120);
        fillColor = ThemeUtil.PRIMARY;
        textColor = Color.WHITE;
        detailColor = ThemeUtil.TEXT_TERTIARY;
        font = ThemeUtil.FONT_TINY;
    }

    /**
     * 设置进度值 (0-100)，触发平滑动画。
     */
    public void setProgress(int percent) {
        targetProgress = Math.max(0, Math.min(100, percent));
        if (!animTimer.isRunning() && Math.abs(currentProgress - targetProgress) > 0.5) {
            animTimer.start();
        }
    }

    /**
     * 直接跳转到指定进度（无动画）。
     */
    public void jumpTo(int percent) {
        animTimer.stop();
        targetProgress = Math.max(0, Math.min(100, percent));
        currentProgress = targetProgress;
        repaint();
    }

    /**
     * 重置进度条（归零，隐藏详情）。
     */
    public void reset() {
        animTimer.stop();
        targetProgress = 0;
        currentProgress = 0;
        detailText = "";
        repaint();
    }

    /**
     * 设置详情文字（右侧显示，如文件名、计数）。
     */
    public void setDetail(String text) {
        this.detailText = (text != null) ? text : "";
        repaint();
    }

    // ==================== 绘制 ====================

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        int w = getWidth();
        int h = getHeight();
        int barY = (h - BAR_HEIGHT) / 2;
        int barH = BAR_HEIGHT;

        // --- 轨道 ---
        g2.setColor(trackColor);
        g2.fill(new RoundRectangle2D.Double(0, barY, w, barH, ARC, ARC));

        // --- 进度填充 ---
        if (currentProgress > 0) {
            int fillW = (int) (w * currentProgress / 100.0);
            if (fillW > 0) {
                // 100% 时使用 SUCCESS 色
                Color fgColor = (currentProgress >= 99) ? ThemeUtil.SUCCESS : fillColor;
                g2.setColor(fgColor);

                if (fillW >= w - ARC) {
                    // 接近满时用标准圆角矩形
                    g2.fill(new RoundRectangle2D.Double(0, barY, fillW, barH, ARC, ARC));
                } else {
                    // 左侧圆角 + 右侧直边（避免右侧圆角入侵未填充区域）
                    int rightArc = (fillW > ARC * 2) ? 0 : ARC;
                    g2.fill(new RoundRectangle2D.Double(0, barY, fillW, barH, ARC, ARC));
                }
            }
        }

        // --- 文字（在半透明背景上叠加） ---
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();

        if (currentProgress > 10) {
            // 进度条内显示百分比
            String pctText = (int) currentProgress + "%";
            int textW = fm.stringWidth(pctText);
            int textX = (w - textW) / 2;
            int textY = barY + (barH - fm.getHeight()) / 2 + fm.getAscent();

            // 文字阴影（增强可读性）
            g2.setColor(new Color(0, 0, 0, 80));
            g2.drawString(pctText, textX + 1, textY + 1);
            g2.setColor(textColor);
            g2.drawString(pctText, textX, textY);
        }

        // 详情文字（右对齐）
        if (detailText != null && !detailText.isEmpty()) {
            g2.setColor(detailColor);
            int detailW = fm.stringWidth(detailText);
            int detailX = w - detailW - TEXT_PADDING;
            int detailY = barY + barH + fm.getAscent() + 2;

            if (detailY + fm.getDescent() <= h) {
                g2.drawString(detailText, detailX, detailY);
            }
        }

        g2.dispose();
    }
}
