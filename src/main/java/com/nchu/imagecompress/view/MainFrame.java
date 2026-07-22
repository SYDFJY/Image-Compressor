package com.nchu.imagecompress.view;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.nchu.imagecompress.model.Theme;
import com.nchu.imagecompress.model.ThemePalette;
import com.nchu.imagecompress.util.CardWrapper;
import com.nchu.imagecompress.util.ThemeUtil;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;

/**
 * 应用主窗口（UI 升级版 — 悬浮导航栏 + 卡片化布局）。
 *
 * <pre>
 * ┌──────────────────────────────────────────────────┐
 * │  [Logo] NCHU Image Compressor  │  导入 清空  │  ☀ ⚙ │  ← 悬浮工具栏卡片
 * ├──────────────────────────────────────────────────┤
 * │  ┌─────────────┐  ┌──────────────────────────┐   │
 * │  │  文件列表    │  │      预览面板             │   │  ← 白色卡片
 * │  │  (卡片)     │  │      (卡片)              │   │
 * │  └─────────────┘  │  ┌────────────────────┐  │   │
 * │                   │  │   参数设置 (卡片)   │  │   │
 * │                   │  └────────────────────┘  │   │
 * ├──────────────────────────────────────────────────┤
 * │  ● 就绪                           共 0 个文件    │  ← 状态栏
 * └──────────────────────────────────────────────────┘
 * </pre>
 *
 * @author NCHU-Student
 * @version 2.0.0
 * @since 2026-07-08
 */
public class MainFrame extends JFrame {

    private static final int DEFAULT_WIDTH = 1060;
    private static final int DEFAULT_HEIGHT = 720;
    private static final int MIN_WIDTH = 860;
    private static final int MIN_HEIGHT = 620;

    // ==================== 菜单项 ====================
    private JMenuItem importFilesItem;
    private JMenuItem importFolderItem;
    private JMenuItem exitItem;
    private JMenuItem clearAllItem;
    private JMenuItem settingsItem;
    private JMenuItem toggleThemeItem;
    private JMenuItem aboutItem;

    // ==================== 工具栏按钮 ====================
    private JButton importBtn;
    private JButton importFolderBtn;
    private JButton clearBtn;
    private JButton themeBtn;
    // 模式切换分段控件（图片 / 视频）
    private JToggleButton imageModeBtn;
    private JToggleButton videoModeBtn;
    private JPanel modeSegmentPanel;

    // ==================== 面板引用 ====================
    private FileListPanel fileListPanel;
    private PreviewPanel previewPanel;
    private ParamPanel paramPanel;
    private VideoPreviewPanel videoPreviewPanel;
    private VideoParamPanel videoParamPanel;
    private StatusBar statusBar;
    private JSplitPane mainSplitPane;
    private JSplitPane rightSplitPane;

    // ==================== 布局管理器 ====================
    private CardLayout rightCardLayout;
    private JPanel rightCardPanel;
    private JPanel imageRightPanel;
    private JPanel videoRightPanel;

    private static final String CARD_IMAGE = "IMAGE";
    private static final String CARD_VIDEO = "VIDEO";

    public MainFrame() {
        initFrame();
        initMenuBar();
        initToolBar();
        initPanels();
        initStatusBar();
    }

