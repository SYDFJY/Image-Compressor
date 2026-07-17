package com.nchu.imagecompress.view.widget;

import com.nchu.imagecompress.util.ThemeUtil;
import com.formdev.flatlaf.ui.FlatSliderUI;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/**
 * 自定义滑块 UI — 6px 圆角轨道 + 14px 圆形主题色滑块。
 *
 * <p>基于 FlatLaf 的 {@link FlatSliderUI} 扩展，覆盖轨道和滑块的绘制逻辑。
 * 主题切换后自动从 {@link ThemeUtil} 重新读取颜色。</p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * mySlider.setUI(new GradientSliderUI());
 * }</pre>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-14
 */
public class GradientSliderUI extends FlatSliderUI {

    private static final int TRACK_H = 6;
    private static final int THUMB_D = 14;

    @Override
    public void installUI(JComponent c) {
        super.installUI(c);
    }

    @Override
    public void paintTrack(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        if (slider.getOrientation() != javax.swing.SwingConstants.HORIZONTAL) {
            g2.dispose();
            super.paintTrack(g);
            return;
        }

        int trackY = trackRect.y + (trackRect.height - TRACK_H) / 2;
        int trackW = trackRect.width;
        int trackX = trackRect.x;
        int arc = ThemeUtil.ARC_PILL;

        // 未填充段：边框色
        g2.setColor(ThemeUtil.BORDER);
        g2.fill(new RoundRectangle2D.Double(trackX, trackY, trackW, TRACK_H, arc, arc));

        // 已填充段：主题色（左端 → 滑块中心）
        int thumbPos = thumbRect.x + (thumbRect.width / 2);
        int fillW = thumbPos - trackX;
        if (fillW > 0) {
            g2.setColor(ThemeUtil.PRIMARY);
            g2.fill(new RoundRectangle2D.Double(trackX, trackY, fillW, TRACK_H, arc, arc));
        }

        g2.dispose();
    }

    @Override
    public void paintThumb(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        int x = thumbRect.x + (thumbRect.width - THUMB_D) / 2;
        int y = thumbRect.y + (thumbRect.height - THUMB_D) / 2;

        // 外投影
        g2.setColor(new Color(0, 0, 0, 30));
        g2.fillOval(x + 1, y + 2, THUMB_D, THUMB_D);

        // 主体：主题色填充
        g2.setColor(ThemeUtil.PRIMARY);
        g2.fillOval(x, y, THUMB_D, THUMB_D);

        // 白色边框
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(2f));
        g2.drawOval(x + 1, y + 1, THUMB_D - 2, THUMB_D - 2);

        g2.dispose();
    }

    @Override
    protected Dimension getThumbSize() {
        return new Dimension(THUMB_D + 4, THUMB_D + 4);
    }
}
