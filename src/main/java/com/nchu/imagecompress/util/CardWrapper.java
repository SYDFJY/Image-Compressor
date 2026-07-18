package com.nchu.imagecompress.util;

import com.nchu.imagecompress.model.ThemePalette;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * 卡片包装器 — 为任意 Swing 组件提供 12px 圆角 + 主题感知单层柔阴影的容器。
 *
 * <p>阴影层次（简洁风格，参考蓝韵音乐设计体系）：
 * <ol>
 *   <li>4px 偏移、shadow1 — 单层扩散阴影</li>
 *   <li>卡片主体背景</li>
 * </ol>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * FileListPanel list = new FileListPanel();
 * JPanel card = new CardWrapper(list);
 * card.add(list, BorderLayout.CENTER);
 * }</pre>
 *
 * <p>右侧预留 4px、底部预留 6px 给阴影绘制区域。
 * 主题切换后通过 {@link ThemeUtil#addThemeChangeListener} 触发 repaint(),
 * 阴影色自动跟随 {@link ThemeUtil#getCurrentPalette()}。</p>
 *
 * @author NCHU-Student
 * @version 1.1.0
 * @since 2026-07-14
 */
public class CardWrapper extends JPanel {

    /** 卡片圆角半径 */
    private static final int ARC = ThemePalette.ARC_CARD;

    /** 是否为暗色主题启用 1px 卡片边框 */
    private final boolean withBorder;

    /**
     * 创建仅含阴影的卡片容器。
     *
     * @param content 被包裹的内容组件
     */
    public CardWrapper(JComponent content) {
        this(content, false);
    }

    /**
     * 创建卡片容器，可选 1px 边框（暗色主题需要）。
     *
     * @param content    被包裹的内容组件
     * @param withBorder 是否绘制卡片边框
     */
    public CardWrapper(JComponent content, boolean withBorder) {
        this.withBorder = withBorder;
        setLayout(new BorderLayout());
        setOpaque(false);
        // 右侧 4px + 底部 6px 预留给阴影
        setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 4));
        add(content, BorderLayout.CENTER);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth() - 4;
        int h = getHeight() - 6;

        ThemePalette palette = ThemeUtil.getCurrentPalette();
        Color shadow1 = palette != null ? palette.getShadow1()
                : new Color(0, 0, 0, 15);

        // 单层阴影（4px 偏移）— 参考蓝韵音乐 0 4px 12px rgba(0,0,0,0.08)
        g2.setColor(shadow1);
        g2.fillRoundRect(4, 4, w - 4, h - 4, ARC, ARC);

        // 卡片主体
        g2.setColor(ThemeUtil.BG_CARD);
        g2.fillRoundRect(0, 0, w, h, ARC, ARC);

        // 可选：卡片边框（暗色主题需要）
        if (withBorder && palette != null && palette.cardHasBorder
                && palette.cardBorderColor != null) {
            g2.setColor(palette.cardBorderColor);
            g2.drawRoundRect(0, 0, w, h, ARC, ARC);
        }

        g2.dispose();
    }
}
