package com.nchu.imagecompress.view;

import com.nchu.imagecompress.model.Theme;
import com.nchu.imagecompress.util.ThemeUtil;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 主题切换弹出面板。
 *
 * <p>从导航栏主题按钮下方弹出，展示 6 套主题选项。
 * 每项包含：圆形色块（18px）+ 主题名称 + 副标题，选中态带左侧强调条。</p>
 *
 * <ul>
 *   <li>宽度 220px，内边距 8px，圆角 12px</li>
 *   <li>白色背景 + 二级阴影（通过 JPopupMenu 自带阴影）</li>
 *   <li>选中项：浅主色背景 + 主色文字加粗 + 左侧 2px 主色竖条</li>
 *   <li>悬停项：极浅主色背景</li>
 * </ul>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-09
 */
public class ThemePopup {

    /** 主题变更回调 */
    public interface ThemeChangeCallback {
        void onThemeChanged(Theme theme);
    }

    /**
     * 创建并返回主题选择弹出菜单。
     *
     * @param currentTheme 当前激活的主题
     * @param callback     主题变更回调
     * @return JPopupMenu 实例
     */
    public static JPopupMenu create(Theme currentTheme, ThemeChangeCallback callback) {
        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(BorderFactory.createEmptyBorder(ThemeUtil.SPACE_SM, ThemeUtil.SPACE_SM,
                ThemeUtil.SPACE_SM, ThemeUtil.SPACE_SM));

        // 面板标题
        JLabel titleLabel = new JLabel("  选择主题");
        titleLabel.setFont(ThemeUtil.FONT_SMALL);
        titleLabel.setForeground(ThemeUtil.TEXT_TERTIARY);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, ThemeUtil.SPACE_SM,
                ThemeUtil.SPACE_SM, ThemeUtil.SPACE_SM));
        titleLabel.setPreferredSize(new Dimension(200, 28));
        popup.add(titleLabel);

        // 主题列表
        Theme[] themes = Theme.values();
        for (Theme theme : themes) {
            ThemeItemPanel item = new ThemeItemPanel(theme, theme == currentTheme);
            item.setPreferredSize(new Dimension(200, 44));
            item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            item.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    popup.setVisible(false);
                    if (callback != null) {
                        callback.onThemeChanged(theme);
                    }
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    if (theme != currentTheme) {
                        item.setBackground(ThemeUtil.BG_HOVER);
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (theme != currentTheme) {
                        item.setBackground(ThemeUtil.BG_CARD);
                    }
                }
            });
            popup.add(item);
        }

        return popup;
    }

    /**
     * 单个主题项面板 — 色块 + 名称 + 副标题。
     */
    private static class ThemeItemPanel extends JPanel {

        private final Theme theme;
        private final boolean isSelected;
        private Color bg;

        ThemeItemPanel(Theme theme, boolean isSelected) {
            this.theme = theme;
            this.isSelected = isSelected;
            this.bg = isSelected ? ThemeUtil.BG_SELECTED : ThemeUtil.BG_CARD;

            setLayout(new BorderLayout(ThemeUtil.SPACE_MD, 0));
            setOpaque(true);
            setBackground(bg);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createEmptyBorder(ThemeUtil.SPACE_SM, ThemeUtil.SPACE_MD,
                            ThemeUtil.SPACE_SM, ThemeUtil.SPACE_MD),
                    BorderFactory.createMatteBorder(0, isSelected ? 2 : 0, 0, 0, ThemeUtil.PRIMARY)));

            // 左侧：色块
            JPanel dotPanel = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Color.decode(theme.getPrimaryHex()));
                    int d = 18;
                    int x = (getWidth() - d) / 2;
                    int y = (getHeight() - d) / 2;
                    g2.fillOval(x, y, d, d);
                    g2.dispose();
                }
            };
            dotPanel.setOpaque(false);
            dotPanel.setPreferredSize(new Dimension(18, 44));
            add(dotPanel, BorderLayout.WEST);

            // 中间：名称 + 副标题
            JPanel textPanel = new JPanel(new GridLayout(2, 1, 0, 2));
            textPanel.setOpaque(false);

            JLabel nameLabel = new JLabel(theme.getDisplayName());
            nameLabel.setFont(isSelected
                    ? new Font("Microsoft YaHei", Font.BOLD, 14)
                    : ThemeUtil.FONT_BODY);
            nameLabel.setForeground(isSelected ? ThemeUtil.PRIMARY : ThemeUtil.TEXT_PRIMARY);
            textPanel.add(nameLabel);

            JLabel subLabel = new JLabel(theme.getSubtitle());
            subLabel.setFont(ThemeUtil.FONT_TINY);
            subLabel.setForeground(ThemeUtil.TEXT_TERTIARY);
            textPanel.add(subLabel);

            add(textPanel, BorderLayout.CENTER);
        }

        @Override
        public void setBackground(Color bg) {
            this.bg = bg;
            super.setBackground(bg);
        }
    }
}
