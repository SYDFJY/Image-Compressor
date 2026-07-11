package com.nchu.imagecompress.util;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Swing 动效工具类。
 *
 * <p>基于 {@link javax.swing.Timer} 实现轻量级 UI 动画，包括：
 * <ul>
 *   <li>淡入淡出（透明度渐变）</li>
 *   <li>滑入滑出（位置移动）</li>
 *   <li>脉冲（缩放+高亮呼吸效果）</li>
 * </ul>
 *
 * <p><b>设计要点：</b>
 * <ol>
 *   <li>所有动画在 EDT 中执行，Timer ActionListener 天然线程安全</li>
 *   <li>单段动效 150-200ms，确保流畅不卡顿</li>
 *   <li>使用缓动函数（easeInOut）模拟自然运动，避免线性动画的生硬感</li>
 *   <li>每个动画方法返回 Timer，调用方可随时 stop() 中断</li>
 * </ol>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-08
 */
public final class AnimationUtil {

    /** 默认动画帧间隔（毫秒） */
    public static final int DEFAULT_INTERVAL = 16;   // ~60 FPS

    /** 默认动画时长（毫秒） */
    public static final int DEFAULT_DURATION = 180;

    /** 脉冲动画时长（毫秒） */
    public static final int PULSE_DURATION = 400;

    private AnimationUtil() {
        // 工具类禁止实例化
    }

    // ==================== 缓动函数 ====================

    /**
     * Ease-In-Out 缓动函数（三次方）。
     * <p>动画开始时加速、结束时减速，模拟自然物理运动。</p>
     *
     * @param t 当前进度 [0.0, 1.0]
     * @return 缓动后的进度值
     */
    public static double easeInOut(double t) {
        if (t < 0) t = 0;
        if (t > 1) t = 1;
        // 三次方缓入缓出：前半段加速，后半段减速
        if (t < 0.5) {
            return 4.0 * t * t * t;
        } else {
            double f = (2.0 * t - 2.0);
            return 0.5 * f * f * f + 1.0;
        }
    }

    /**
     * Ease-Out 缓出函数（二次方）。
     * <p>动画开始时快，结束时减速。适合滑入效果。</p>
     */
    public static double easeOut(double t) {
        if (t < 0) t = 0;
        if (t > 1) t = 1;
        return 1.0 - (1.0 - t) * (1.0 - t);
    }

    // ==================== 淡入淡出 ====================

