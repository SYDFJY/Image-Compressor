package com.nchu.imagecompress.view;

import com.nchu.imagecompress.util.ThemeUtil;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
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
    private final JProgressBar progressBar;

    public StatusBar() {
        setLayout(new BorderLayout(ThemeUtil.SPACE_LG, 0));
        // 顶部：边框线 + 微阴影（参考蓝韵音乐 player bar 的顶部立体感）
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, ThemeUtil.BORDER),
                BorderFactory.createEmptyBorder(0, ThemeUtil.SPACE_LG, 0, ThemeUtil.SPACE_LG)));
        setPreferredSize(new Dimension(0, 36));
        // 使用 BG_CARD 凸起背景，区别于窗口底色
        setBackground(ThemeUtil.BG_CARD);

        // === 左侧：状态指示 ===
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, ThemeUtil.SPACE_SM, 0));
        leftPanel.setOpaque(false);

        statusIconLabel = new JLabel("●");
        statusIconLabel.setFont(ThemeUtil.FONT_TINY);
        statusIconLabel.setForeground(ThemeUtil.SUCCESS);
        leftPanel.add(statusIconLabel);

        statusLabel = new JLabel("就绪");
        statusLabel.setFont(ThemeUtil.FONT_SMALL);
        statusLabel.setForeground(ThemeUtil.TEXT_TERTIARY);
        leftPanel.add(statusLabel);

        add(leftPanel, BorderLayout.WEST);

        // === 中间：进度条（自适应宽度） ===
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(false);
        progressBar.setVisible(false);
        progressBar.setBorder(BorderFactory.createEmptyBorder());
        add(progressBar, BorderLayout.CENTER);

        // === 右侧：统计信息 ===
        statsLabel = new JLabel("", SwingConstants.RIGHT);
        statsLabel.setFont(ThemeUtil.FONT_SMALL);
        statsLabel.setForeground(ThemeUtil.TEXT_TERTIARY);
        add(statsLabel, BorderLayout.EAST);

        // 主题切换时刷新背景色
        ThemeUtil.addThemeChangeListener(() -> setBackground(ThemeUtil.BG_CARD));
    }

    // ==================== 状态更新 ====================

    public void setStatus(String text) {
        statusLabel.setText(text);
    }

    public void setStatus(String text, String status) {
        statusLabel.setText(text);
        switch (status) {
            case "ready":
                statusIconLabel.setForeground(ThemeUtil.SUCCESS);
                statusIconLabel.setText("●");
                break;
            case "working":
                statusIconLabel.setForeground(ThemeUtil.PRIMARY);
                statusIconLabel.setText("◉");
                break;
            case "success":
                statusIconLabel.setForeground(ThemeUtil.SUCCESS);
                statusIconLabel.setText("★");
                break;
            case "error":
                statusIconLabel.setForeground(ThemeUtil.ERROR);
                statusIconLabel.setText("✖");
                break;
            default:
                statusIconLabel.setForeground(ThemeUtil.TEXT_TERTIARY);
                statusIconLabel.setText("●");
        }
    }

    public void showProgress(int percent) {
        showProgress(percent, null, null);
    }

    public void showProgress(int percent, String detail, String statusText) {
        progressBar.setVisible(true);
        progressBar.setValue(Math.max(0, Math.min(100, percent)));

        if (detail != null) {
            statsLabel.setText(detail);
        }
        if (statusText != null) {
            setStatus(statusText, "working");
        } else {
            setStatus("压缩中...", "working");
        }
    }

    public void hideProgress() {
        progressBar.setVisible(false);
        progressBar.setValue(0);
        statsLabel.setText("");
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

    public JProgressBar getProgressBar() { return progressBar; }
    public JLabel getStatusLabel() { return statusLabel; }
}
