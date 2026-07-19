package com.nchu.imagecompress.view;

import com.nchu.imagecompress.util.ThemeUtil;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.RoundRectangle2D;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Toast 轻量通知组件。
 *
 * <p>从屏幕右下角弹出非模态通知，3 秒后自动消失。支持四种类型：
 * <ul>
 *   <li>{@code SUCCESS} — 绿色 #67C23A，操作成功</li>
 *   <li>{@code ERROR}   — 红色 #F56C6C，操作失败</li>
 *   <li>{@code INFO}    — 蓝色 #409EFF，一般信息</li>
 *   <li>{@code WARNING} — 橙色 #E6A23C，警告提示</li>
 * </ul>
 *
 * <p><b>特性：</b>
 * <ol>
 *   <li>JWindow 无边框 + 圆角遮罩 + 半透明背景</li>
 *   <li>滑入动画（从下方滑入）+ 淡出动画</li>
 *   <li>内置消息队列：多条 Toast 会排队依次显示，避免重叠</li>
 *   <li>点击 Toast 可提前关闭</li>
 * </ol>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-08
 */
public class ToastNotification {

    // ==================== 类型枚举 ====================

    /** Toast 类型 */
    public enum Type {
        SUCCESS("✅", ThemeUtil.SUCCESS),
        ERROR("❌", ThemeUtil.ERROR),
        INFO("ℹ️", ThemeUtil.PRIMARY),
        WARNING("⚠️", ThemeUtil.WARNING);

        final String icon;
        final Color color;

        Type(String icon, Color color) {
            this.icon = icon;
            this.color = color;
        }
    }

    // ==================== 常量 ====================

    /** Toast 宽度 */
    private static final int TOAST_WIDTH = 320;

    /** Toast 高度 */
    private static final int TOAST_HEIGHT = 56;

    /** 距离屏幕右边缘 */
    private static final int MARGIN_RIGHT = 20;

    /** 距离屏幕下边缘 */
    private static final int MARGIN_BOTTOM = 40;

    /** 自动消失延迟（毫秒） */
    private static final int AUTO_DISMISS_MS = 3000;

    /** 动画帧间隔 */
    private static final int ANIM_INTERVAL = 16;

    /** 动画时长 */
    private static final int ANIM_DURATION = 200;

    // ==================== 消息队列 ====================

    /** 全局 Toast 消息队列（先进先出） */
    private static final Queue<ToastItem> queue = new LinkedList<>();

    /** 当前是否有 Toast 正在显示 */
    private static boolean isShowing = false;

    // ==================== 公开方法 ====================

    /**
     * 显示一条成功 Toast。
     *
     * @param message 消息文本
     */
    public static void success(String message) {
        show(message, Type.SUCCESS);
    }

    /**
     * 显示一条错误 Toast。
     *
     * @param message 消息文本
     */
    public static void error(String message) {
        show(message, Type.ERROR);
    }

    /**
     * 显示一条信息 Toast。
     *
     * @param message 消息文本
     */
    public static void info(String message) {
        show(message, Type.INFO);
    }

    /**
     * 显示一条警告 Toast。
     *
     * @param message 消息文本
     */
    public static void warning(String message) {
        show(message, Type.WARNING);
    }

    /**
     * 显示 Toast（入队）。
     *
     * @param message 消息文本
     * @param type    消息类型
     */
    public static void show(String message, Type type) {
        if (message == null || message.isEmpty()) return;

        // 在 EDT 中执行
        javax.swing.SwingUtilities.invokeLater(() -> {
            queue.offer(new ToastItem(message, type));
            processQueue();
        });
    }

    // ==================== 队列处理 ====================

    /**
     * 处理队列中的下一条 Toast。
     */
    private static void processQueue() {
        if (isShowing || queue.isEmpty()) return;

        ToastItem item = queue.poll();
        if (item == null) return;

        isShowing = true;
        createAndShowToast(item);
    }