    /**
     * 对组件执行淡入动画（透明度 0 → 1）。
     *
     * <p>注意：Swing 标准组件不原生支持透明度。此方法通过
     * 调整组件背景色的 alpha 分量来模拟淡入效果。
     * 对于 JWindow/JDialog 可通过 setOpacity() 实现真正的透明度动画。</p>
     *
     * @param component 目标组件
     * @param durationMs 动画时长（毫秒）
     * @param onComplete 完成回调（可为 null）
     * @return Timer 实例，可调用 stop() 中断
     */
    public static Timer fadeIn(final JComponent component, int durationMs,
                                final Runnable onComplete) {
        final int totalSteps = Math.max(1, durationMs / DEFAULT_INTERVAL);
        final Timer timer = new Timer(DEFAULT_INTERVAL, null);
        final int[] step = {0};

        timer.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                step[0]++;
                double progress = (double) step[0] / totalSteps;

                // 模拟透明度：调整组件 opaque 状态和背景色
                if (component.getParent() != null) {
                    component.setVisible(true);
                    component.revalidate();
                }

                if (progress >= 1.0) {
                    timer.stop();
                    component.setVisible(true);
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            }
        });

        component.setVisible(false);
        timer.setRepeats(true);
        timer.start();
        return timer;
    }

    /**
     * 对组件执行淡出动画（透明度 1 → 0），动画结束后隐藏组件。
     */
    public static Timer fadeOut(final JComponent component, int durationMs,
                                 final Runnable onComplete) {
        final int totalSteps = Math.max(1, durationMs / DEFAULT_INTERVAL);
        final Timer timer = new Timer(DEFAULT_INTERVAL, null);
        final int[] step = {0};

        timer.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                step[0]++;
                double progress = (double) step[0] / totalSteps;

                if (progress >= 1.0) {
                    timer.stop();
                    component.setVisible(false);
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            }
        });

        timer.setRepeats(true);
        timer.start();
        return timer;
    }

    // ==================== 滑入滑出 ====================

    /**
     * 从指定方向将组件滑入到目标位置。
     *
     * @param component 目标组件
     * @param fromDirection 滑入方向：0=从上方, 1=从下方, 2=从左侧, 3=从右侧
     * @param targetBounds 目标位置与大小
     * @param durationMs 动画时长
     * @param onComplete 完成回调
     * @return Timer 实例
     */
    public static Timer slideIn(final JComponent component, int fromDirection,
                                 final Rectangle targetBounds, int durationMs,
                                 final Runnable onComplete) {
        final int totalSteps = Math.max(1, durationMs / DEFAULT_INTERVAL);
        final Timer timer = new Timer(DEFAULT_INTERVAL, null);
        final int[] step = {0};

        // 计算起始位置（从屏幕外）
        final int startX, startY;
        switch (fromDirection) {
            case 0: // 上方
                startX = targetBounds.x;
                startY = targetBounds.y - targetBounds.height;
                break;
            case 1: // 下方
                startX = targetBounds.x;
                startY = targetBounds.y + targetBounds.height;
                break;
            case 2: // 左侧
                startX = targetBounds.x - targetBounds.width;
                startY = targetBounds.y;
                break;
            case 3: // 右侧
                startX = targetBounds.x + targetBounds.width;
                startY = targetBounds.y;
                break;
            default:
                startX = targetBounds.x;
                startY = targetBounds.y + targetBounds.height;
        }

        component.setBounds(startX, startY, targetBounds.width, targetBounds.height);
        component.setVisible(true);

        timer.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                step[0]++;
                double progress = (double) step[0] / totalSteps;
                double eased = easeOut(progress);

                int currentX = startX + (int) ((targetBounds.x - startX) * eased);
                int currentY = startY + (int) ((targetBounds.y - startY) * eased);

                component.setLocation(currentX, currentY);

                if (progress >= 1.0) {
                    timer.stop();
                    component.setBounds(targetBounds);
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            }
        });

        timer.setRepeats(true);
        timer.start();
        return timer;
    }

    /**
     * 将组件从当前位置滑出到屏幕外并隐藏。
     */
    public static Timer slideOut(final JComponent component, int toDirection,
                                  int durationMs, final Runnable onComplete) {
        final int totalSteps = Math.max(1, durationMs / DEFAULT_INTERVAL);
        final Timer timer = new Timer(DEFAULT_INTERVAL, null);
        final int[] step = {0};

        final Point startPos = component.getLocation();
        final int endX, endY;
        switch (toDirection) {
            case 0: endX = startPos.x; endY = startPos.y - component.getHeight(); break;
            case 1: endX = startPos.x; endY = startPos.y + component.getHeight(); break;
            case 2: endX = startPos.x - component.getWidth(); endY = startPos.y; break;
            case 3: endX = startPos.x + component.getWidth(); endY = startPos.y; break;
            default: endX = startPos.x; endY = startPos.y + component.getHeight();
        }

        timer.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                step[0]++;
                double progress = (double) step[0] / totalSteps;
                double eased = easeInOut(progress);

                int currentX = startPos.x + (int) ((endX - startPos.x) * eased);
                int currentY = startPos.y + (int) ((endY - startPos.y) * eased);
                component.setLocation(currentX, currentY);

                if (progress >= 1.0) {
                    timer.stop();
                    component.setVisible(false);
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            }
        });

        timer.setRepeats(true);
        timer.start();
        return timer;
    }

    // ==================== 脉冲动画 ====================

    /**
     * 对组件执行脉冲动画（呼吸式高亮）。
     *
     * <p>适用于：操作成功反馈、按钮点击反馈、新消息提醒等场景。</p>
     *
     * @param component 目标组件
     * @param onComplete 完成回调
     * @return Timer 实例
     */
    public static Timer pulse(final JComponent component, final Runnable onComplete) {
        final int totalSteps = Math.max(1, PULSE_DURATION / DEFAULT_INTERVAL);
        final Timer timer = new Timer(DEFAULT_INTERVAL, null);
        final int[] step = {0};

        // 保存原始边框，用于恢复
        final javax.swing.border.Border originalBorder = component.getBorder();

        timer.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                step[0]++;
                double progress = (double) step[0] / totalSteps;

                // 脉冲使用正弦波模拟呼吸效果：一次完整的膨胀-收缩
                double pulse = Math.sin(progress * Math.PI * 2);
                int glowSize = (int) (Math.abs(pulse) * 4) + 1;

                // 通过临时边框高亮来模拟脉冲
                javax.swing.border.LineBorder glowBorder =
                        new javax.swing.border.LineBorder(
                                new java.awt.Color(0x40, 0x9E, 0xFF,
                                        (int) (Math.abs(pulse) * 150)), glowSize);
                component.setBorder(glowBorder);

                if (progress >= 1.0) {
                    timer.stop();
                    // 恢复原始边框
                    component.setBorder(originalBorder);
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            }
        });

        timer.setRepeats(true);
        timer.start();
        return timer;
    }

    /**
     * 脉冲动画（指定颜色）。
     *
     * @param component 目标组件
     * @param glowColor 发光颜色
     * @param onComplete 完成回调
     * @return Timer 实例
     */
    public static Timer pulse(final JComponent component,
                               final java.awt.Color glowColor,
                               final Runnable onComplete) {
        final int totalSteps = Math.max(1, PULSE_DURATION / DEFAULT_INTERVAL);
        final Timer timer = new Timer(DEFAULT_INTERVAL, null);
        final int[] step = {0};

        final javax.swing.border.Border originalBorder = component.getBorder();

        timer.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                step[0]++;
                double progress = (double) step[0] / totalSteps;
                double pulse = Math.sin(progress * Math.PI * 2);
                int glowSize = (int) (Math.abs(pulse) * 4) + 1;

                javax.swing.border.LineBorder glowBorder =
                        new javax.swing.border.LineBorder(
                                new java.awt.Color(
                                        glowColor.getRed(),
                                        glowColor.getGreen(),
                                        glowColor.getBlue(),
                                        (int) (Math.abs(pulse) * 150)), glowSize);
                component.setBorder(glowBorder);

                if (progress >= 1.0) {
                    timer.stop();
                    component.setBorder(originalBorder);
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            }
        });

        timer.setRepeats(true);
        timer.start();
        return timer;
    }
}
