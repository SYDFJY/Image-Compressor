package com.nchu.imagecompress.view;

import com.nchu.imagecompress.util.ThemeUtil;

import com.nchu.imagecompress.view.widget.ModernProgressBar;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;

/**
 * 底部状态栏（UI 升级版 — 32px 极简设计）。
 *
 * <p>升级要点：
 * <ul>
 *   <li>高度 32px，背景与窗口一致，无顶部边框</li>
 *   <li>左侧：状态图标 + 文字，12px 灰色小字</li>
 *   <li>右侧：文件统计，12px 灰色</li>
 *   <li>进度条嵌入中间，细条样式，平滑增长</li>
 * </ul>
 *
 * @author NCHU-Student
 * @version 2.0.0
 * @since 2026-07-08
 */
public class StatusBar extends JPanel {

    private final JLabel statusIconLabel;
    private final JLabel statusLabel;
    private final JLabel statsLabel;
    private final ModernProgressBar progressBar;

    public StatusBar() {
        setLayout(new BorderLayout(ThemeUtil.SPACE_LG, 0));
        // 顶部：主题色半透明边框线（与卡片 accent 描边风格统一）
        refreshBorder();
        setPreferredSize(new Dimension(0, 36));
        // 使用 BG_CARD 凸起背景，区别于窗口底色
        ThemeUtil.setDynamicBackground(this, () -> ThemeUtil.BG_CARD);

        // === 左侧：状态指示 ===
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, ThemeUtil.SPACE_SM, 0));
        leftPanel.setOpaque(false);

        statusIconLabel = new JLabel("●");
        statusIconLabel.setFont(ThemeUtil.FONT_TINY);
        ThemeUtil.setDynamicForeground(statusIconLabel, () -> ThemeUtil.SUCCESS);
        leftPanel.add(statusIconLabel);

        statusLabel = new JLabel("就绪");
        statusLabel.setFont(ThemeUtil.FONT_SMALL);
        ThemeUtil.setDynamicForeground(statusLabel, () -> ThemeUtil.TEXT_TERTIARY);
        leftPanel.add(statusLabel);

        add(leftPanel, BorderLayout.WEST);

        // === 中间：进度条（ModernProgressBar — 圆角 + 渐变 + 文字叠加） ===
        progressBar = new ModernProgressBar();
        progressBar.setVisible(false);
        add(progressBar, BorderLayout.CENTER);

        // === 右侧：统计信息 ===
        statsLabel = new JLabel("", SwingConstants.RIGHT);
        statsLabel.setFont(ThemeUtil.FONT_SMALL);
        ThemeUtil.setDynamicForeground(statsLabel, () -> ThemeUtil.TEXT_TERTIARY);
        add(statsLabel, BorderLayout.EAST);

        // 主题切换时刷新背景色 + 边框色
        ThemeUtil.addThemeChangeListener(() -> {
            setBackground(ThemeUtil.BG_CARD);
            refreshBorder();
        });
    }

    /** 重建顶部边框，使用 PRIMARY 色半透明（与卡片 accent 描边统一） */
    private void refreshBorder() {
        java.awt.Color primary = ThemeUtil.PRIMARY;
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0,
                        new java.awt.Color(primary.getRed(), primary.getGreen(),
                                primary.getBlue(), 40)),
                BorderFactory.createEmptyBorder(0, ThemeUtil.SPACE_LG, 0, ThemeUtil.SPACE_LG)));
    }

    // ==================== 状态更新 ====================

    public void setStatus(String text) {
        statusLabel.setText(text);
    }

    public void setStatus(String text, String status) {
        statusLabel.setText(text);
        switch (status) {
            case "ready":
                ThemeUtil.setDynamicForeground(statusIconLabel, () -> ThemeUtil.SUCCESS);
                statusIconLabel.setText("●");
                break;
            case "working":
                ThemeUtil.setDynamicForeground(statusIconLabel, () -> ThemeUtil.PRIMARY);
                statusIconLabel.setText("●");
                break;
            case "success":
                ThemeUtil.setDynamicForeground(statusIconLabel, () -> ThemeUtil.SUCCESS);
                statusIconLabel.setText("●");
                break;
            case "error":
                ThemeUtil.setDynamicForeground(statusIconLabel, () -> ThemeUtil.ERROR);
                statusIconLabel.setText("●");
                break;
            default:
                ThemeUtil.setDynamicForeground(statusIconLabel, () -> ThemeUtil.TEXT_TERTIARY);
                statusIconLabel.setText("●");
        }
    }

    public void showProgress(int percent) {
        showProgress(percent, null, null);
    }

    public void showProgress(int percent, String detail, String statusText) {
        progressBar.setVisible(true);
        progressBar.setProgress(Math.max(0, Math.min(100, percent)));
        progressBar.setDetail(detail != null ? detail : "");

        if (statusText != null) {
            setStatus(statusText, "working");
        } else {
            setStatus("压缩中...", "working");
        }
    }

    public void hideProgress() {
        progressBar.setVisible(false);
        progressBar.reset();
        setStatus("就绪", "ready");
    }

    public void flashSuccess(String message) {
        setStatus(message, "success");
        javax.swing.Timer timer = new javax.swing.Timer(2000, e -> setStatus("就绪", "ready"));
        timer.setRepeats(false);
        timer.start();
    }

    public void flashError(String message) {
        setStatus(message, "error");
        javax.swing.Timer timer = new javax.swing.Timer(3000, e -> setStatus("就绪", "ready"));
        timer.setRepeats(false);
        timer.start();
    }

    /**
     * 显示文件夹监控状态（v2.5: 提升监控可发现性）。
     * 监控运行时在状态栏左侧持久显示监控目录信息。
     */
    public void showWatchStatus(String watchDir) {
        if (watchDir != null && !watchDir.isEmpty()) {
            String shortName = watchDir;
            if (watchDir.length() > 40) {
                shortName = "..." + watchDir.substring(watchDir.length() - 37);
            }
            setStatus("📁 监控中: " + shortName, "working");
        }
    }

    public ModernProgressBar getProgressBar() { return progressBar; }
    public JLabel getStatusLabel() { return statusLabel; }
}