    /**
     * 当前 Toast 消失后的回调。
     */
    private static void onDismissed() {
        isShowing = false;
        // 短暂延迟后处理下一条
        Timer delayTimer = new Timer(150, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ((Timer) e.getSource()).stop();
                processQueue();
            }
        });
        delayTimer.setRepeats(false);
        delayTimer.start();
    }

    // ==================== 窗口创建与动画 ====================

    /**
     * 创建并显示单个 Toast 窗口。
     */
    private static void createAndShowToast(ToastItem item) {
        // 计算屏幕右下角位置
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice();
        int screenWidth = gd.getDisplayMode().getWidth();
        // 获取任务栏高度（近似值）
        int taskbarHeight = Toolkit.getDefaultToolkit().getScreenInsets(
                GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getDefaultScreenDevice().getDefaultConfiguration()).bottom;

        int targetX = screenWidth - TOAST_WIDTH - MARGIN_RIGHT;
        int targetY = gd.getDisplayMode().getHeight() - taskbarHeight
                - TOAST_HEIGHT - MARGIN_BOTTOM;

        // 创建无边框窗口
        final JWindow window = new JWindow();
        window.setAlwaysOnTop(true);
        window.setSize(TOAST_WIDTH, TOAST_HEIGHT);

        // JDK 7+ 支持窗口透明度
        try {
            window.setOpacity(0.95f);
        } catch (Exception ignored) {
            // 某些平台可能不支持，静默忽略
        }

        // 内置圆角面板
        ToastPanel panel = new ToastPanel(item);
        window.add(panel);
        window.pack();
        window.setSize(TOAST_WIDTH, TOAST_HEIGHT);

        // 点击关闭
        panel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                dismissToast(window, targetX, targetY);
            }
        });

        // === 滑入动画 ===
        int startY = targetY + TOAST_HEIGHT + 20;
        window.setLocation(targetX, startY);
        window.setVisible(true);

        final int totalSteps = Math.max(1, ANIM_DURATION / ANIM_INTERVAL);
        final int[] step = {0};

        Timer slideTimer = new Timer(ANIM_INTERVAL, null);
        slideTimer.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                step[0]++;
                double progress = (double) step[0] / totalSteps;
                double eased = 1.0 - Math.pow(1.0 - Math.min(progress, 1.0), 3); // ease-out cubic

                int currentY = startY + (int) ((targetY - startY) * eased);
                window.setLocation(targetX, currentY);

                if (progress >= 1.0) {
                    slideTimer.stop();
                    window.setLocation(targetX, targetY);
                    // 启动自动消失计时
                    startAutoDismiss(window, targetX, targetY);
                }
            }
        });
        slideTimer.setRepeats(true);
        slideTimer.start();
    }

    /**
     * 启动自动消失计时器。
     */
    private static void startAutoDismiss(final JWindow window,
                                          final int x, final int y) {
        Timer dismissTimer = new Timer(AUTO_DISMISS_MS, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ((Timer) e.getSource()).stop();
                dismissToast(window, x, y);
            }
        });
        dismissTimer.setRepeats(false);
        dismissTimer.start();
    }

    /**
     * 淡出并销毁 Toast。
     */
    private static void dismissToast(final JWindow window, int x, int y) {
        final int totalSteps = Math.max(1, ANIM_DURATION / ANIM_INTERVAL);
        final int[] step = {0};

        Timer fadeTimer = new Timer(ANIM_INTERVAL, null);
        fadeTimer.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                step[0]++;
                double progress = (double) step[0] / totalSteps;
                float opacity = (float) Math.max(0.0, 1.0 - progress);

                try {
                    window.setOpacity(opacity);
                } catch (Exception ignored) {
                }

                if (progress >= 1.0) {
                    fadeTimer.stop();
                    window.dispose();
                    onDismissed();
                }
            }
        });
        fadeTimer.setRepeats(true);
        fadeTimer.start();
    }

    // ==================== 内部类 ====================

    /**
     * 消息队列条目。
     */
    private static class ToastItem {
        final String message;
        final Type type;

        ToastItem(String message, Type type) {
            this.message = message;
            this.type = type;
        }
    }

    /**
     * Toast 内容面板（圆角+彩色边框）。
     */
    private static class ToastPanel extends JPanel {

        private final ToastItem item;

        ToastPanel(ToastItem item) {
            this.item = item;
            setLayout(new BorderLayout(12, 0));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

            // 图标
            JLabel iconLabel = new JLabel(item.type.icon);
            iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
            add(iconLabel, BorderLayout.WEST);

            // 消息文字
            JLabel msgLabel = new JLabel(item.message);
            msgLabel.setFont(ThemeUtil.FONT_BODY);
            ThemeUtil.setDynamicForeground(msgLabel, () -> ThemeUtil.TEXT_PRIMARY);
            add(msgLabel, BorderLayout.CENTER);

            // 关闭提示
            JLabel closeHint = new JLabel("×");
            closeHint.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
            closeHint.setForeground(new Color(0xC0C4CC));
            add(closeHint, BorderLayout.EAST);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // 背景（白色 + 轻微阴影模拟）
            g2.setColor(new Color(0, 0, 0, 40));
            g2.fill(new RoundRectangle2D.Double(2, 3, w - 4, h - 4, 12, 12));

            g2.setColor(Color.WHITE);
            g2.fill(new RoundRectangle2D.Double(0, 0, w - 2, h - 3, 12, 12));

            // 左侧彩色指示条
            g2.setColor(item.type.color);
            g2.fill(new RoundRectangle2D.Double(0, 6, 4, h - 12, 4, 4));

            g2.dispose();
            super.paintComponent(g);
        }
    }
}
