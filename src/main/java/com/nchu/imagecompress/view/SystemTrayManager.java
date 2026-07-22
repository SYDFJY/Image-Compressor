package com.nchu.imagecompress.view;

import com.nchu.imagecompress.util.LogUtil;

import java.awt.AWTException;
import java.awt.Frame;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;

/**
 * 系统托盘管理器 — 最小化到托盘、气泡通知、右键菜单。
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-22
 */
public class SystemTrayManager {

    private final Frame mainFrame;
    private final Runnable onRestore;
    private final Runnable onExit;

    private TrayIcon trayIcon;
    private boolean initialized = false;

    public SystemTrayManager(Frame mainFrame, Runnable onRestore, Runnable onExit) {
        this.mainFrame = mainFrame;
        this.onRestore = onRestore;
        this.onExit = onExit;
    }

    /**
     * 初始化系统托盘图标（创建但不显示）。
     *
     * @return true = 托盘可用，false = 系统不支持
     */
    public boolean initialize() {
        if (!SystemTray.isSupported()) {
            LogUtil.info("[SystemTrayManager] 系统托盘不可用");
            return false;
        }

        try {
            // 生成 16x16 程序图标
            Image icon = createTrayIconImage();

            PopupMenu popup = new PopupMenu();

            MenuItem restoreItem = new MenuItem("恢复窗口");
            restoreItem.addActionListener(e -> restoreFromTray());
            popup.add(restoreItem);

            popup.addSeparator();

            MenuItem exitItem = new MenuItem("退出");
            exitItem.addActionListener(e -> {
                remove();
                if (onExit != null) onExit.run();
            });
            popup.add(exitItem);

            trayIcon = new TrayIcon(icon, "NCHU Compressor", popup);
            trayIcon.setImageAutoSize(true);

            // 双击恢复
            trayIcon.addActionListener(e -> restoreFromTray());

            initialized = true;
            LogUtil.info("[SystemTrayManager] 托盘初始化完成");
            return true;

        } catch (Exception e) {
            LogUtil.info("[SystemTrayManager] 托盘初始化失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 最小化主窗口到系统托盘。
     */
    public void minimizeToTray() {
        if (!initialized || trayIcon == null) return;

        try {
            SystemTray.getSystemTray().add(trayIcon);
            mainFrame.setVisible(false);

            // 首次显示气泡提示
            trayIcon.displayMessage("NCHU Compressor",
                    "应用正在后台运行，双击图标恢复窗口",
                    TrayIcon.MessageType.INFO);

            LogUtil.info("[SystemTrayManager] 已最小化到托盘");
        } catch (AWTException e) {
            LogUtil.info("[SystemTrayManager] 无法添加到托盘: " + e.getMessage());
        }
    }

    /**
     * 从系统托盘恢复主窗口。
     */
    public void restoreFromTray() {
        if (!initialized) return;

        SystemTray.getSystemTray().remove(trayIcon);
        mainFrame.setVisible(true);
        mainFrame.setState(Frame.NORMAL);
        mainFrame.toFront();

        if (onRestore != null) onRestore.run();
        LogUtil.info("[SystemTrayManager] 已恢复窗口");
    }

    /**
     * 显示气泡通知。
     */
    public void showNotification(String title, String message, TrayIcon.MessageType type) {
        if (trayIcon != null) {
            trayIcon.displayMessage(title, message, type);
        }
    }

    /**
     * 从系统托盘中移除图标。
     */
    public void remove() {
        if (initialized && trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
    }

    /**
     * 生成简单的 16x16 程序图标（纯色方块 + "C" 字母）。
     */
    private static Image createTrayIconImage() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

        // 圆角矩形背景
        g2.setColor(new java.awt.Color(64, 158, 255)); // #409EFF
        g2.fillRoundRect(0, 0, 15, 15, 4, 4);

        // "C" 文字
        g2.setColor(java.awt.Color.WHITE);
        g2.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 10));
        java.awt.FontMetrics fm = g2.getFontMetrics();
        String text = "C";
        int tw = fm.stringWidth(text);
        g2.drawString(text, (16 - tw) / 2, 12);

        g2.dispose();
        return img;
    }
}
