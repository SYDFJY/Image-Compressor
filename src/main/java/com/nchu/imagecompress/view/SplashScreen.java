package com.nchu.imagecompress.view;

import com.nchu.imagecompress.util.ThemeUtil;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;

/**
 * 应用启动画面。
 *
 * <p>在应用初始化期间显示品牌标识和加载进度条。
 * 使用 JWindow（无边框窗口）实现，初始化完成后自动关闭。</p>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-08
 */
public class SplashScreen extends JWindow {

    /** 启动画面宽度 */
    private static final int WIDTH = 420;

    /** 启动画面高度 */
    private static final int HEIGHT = 280;

    /** 进度条 */
    private final JProgressBar progressBar;

    /** 状态文字 */
    private final JLabel statusLabel;

    /**
     * 构造启动画面并居中显示。
     */
    public SplashScreen() {
        // 主面板
        JPanel panel = new JPanel(new BorderLayout(20, 20));
        panel.setBackground(ThemeUtil.PRIMARY);
        panel.setBorder(BorderFactory.createEmptyBorder(30, 40, 30, 40));

        // === 顶部：应用名称 ===
        JLabel titleLabel = new JLabel("NCHU Image Compressor", SwingConstants.CENTER);
        titleLabel.setFont(safeFont(ThemeUtil.FONT_TITLE, 22f));
        titleLabel.setForeground(Color.WHITE);
        panel.add(titleLabel, BorderLayout.NORTH);

        // === 中部：版本号 ===
        JLabel versionLabel = new JLabel("v1.0.0 — 南昌航空大学软件工程实训项目", SwingConstants.CENTER);
        versionLabel.setFont(safeFont(ThemeUtil.FONT_SMALL, 12f));
        versionLabel.setForeground(new Color(255, 255, 255, 200));
        panel.add(versionLabel, BorderLayout.CENTER);

        // === 底部：进度条 + 状态 ===
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 8));
        bottomPanel.setOpaque(false);

        progressBar = new JProgressBar(0, 100);
        progressBar.setPreferredSize(new Dimension(WIDTH - 80, 6));
        progressBar.setStringPainted(false);
        progressBar.setForeground(Color.WHITE);
        progressBar.setBackground(new Color(255, 255, 255, 60));
        bottomPanel.add(progressBar, BorderLayout.NORTH);

        statusLabel = new JLabel("正在初始化...", SwingConstants.LEFT);
        statusLabel.setFont(safeFont(ThemeUtil.FONT_TINY, 10f));
        statusLabel.setForeground(new Color(255, 255, 255, 180));
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);

        panel.add(bottomPanel, BorderLayout.SOUTH);

        // 兜底：点击启动画面可手动关闭
        panel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                dispose();
            }
        });

        setContentPane(panel);
        setSize(WIDTH, HEIGHT);
        centerOnScreen();
    }

    /**
     * 更新进度条和状态文字。
     *
     * @param progress 进度值 (0-100)
     * @param message  状态描述
     */
    public void updateProgress(int progress, String message) {
        progressBar.setValue(progress);
        statusLabel.setText(message);
    }

    /**
     * 将启动画面居中于屏幕。
     */
    private void centerOnScreen() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int x = (screen.width - WIDTH) / 2;
        int y = (screen.height - HEIGHT) / 2;
        setLocation(x, y);
    }

    /**
     * 安全获取字体 — 如主题未加载则使用系统默认字体回退。
     *
     * @param themeFont 主题字体（可能为 null）
     * @param size      字号
     * @return 非 null 的 Font
     */
    private static Font safeFont(Font themeFont, float size) {
        if (themeFont != null) return themeFont.deriveFont(size);
        return new Font("Microsoft YaHei", Font.PLAIN, (int) size);
    }
}
