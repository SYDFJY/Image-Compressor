package com.nchu.imagecompress.view.widget;

import com.nchu.imagecompress.util.ThemeUtil;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * iOS 风格滑动开关 — 替代 JCheckBox 的自定义绘制组件。
 *
 * <p>视觉参数：
 * <ul>
 *   <li>轨道：36×20px 胶囊形，关态=边框色，开态=主题色</li>
 *   <li>滑块：16px 白色圆形，带 1px 浅阴影</li>
 *   <li>动画：180ms 缓动滑动</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * ToggleSwitch toggle = new ToggleSwitch("覆盖已存在的输出文件");
 * toggle.setSelected(true);
 * toggle.addActionListener(e -> System.out.println(toggle.isSelected()));
 * }</pre>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-14
 */
public class ToggleSwitch extends JComponent {

    private static final int TRACK_W = 36;
    private static final int TRACK_H = 20;
    private static final int KNOB_D = 16;
    private static final int ANIM_MS = 180;
    private static final int FPS = 30;

    private boolean selected;
    private float animPosition; // 0.0 = off, 1.0 = on
    private Timer animTimer;
    private final List<ActionListener> listeners = new ArrayList<>();
    private final String label;

    public ToggleSwitch(String label) {
        this.label = label;
        this.selected = false;
        this.animPosition = 0f;

        setPreferredSize(new Dimension(TRACK_W + 8, TRACK_H + 4));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setOpaque(false);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                setSelected(!selected);
                fireActionEvent();
                requestFocusInWindow();
            }
        });
    }

    public boolean isSelected() { return selected; }

    public void setSelected(boolean sel) {
        if (this.selected == sel) return;
        this.selected = sel;
        animateTo(sel ? 1f : 0f);
    }

    private void animateTo(float target) {
        if (animTimer != null && animTimer.isRunning()) {
            animTimer.stop();
        }
        float start = animPosition;
        long startTime = System.currentTimeMillis();
        animTimer = new Timer(1000 / FPS, e -> {
            float elapsed = (System.currentTimeMillis() - startTime) / (float) ANIM_MS;
            if (elapsed >= 1f) {
                animPosition = target;
                ((Timer) e.getSource()).stop();
            } else {
                // easeInOut cubic
                float t = elapsed;
                float eased = t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
                animPosition = start + (target - start) * eased;
            }
            repaint();
        });
        animTimer.start();
    }

    private void fireActionEvent() {
        ActionEvent evt = new ActionEvent(this, ActionEvent.ACTION_PERFORMED,
                selected ? "on" : "off");
        for (ActionListener l : listeners) {
            l.actionPerformed(evt);
        }
    }

    public void addActionListener(ActionListener l) {
        if (l != null) listeners.add(l);
    }

    public void removeActionListener(ActionListener l) {
        listeners.remove(l);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int trackX = 4;
        int trackY = (h - TRACK_H) / 2;
        int knobY = trackY + (TRACK_H - KNOB_D) / 2;

        // 轨道
        Color trackColor;
        if (selected || animPosition > 0f) {
            int r = interpolate(ThemeUtil.BORDER.getRed(), ThemeUtil.PRIMARY.getRed());
            int g2c = interpolate(ThemeUtil.BORDER.getGreen(), ThemeUtil.PRIMARY.getGreen());
            int b = interpolate(ThemeUtil.BORDER.getBlue(), ThemeUtil.PRIMARY.getBlue());
            trackColor = new Color(r, g2c, b);
        } else {
            trackColor = ThemeUtil.BORDER;
        }
        g2.setColor(trackColor);
        g2.fillRoundRect(trackX, trackY, TRACK_W, TRACK_H, TRACK_H, TRACK_H);

        // 滑块位置
        int knobMaxX = trackX + TRACK_W - KNOB_D - 2;
        int knobMinX = trackX + 2;
        int knobX = knobMinX + Math.round((knobMaxX - knobMinX) * animPosition);

        // 滑块阴影
        g2.setColor(new Color(0, 0, 0, 30));
        g2.fillOval(knobX + 1, knobY + 1, KNOB_D, KNOB_D);

        // 滑块
        g2.setColor(Color.WHITE);
        g2.fillOval(knobX, knobY, KNOB_D, KNOB_D);

        g2.dispose();

        // 标签文字右移
        if (label != null && !label.isEmpty()) {
            // 标签由外部 JLabel 负责，本组件只画开关
        }
    }

    private int interpolate(int a, int b) {
        return a + Math.round((b - a) * animPosition);
    }
}
