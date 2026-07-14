package com.nchu.imagecompress.view.widget;

import com.nchu.imagecompress.util.ThemeUtil;
import com.formdev.flatlaf.ui.FlatComboBoxUI;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;

/**
 * 自定义下拉框 UI — 圆角边框 + 主题色下拉箭头。
 *
 * <p>基于 FlatLaf 的 {@link FlatComboBoxUI} 扩展，替换原生箭头按钮
 * 为主题色三角箭头。下拉列表选中项自动使用主题色底色。</p>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-14
 */
public class RoundedComboBoxUI extends FlatComboBoxUI {

    public static ComponentUI createUI(JComponent c) {
        return new RoundedComboBoxUI();
    }

    @Override
    protected JButton createArrowButton() {
        JButton btn = new JButton() {
            @Override
            public void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);

                // 背景
                g2.setColor(ThemeUtil.BG_CARD);
                g2.fillRect(0, 0, getWidth(), getHeight());

                // 主题色三角箭头
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                int size = 4;
                int[] xs = {cx - size, cx + size, cx};
                int[] ys = {cy - 2, cy - 2, cy + 2};
                Polygon arrow = new Polygon(xs, ys, 3);

                g2.setColor(ThemeUtil.PRIMARY);
                g2.fill(arrow);
                g2.dispose();
            }
        };
        btn.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        btn.setFocusPainted(false);
        return btn;
    }
}