    private void initFrame() {
        setTitle("NCHU Compressor");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));
        // 窗口背景：支持渐变（暗色主题）或纯色（浅色主题）
        backgroundPanel = new BackgroundPanel();
        setContentPane(backgroundPanel);
        backgroundPanel.setLayout(new BorderLayout(0, 0));
        centerOnScreen();
        // 主题切换时重绘背景
        ThemeUtil.addThemeChangeListener(() -> {
            backgroundPanel.repaint();
        });
    }

    /** 窗口背景面板 — 支持渐变（暗色主题）或纯色（浅色主题） */
    private BackgroundPanel backgroundPanel;

    private class BackgroundPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            ThemePalette palette = ThemeUtil.getCurrentPalette();
            if (palette != null && palette.bgWindowEnd != null) {
                // 渐变背景（暗色主题）
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                double angleRad = Math.toRadians(palette.bgGradientAngle);
                int w = getWidth();
                int h = getHeight();
                // 计算渐变起终点（135° 对角线：左上→右下）
                int dx = (int)(Math.cos(angleRad) * Math.max(w, h));
                int dy = (int)(Math.sin(angleRad) * Math.max(w, h));
                GradientPaint gp = new GradientPaint(
                        0, 0, palette.bgWindowEnd,
                        dx > 0 ? dx : w, dy > 0 ? dy : h, palette.bgWindow,
                        false);
                g2.setPaint(gp);
                g2.fillRect(0, 0, w, h);
            } else {
                // 纯色背景（浅色主题，向后兼容）
                g2.setColor(ThemeUtil.BG_WINDOW);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
            g2.dispose();
        }
    }

    private void initMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeUtil.BORDER));

        JMenu fileMenu = new JMenu("文件(F)");
        fileMenu.setMnemonic(KeyEvent.VK_F);
        importFilesItem = new JMenuItem("导入图片...");
        importFilesItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK));
        fileMenu.add(importFilesItem);
        importFolderItem = new JMenuItem("导入文件夹...");
        importFolderItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O,
                KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK));
        fileMenu.add(importFolderItem);
        fileMenu.add(new JSeparator());
        exitItem = new JMenuItem("退出");
        exitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, KeyEvent.CTRL_DOWN_MASK));
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);

        JMenu editMenu = new JMenu("编辑(E)");
        editMenu.setMnemonic(KeyEvent.VK_E);
        clearAllItem = new JMenuItem("清空列表");
        clearAllItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE,
                KeyEvent.CTRL_DOWN_MASK));
        editMenu.add(clearAllItem);
        editMenu.add(new JSeparator());
        settingsItem = new JMenuItem("设置...");
        settingsItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_COMMA,
                KeyEvent.CTRL_DOWN_MASK));
        editMenu.add(settingsItem);
        menuBar.add(editMenu);

        JMenu viewMenu = new JMenu("视图(V)");
        viewMenu.setMnemonic(KeyEvent.VK_V);
        toggleThemeItem = new JMenuItem("切换深色/浅色主题");
        toggleThemeItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T,
                KeyEvent.CTRL_DOWN_MASK));
        viewMenu.add(toggleThemeItem);
        menuBar.add(viewMenu);

        JMenu helpMenu = new JMenu("帮助(H)");
        helpMenu.setMnemonic(KeyEvent.VK_H);
        aboutItem = new JMenuItem("关于...");
        aboutItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
        helpMenu.add(aboutItem);
        menuBar.add(helpMenu);

        setJMenuBar(menuBar);
    }

    /**
     * 初始化一体化悬浮工具栏（v2 — 56px 二次阴影 + 按钮边框 + 主次分明）。
     *
     * <pre>
     * WEST: [图标] NCHU Image Compressor 图片压缩工具
     * CENTER: [导入] [导入文件夹] [清空] — 边框按钮
     * EAST: ☀ 主题 + ▶ 开始压缩(主按钮)
     * </pre>
     */
    private void initToolBar() {
        JPanel toolBarPanel = new JPanel(new BorderLayout());
        toolBarPanel.setBackground(ThemeUtil.BG_CARD);
        toolBarPanel.setOpaque(true);
        // 主题切换时刷新工具栏背景
        ThemeUtil.addThemeChangeListener(() -> {
            toolBarPanel.setBackground(ThemeUtil.BG_CARD);
            toolBarPanel.repaint();
        });
        toolBarPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeUtil.BORDER),
                BorderFactory.createEmptyBorder(12, 20, 12, 20)));

        // --- 左侧：品牌 ---
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftPanel.setOpaque(false);

        JLabel logoLabel = new JLabel("🖼");
        logoLabel.setFont(new java.awt.Font("Segoe UI Emoji", java.awt.Font.PLAIN, 20));
        leftPanel.add(logoLabel);

        JLabel nameLabel = new JLabel("NCHU Compressor");
        nameLabel.setFont(ThemeUtil.FONT_TITLE);
        ThemeUtil.setDynamicForeground(nameLabel, () -> ThemeUtil.TEXT_PRIMARY);
        leftPanel.add(nameLabel);

        JLabel subLabel = new JLabel("图片/视频压缩工具");
        subLabel.setFont(ThemeUtil.FONT_SMALL);
        ThemeUtil.setDynamicForeground(subLabel, () -> ThemeUtil.TEXT_TERTIARY);
        leftPanel.add(subLabel);

        toolBarPanel.add(leftPanel, BorderLayout.WEST);

        // --- 中间：操作按钮组（图标+文字，主次分层） ---
        JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        centerPanel.setOpaque(false);

        importBtn = createSecondaryButton("导入文件", "icons/import.svg");
        importBtn.setToolTipText("导入图片文件 (Ctrl+O)");
        centerPanel.add(importBtn);

        importFolderBtn = createSecondaryButton("导入文件夹", "icons/folder.svg");
        importFolderBtn.setToolTipText("导入文件夹中的所有图片 (Ctrl+Shift+O)");
        centerPanel.add(importFolderBtn);

        clearBtn = createTertiaryButton("清空列表", "icons/delete.svg");
        clearBtn.setToolTipText("清空文件列表 (Ctrl+Delete)");
        centerPanel.add(clearBtn);

        toolBarPanel.add(centerPanel, BorderLayout.CENTER);

        // --- 右侧：功能图标 + 主按钮 ---
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightPanel.setOpaque(false);

        // 模式切换分段控件（图片 | 视频）
        modeSegmentPanel = createModeSegmentedControl();
        rightPanel.add(modeSegmentPanel);

        themeBtn = new JButton("默认蓝调", new FlatSVGIcon("icons/palette.svg"));
        themeBtn.setFont(ThemeUtil.FONT_SMALL);
        themeBtn.setToolTipText("切换主题");
        themeBtn.setFocusPainted(false);
        ThemeUtil.setDynamicBackground(themeBtn, () -> ThemeUtil.BG_CARD);
        ThemeUtil.setDynamicForeground(themeBtn, () -> ThemeUtil.TEXT_SECONDARY);
        themeBtn.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        themeBtn.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        ThemeUtil.addThemeChangeListener(() -> themeBtn.setBackground(ThemeUtil.BG_CARD));
        rightPanel.add(themeBtn);

        toolBarPanel.add(rightPanel, BorderLayout.EAST);

        add(toolBarPanel, BorderLayout.NORTH);
    }

    /** 创建次要按钮（图标+文字，32px 高，浅底色，hover 变色） */
    private JButton createSecondaryButton(String text) {
        return createSecondaryButton(text, null);
    }

    private JButton createSecondaryButton(String text, String iconPath) {
        JButton btn = (iconPath != null)
                ? new JButton(text, new FlatSVGIcon(iconPath))
                : new JButton(text);
        btn.setFont(ThemeUtil.FONT_BODY);
        btn.setFocusPainted(false);
        ThemeUtil.setDynamicBackground(btn, () -> ThemeUtil.BG_HOVER);
        ThemeUtil.setDynamicForeground(btn, () -> ThemeUtil.TEXT_PRIMARY);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeUtil.PRIMARY, 1),
                BorderFactory.createEmptyBorder(5, 14, 5, 14)));
        btn.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        // hover/press 效果
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btn.setBackground(ThemeUtil.BG_SELECTED);
                btn.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(ThemeUtil.PRIMARY_DEEP, 1),
                        BorderFactory.createEmptyBorder(5, 14, 5, 14)));
            }
            public void mouseExited(java.awt.event.MouseEvent e) {
                btn.setBackground(ThemeUtil.BG_HOVER);
                btn.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(ThemeUtil.PRIMARY, 1),
                        BorderFactory.createEmptyBorder(5, 14, 5, 14)));
            }
            public void mousePressed(java.awt.event.MouseEvent e) {
                btn.setBackground(ThemeUtil.PRIMARY_LIGHTEST);
            }
            public void mouseReleased(java.awt.event.MouseEvent e) {
                btn.setBackground(ThemeUtil.BG_SELECTED);
            }
        });
        // 主题切换时重置为默认态背景
        ThemeUtil.addThemeChangeListener(() -> {
            btn.setBackground(ThemeUtil.BG_HOVER);
            btn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ThemeUtil.PRIMARY, 1),
                    BorderFactory.createEmptyBorder(5, 14, 5, 14)));
        });
        return btn;
    }

    /** 创建三级文字按钮（无边框，弱化，hover 加深） */
    private JButton createTertiaryButton(String text) {
        return createTertiaryButton(text, null);
    }

    private JButton createTertiaryButton(String text, String iconPath) {
        JButton btn = (iconPath != null)
                ? new JButton(text, new FlatSVGIcon(iconPath))
                : new JButton(text);
        btn.setFont(ThemeUtil.FONT_SMALL);
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        ThemeUtil.setDynamicForeground(btn, () -> ThemeUtil.TEXT_TERTIARY);
        btn.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        btn.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) {
                ThemeUtil.setDynamicForeground(btn, () -> ThemeUtil.TEXT_PRIMARY);
            }
            public void mouseExited(java.awt.event.MouseEvent e) {
                ThemeUtil.setDynamicForeground(btn, () -> ThemeUtil.TEXT_TERTIARY);
            }
        });
        return btn;
    }

    /** 渐变按钮 client property key（v2 — 每个按钮独立状态，消除实例变量竞态） */
    private static final String PROP_HOVERED = "gradientBtnHovered";
    private static final String PROP_PRESSED = "gradientBtnPressed";

    /** 创建渐变主操作按钮（40px 高，渐变填充，发光投影，hover 增强发光） */
    private JButton createGradientButton(String text) {
        return createGradientButton(text, null);
    }

    private JButton createGradientButton(String text, String iconPath) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();

                // 通过 client property 读取当前按钮的独立状态
                Boolean hovered = (Boolean) getClientProperty(PROP_HOVERED);
                Boolean pressed = (Boolean) getClientProperty(PROP_PRESSED);
                boolean isHovered = hovered != null && hovered;
                boolean isPressed = pressed != null && pressed;

                // hover 增强发光 / press 加深（使用主题按钮发光色）
                int glowAlpha = isPressed ? 100 : (isHovered ? 90 : 60);
                ThemePalette palette = ThemeUtil.getCurrentPalette();
                java.awt.Color glow = (palette != null && palette.buttonHasGlow && palette.buttonGlowColor != null)
                        ? palette.buttonGlowColor
                        : ThemeUtil.PRIMARY_DEEP;
                g2.setColor(new java.awt.Color(glow.getRed(), glow.getGreen(),
                        glow.getBlue(), glowAlpha));
                g2.fillRoundRect(2, 4, w - 4, h, ThemeUtil.ARC_BUTTON,
                        ThemeUtil.ARC_BUTTON);

                // 渐变填充（press 时加深）
                java.awt.Color top = isPressed
                        ? new java.awt.Color(
                                Math.max(0, ThemeUtil.PRIMARY_DEEP.getRed() - 30),
                                Math.max(0, ThemeUtil.PRIMARY_DEEP.getGreen() - 30),
                                Math.max(0, ThemeUtil.PRIMARY_DEEP.getBlue() - 30))
                        : ThemeUtil.PRIMARY_DEEP;
                java.awt.Color bottom = isPressed
                        ? new java.awt.Color(
                                Math.max(0, ThemeUtil.PRIMARY.getRed() - 20),
                                Math.max(0, ThemeUtil.PRIMARY.getGreen() - 20),
                                Math.max(0, ThemeUtil.PRIMARY.getBlue() - 20))
                        : ThemeUtil.PRIMARY;
                java.awt.GradientPaint gp = new java.awt.GradientPaint(0, 0, top, 0, h, bottom);
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, w, h - 2, ThemeUtil.ARC_BUTTON,
                        ThemeUtil.ARC_BUTTON);
                g2.dispose();

                setForeground(java.awt.Color.WHITE);
                super.paintComponent(g);
            }
        };
        if (iconPath != null) {
            btn.setIcon(new FlatSVGIcon(iconPath));
        }
        btn.putClientProperty(PROP_HOVERED, false);
        btn.putClientProperty(PROP_PRESSED, false);
        btn.setFont(ThemeUtil.FONT_TITLE);
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setForeground(java.awt.Color.WHITE);
        btn.setBorder(BorderFactory.createEmptyBorder(10, 24, 10, 24));
        btn.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btn.putClientProperty(PROP_HOVERED, true);
                btn.repaint();
            }
            public void mouseExited(java.awt.event.MouseEvent e) {
                btn.putClientProperty(PROP_HOVERED, false);
                btn.repaint();
            }
            public void mousePressed(java.awt.event.MouseEvent e) {
                btn.putClientProperty(PROP_PRESSED, true);
                btn.repaint();
            }
            public void mouseReleased(java.awt.event.MouseEvent e) {
                btn.putClientProperty(PROP_PRESSED, false);
                btn.repaint();
            }
        });
        ThemeUtil.addThemeChangeListener(btn::repaint);
        return btn;
    }

    /**
     * 创建图片/视频模式切换分段控件。
     *
     * <p>两个按钮始终可见，选中段高亮，未选中段灰色 —
     * 新用户一眼就能看到存在两种压缩模式。</p>
     */
    private JPanel createModeSegmentedControl() {
        // 外层容器
        JPanel container = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        container.setOpaque(false);

        // 分段底板：BG_HOVER 底色 + 6px 圆角
        JPanel segment = new JPanel(new GridLayout(1, 2, 0, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(ThemeUtil.BG_HOVER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(),
                        ThemeUtil.ARC_TAG, ThemeUtil.ARC_TAG);
                g2.dispose();
            }
        };
        segment.setOpaque(true);
        segment.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        ButtonGroup group = new ButtonGroup();

        // 图片段
        imageModeBtn = createModeSegToggle("图片", "icons/image.svg");
        imageModeBtn.setToolTipText("切换到图片压缩模式");
        group.add(imageModeBtn);
        segment.add(imageModeBtn);

        // 视频段
        videoModeBtn = createModeSegToggle("视频", "icons/film.svg");
        videoModeBtn.setToolTipText("切换到视频压缩模式");
        group.add(videoModeBtn);
        segment.add(videoModeBtn);

        // 默认选中图片模式
        imageModeBtn.setSelected(true);

        container.add(segment);
        return container;
    }

    /**
     * 创建分段控件中的单个段按钮（图标 + 文字，自绘背景和文字）。
     *
     * <p>FlatLaf ButtonUI 在 opaque=false + contentAreaFilled=false 时
     * 可能不渲染文字，因此这里手动绘制背景和文字，确保主题切换时颜色正确。</p>
     */
    private JToggleButton createModeSegToggle(String text, String iconPath) {
        JToggleButton btn = new JToggleButton(text, new FlatSVGIcon(iconPath)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                // 背景：选中时 PRIMARY（强调色填充），否则透明
                if (isSelected()) {
                    g2.setColor(ThemeUtil.PRIMARY);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(),
                            ThemeUtil.ARC_TAG - 1, ThemeUtil.ARC_TAG - 1);
                }

                // 文字颜色
                Color fg = isSelected() ? Color.WHITE : ThemeUtil.TEXT_SECONDARY;
                g2.setColor(fg);
                g2.setFont(getFont());

                // 图标 + 文字水平居中
                java.awt.FontMetrics fm = g2.getFontMetrics();
                String t = getText();
                javax.swing.Icon icon = getIcon();
                int iconW = (icon != null) ? icon.getIconWidth() : 0;
                int gap = (icon != null && t != null && !t.isEmpty()) ? 4 : 0;
                int totalW = iconW + gap + ((t != null && fm != null) ? fm.stringWidth(t) : 0);
                int startX = (getWidth() - totalW) / 2;

                // 绘制图标
                if (icon != null) {
                    int iconY = (getHeight() - icon.getIconHeight()) / 2;
                    icon.paintIcon(this, g2, startX, iconY);
                    startX += iconW + gap;
                }

                // 绘制文字
                if (t != null && fm != null) {
                    int textY = (getHeight() + fm.getAscent()) / 2 - 1;
                    g2.drawString(t, startX, textY);
                }

                g2.dispose();
            }
        };
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFont(ThemeUtil.FONT_SMALL);
        btn.setFocusPainted(false);
        // 固定宽度：图标 16 + 间距 6 + 文字约 28 + 内边距 24 ≈ 74px
        btn.setPreferredSize(new Dimension(76, 30));
        btn.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        return btn;
    }

    /**
     * 初始化中心面板：三区卡片化布局 + 图片/视频模式切换。
     * 每个功能区是独立卡片（12px 圆角 + 3 层柔阴影 + 16px 间距）。
     */
    private void initPanels() {
        // 左侧卡片：文件列表
        fileListPanel = new FileListPanel();
        JPanel leftCard = new CardWrapper(fileListPanel);

        // ========== 图片模式右侧面板 ==========
        previewPanel = new PreviewPanel();
        JPanel previewCard = new CardWrapper(previewPanel);
        previewCard.setMinimumSize(new Dimension(0, 60));

        paramPanel = new ParamPanel();
        JPanel paramCard = new CardWrapper(paramPanel);

        // 参数面板包滚动条：压缩到很小时可滚动，不丢失内容
        JScrollPane imgParamScroll = new JScrollPane(paramCard);
        imgParamScroll.setMinimumSize(new Dimension(0, 60));
        imgParamScroll.setBorder(BorderFactory.createEmptyBorder());
        imgParamScroll.setOpaque(false);
        imgParamScroll.getViewport().setOpaque(false);
        imgParamScroll.getVerticalScrollBar().setUnitIncrement(16);

        // 垂直可拖拽分割：预览(上) | 参数(下)
        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                previewCard, imgParamScroll);
        rightSplit.setDividerLocation(380);
        rightSplit.setResizeWeight(0.65);
        rightSplit.setDividerSize(5);
        rightSplit.setBorder(BorderFactory.createEmptyBorder());
        rightSplit.setOpaque(false);

        imageRightPanel = new JPanel(new BorderLayout());
        imageRightPanel.setOpaque(false);
        imageRightPanel.add(rightSplit, BorderLayout.CENTER);

        // ========== 视频模式右侧面板 ==========
        videoPreviewPanel = new VideoPreviewPanel();
        JPanel videoPreviewCard = new CardWrapper(videoPreviewPanel);
        videoPreviewCard.setMinimumSize(new Dimension(0, 60));

        videoParamPanel = new VideoParamPanel();
        JPanel videoParamCard = new CardWrapper(videoParamPanel);

        // 参数面板包滚动条：压缩到很小时可滚动，不丢失内容
        JScrollPane vidParamScroll = new JScrollPane(videoParamCard);
        vidParamScroll.setMinimumSize(new Dimension(0, 60));
        vidParamScroll.setBorder(BorderFactory.createEmptyBorder());
        vidParamScroll.setOpaque(false);
        vidParamScroll.getViewport().setOpaque(false);
        vidParamScroll.getVerticalScrollBar().setUnitIncrement(16);

        // 垂直可拖拽分割：视频预览(上) | 参数(下)
        JSplitPane videoRightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                videoPreviewCard, vidParamScroll);
        videoRightSplit.setDividerLocation(400);
        videoRightSplit.setResizeWeight(0.60);
        videoRightSplit.setDividerSize(5);
        videoRightSplit.setBorder(BorderFactory.createEmptyBorder());
        videoRightSplit.setOpaque(false);

        videoRightPanel = new JPanel(new BorderLayout());
        videoRightPanel.setOpaque(false);
        videoRightPanel.add(videoRightSplit, BorderLayout.CENTER);

        // ========== CardLayout 切换图片/视频右侧面板 ==========
        rightCardLayout = new CardLayout();
        rightCardPanel = new JPanel(rightCardLayout);
        rightCardPanel.setOpaque(false);
        rightCardPanel.add(imageRightPanel, CARD_IMAGE);
        rightCardPanel.add(videoRightPanel, CARD_VIDEO);

        // 默认显示图片模式
        rightCardLayout.show(rightCardPanel, CARD_IMAGE);

        // ========== 水平分割：左卡片 | 右面板（16px 窗口内边距） ==========
        mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftCard, rightCardPanel);
        mainSplitPane.setDividerLocation(380);
        mainSplitPane.setResizeWeight(0.36);
        mainSplitPane.setBorder(BorderFactory.createEmptyBorder(
                ThemeUtil.SPACE_BLOCK, ThemeUtil.SPACE_BLOCK,
                ThemeUtil.SPACE_BLOCK, ThemeUtil.SPACE_BLOCK));
        mainSplitPane.setOpaque(false);

        add(mainSplitPane, BorderLayout.CENTER);
    }

    private void initStatusBar() {
        statusBar = new StatusBar();
        add(statusBar, BorderLayout.SOUTH);
    }

    private void centerOnScreen() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int x = (screen.width - DEFAULT_WIDTH) / 2;
        int y = (screen.height - DEFAULT_HEIGHT) / 2;
        setLocation(x, y);
    }

    // ==================== 模式切换 ====================

    /**
     * 切换到指定压缩模式（IMAGE 或 VIDEO）。
     *
     * @param mode "IMAGE" 或 "VIDEO"
     */
    public void switchCompressMode(String mode) {
        if ("VIDEO".equals(mode)) {
            rightCardLayout.show(rightCardPanel, CARD_VIDEO);
            videoModeBtn.setSelected(true);
            setTitle("NCHU Compressor — 视频压缩");
        } else {
            rightCardLayout.show(rightCardPanel, CARD_IMAGE);
            imageModeBtn.setSelected(true);
            setTitle("NCHU Compressor — 图片压缩");
        }
    }

    /**
     * 获取当前是否为视频模式。
     */
    public boolean isVideoMode() {
        return videoModeBtn != null && videoModeBtn.isSelected();
    }

    /**
     * 更新主题按钮文字（主题切换时由 Controller 调用）。
     *
     * @param theme 新主题（用于获取显示名称）
     */
    public void updateThemeButtonText(Theme theme) {
        themeBtn.setText(theme.getDisplayName());
    }

    // ==================== Getter（API 兼容） ====================

    public JMenuItem getImportFilesItem() { return importFilesItem; }
    public JMenuItem getImportFolderItem() { return importFolderItem; }
    public JMenuItem getExitItem() { return exitItem; }
    public JMenuItem getClearAllItem() { return clearAllItem; }
    public JMenuItem getSettingsItem() { return settingsItem; }
    public JMenuItem getToggleThemeItem() { return toggleThemeItem; }
    public JMenuItem getAboutItem() { return aboutItem; }

    public JButton getImportBtn() { return importBtn; }
    public JButton getImportFolderBtn() { return importFolderBtn; }
    public JButton getClearBtn() { return clearBtn; }
    public JButton getThemeBtn() { return themeBtn; }
    public JToggleButton getImageModeBtn() { return imageModeBtn; }
    public JToggleButton getVideoModeBtn() { return videoModeBtn; }

    public FileListPanel getFileListPanel() { return fileListPanel; }
    public PreviewPanel getPreviewPanel() { return previewPanel; }
    public ParamPanel getParamPanel() { return paramPanel; }
    public VideoPreviewPanel getVideoPreviewPanel() { return videoPreviewPanel; }
    public VideoParamPanel getVideoParamPanel() { return videoParamPanel; }
    public StatusBar getStatusBar() { return statusBar; }
    public JSplitPane getMainSplitPane() { return mainSplitPane; }
    public JSplitPane getRightSplitPane() { return rightSplitPane; }
}
