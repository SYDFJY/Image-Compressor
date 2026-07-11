package com.nchu.imagecompress.view;

import com.nchu.imagecompress.util.ThemeUtil;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
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
    private JButton compressBtn;
    private JButton themeBtn;

    // ==================== 面板引用 ====================
    private FileListPanel fileListPanel;
    private PreviewPanel previewPanel;
    private ParamPanel paramPanel;
    private StatusBar statusBar;
    private JSplitPane mainSplitPane;
    private JSplitPane rightSplitPane;

    public MainFrame() {
        initFrame();
        initMenuBar();
        initToolBar();
        initPanels();
        initStatusBar();
    }

    private void initFrame() {
        setTitle("NCHU Image Compressor");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));
        // 窗口背景：极浅冷灰
        getContentPane().setBackground(ThemeUtil.BG_WINDOW);
        setLayout(new BorderLayout(0, 0));
        centerOnScreen();
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
        toolBarPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeUtil.BORDER),
                BorderFactory.createEmptyBorder(12, 20, 12, 20)));

        // --- 左侧：品牌 ---
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftPanel.setOpaque(false);

        JLabel logoLabel = new JLabel("🖼");
        logoLabel.setFont(new java.awt.Font("Segoe UI Emoji", java.awt.Font.PLAIN, 20));
        leftPanel.add(logoLabel);

        JLabel nameLabel = new JLabel("NCHU Image Compressor");
        nameLabel.setFont(ThemeUtil.FONT_TITLE);
        nameLabel.setForeground(ThemeUtil.TEXT_PRIMARY);
        leftPanel.add(nameLabel);

        JLabel subLabel = new JLabel("图片压缩工具");
        subLabel.setFont(ThemeUtil.FONT_SMALL);
        subLabel.setForeground(ThemeUtil.TEXT_TERTIARY);
        leftPanel.add(subLabel);

        toolBarPanel.add(leftPanel, BorderLayout.WEST);

        // --- 中间：操作按钮组（边框样式） ---
        JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        centerPanel.setOpaque(false);

        importBtn = createBorderedButton("📂  导入文件");
        importBtn.setToolTipText("导入图片文件 (Ctrl+O)");
        centerPanel.add(importBtn);

        importFolderBtn = createBorderedButton("📁  导入文件夹");
        importFolderBtn.setToolTipText("导入文件夹中的所有图片 (Ctrl+Shift+O)");
        centerPanel.add(importFolderBtn);

        clearBtn = createBorderedButton("🗑  清空列表");
        clearBtn.setToolTipText("清空文件列表 (Ctrl+Delete)");
        centerPanel.add(clearBtn);

        toolBarPanel.add(centerPanel, BorderLayout.CENTER);

        // --- 右侧：功能图标 + 主按钮 ---
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightPanel.setOpaque(false);

        themeBtn = new JButton("🎨");
        themeBtn.setFont(new java.awt.Font("Segoe UI Emoji", java.awt.Font.PLAIN, 16));
        themeBtn.setToolTipText("切换主题");
        themeBtn.setFocusPainted(false);
        themeBtn.setBackground(ThemeUtil.BG_CARD);
        themeBtn.setForeground(ThemeUtil.TEXT_SECONDARY);
        themeBtn.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        themeBtn.setPreferredSize(new java.awt.Dimension(32, 32));
        themeBtn.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        rightPanel.add(themeBtn);

        compressBtn = new JButton("▶  开始压缩");
        compressBtn.setFont(new java.awt.Font("Microsoft YaHei", java.awt.Font.BOLD, 14));
        compressBtn.setFocusPainted(false);
        compressBtn.setBackground(ThemeUtil.PRIMARY);
        compressBtn.setForeground(Color.WHITE);
        compressBtn.setBorder(BorderFactory.createEmptyBorder(10, 24, 10, 24));
        compressBtn.setPreferredSize(new Dimension(140, 38));
        compressBtn.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        rightPanel.add(compressBtn);

        toolBarPanel.add(rightPanel, BorderLayout.EAST);

        add(toolBarPanel, BorderLayout.NORTH);
    }

    /** 创建边框样式次要按钮（1px 边框 + 8px 圆角） */
    private JButton createBorderedButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(ThemeUtil.FONT_BODY);
        btn.setFocusPainted(false);
        btn.setBackground(ThemeUtil.BG_CARD);
        btn.setForeground(ThemeUtil.TEXT_SECONDARY);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeUtil.BORDER, 1),
                BorderFactory.createEmptyBorder(5, 14, 5, 14)));
        return btn;
    }

    /**
     * 初始化中心面板：三区卡片化布局。
     * 每个功能区是独立卡片（12px 圆角 + 3 层柔阴影 + 16px 间距）。
     */
    private void initPanels() {
        // 左侧卡片：文件列表
        fileListPanel = new FileListPanel();
        JPanel leftCard = new CardWrapper(fileListPanel);

        // 右侧：预览（独立卡片）
        previewPanel = new PreviewPanel();
        JPanel previewCard = new CardWrapper(previewPanel);

        // 右侧：参数设置（独立卡片）
        paramPanel = new ParamPanel();
        JPanel paramCard = new CardWrapper(paramPanel);

        // 右侧垂直排列：预览卡片 + 间距 + 参数卡片
        JPanel rightPanel = new JPanel(new BorderLayout(0, ThemeUtil.SPACE_LG));
        rightPanel.setOpaque(false);
        rightPanel.add(previewCard, BorderLayout.CENTER);
        rightPanel.add(paramCard, BorderLayout.SOUTH);

        // 水平分割：左卡片 | 右面板（窗口 20px 内边距）
        mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftCard, rightPanel);
        mainSplitPane.setDividerLocation(380);
        mainSplitPane.setResizeWeight(0.36);
        mainSplitPane.setBorder(BorderFactory.createEmptyBorder(
                ThemeUtil.SPACE_XL, ThemeUtil.SPACE_XL, ThemeUtil.SPACE_XL, ThemeUtil.SPACE_XL));
        mainSplitPane.setOpaque(false);

        add(mainSplitPane, BorderLayout.CENTER);
    }

    /**
     * 卡片包装面板 — 3 层柔和阴影 + 白色卡片背景 + 12px 圆角。
     *
     * <p>阴影层次（从外到内）：
     * <ol>
     *   <li>4px 偏移, 6% 透明度 — 最外层扩散阴影</li>
     *   <li>2px 偏移, 4% 透明度 — 中间层</li>
     *   <li>0px 偏移, 白色卡片背景 — 最上层</li>
     * </ol>
     *
     * <p>右侧预留 4px、底部预留 6px 给阴影。</p>
     */
    private static class CardWrapper extends JPanel {
        private final int arc = ThemeUtil.ARC_CARD;

        CardWrapper(JPanel content) {
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

            // 第 3 层：最远扩散阴影（4px 偏移, 6% alpha）
            g2.setColor(new Color(0, 0, 0, 15));
            g2.fillRoundRect(4, 4, w - 4, h - 4, arc, arc);

            // 第 2 层：中间阴影（2px 偏移, 4% alpha）
            g2.setColor(new Color(0, 0, 0, 10));
            g2.fillRoundRect(2, 2, w - 2, h - 2, arc, arc);

            // 第 1 层：卡片主体（白色背景）
            g2.setColor(ThemeUtil.BG_CARD);
            g2.fillRoundRect(0, 0, w, h, arc, arc);

            g2.dispose();
        }
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
    public JButton getCompressBtn() { return compressBtn; }
    public JButton getThemeBtn() { return themeBtn; }

    public FileListPanel getFileListPanel() { return fileListPanel; }
    public PreviewPanel getPreviewPanel() { return previewPanel; }
    public ParamPanel getParamPanel() { return paramPanel; }
    public StatusBar getStatusBar() { return statusBar; }
    public JSplitPane getMainSplitPane() { return mainSplitPane; }
    public JSplitPane getRightSplitPane() { return rightSplitPane; }
}
