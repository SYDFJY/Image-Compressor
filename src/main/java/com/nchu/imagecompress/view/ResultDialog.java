package com.nchu.imagecompress.view;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.nchu.imagecompress.model.AppConfig;
import com.nchu.imagecompress.model.CompressResult;
import com.nchu.imagecompress.util.FileUtil;
import com.nchu.imagecompress.util.LogUtil;
import com.nchu.imagecompress.util.ThemeUtil;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;

/**
 * 批量压缩结果弹窗（v2 — 主题感知）。
 *
 * <p>v2 更新：全部颜色/字体迁移至 ThemeUtil，支持 7 套主题自动适配；
 * emoji 图标替换为 FlatSVGIcon；文件大小格式化复用 FileUtil。</p>
 *
 * @author NCHU-Student
 * @version 2.0.0
 * @since 2026-07-08
 */
public class ResultDialog extends JDialog {

    /**
     * 显示批量压缩结果弹窗。
     *
     * @param owner         父窗口
     * @param results       所有压缩结果
     * @param outputDir     输出目录路径
     * @param totalElapsedMs 总耗时（毫秒）
     * @param appConfig     应用配置（用于读取"自动定位"偏好）
     */
    public static void show(Frame owner, List<CompressResult> results,
                            String outputDir, long totalElapsedMs, AppConfig appConfig) {
        ResultDialog dialog = new ResultDialog(owner, results, outputDir, totalElapsedMs, appConfig);
        dialog.setVisible(true);
    }

    /**
     * 私有构造，通过静态工厂方法调用。
     */
    private ResultDialog(Frame owner, List<CompressResult> results,
                         String outputDir, long totalElapsedMs, AppConfig appConfig) {
        super(owner, "压缩结果", true);
        setResizable(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // 找到第一个成功的结果（用于定位文件）
        CompressResult firstSuccess = null;
        for (CompressResult r : results) {
            if (r.isSuccess() && r.getOutputPath() != null) {
                firstSuccess = r;
                break;
            }
        }
        final CompressResult targetResult = firstSuccess;

        // 统计（支持图片和视频两种结果）
        int success = 0, fail = 0;
        long totalOriginal = 0, totalCompressed = 0;
        for (CompressResult r : results) {
            if (r.isSuccess()) {
                success++;
                // 优先获取图片结果，其次视频结果
                if (r.getInputInfo() != null) {
                    totalOriginal += r.getInputInfo().getOriginalSize();
                } else if (r.getVideoInputInfo() != null) {
                    totalOriginal += r.getVideoInputInfo().getOriginalSize();
                }
                totalCompressed += r.getOutputSize();
            } else {
                fail++;
            }
        }

        double savedPercent = totalOriginal > 0
                ? (1.0 - (double) totalCompressed / totalOriginal) * 100.0 : 0;
        long savedBytes = totalOriginal - totalCompressed;

        // ========== 构建 UI ==========
        JPanel mainPanel = new JPanel(new BorderLayout(0, 16));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(24, 28, 20, 28));
        mainPanel.setBackground(ThemeUtil.BG_CARD);

        // --- 顶部：图标 + 标题 ---
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        // 使用 SVG 图标替代 emoji
        String iconPath = success > 0 ? "icons/play.svg" : "icons/delete.svg";
        JLabel iconLabel = new JLabel(new FlatSVGIcon(iconPath, 36, 36));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);

        String titleText = success > 0
                ? "压缩完成！" : "压缩失败";
        JLabel titleLabel = new JLabel(titleText);
        titleLabel.setFont(ThemeUtil.FONT_TITLE.deriveFont(20f));
        titleLabel.setForeground(ThemeUtil.TEXT_PRIMARY);
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel titlePanel = new JPanel(new BorderLayout(0, 8));
        titlePanel.setOpaque(false);
        titlePanel.add(iconLabel, BorderLayout.NORTH);
        titlePanel.add(titleLabel, BorderLayout.SOUTH);
        headerPanel.add(titlePanel, BorderLayout.CENTER);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // --- 中部：统计数据卡片 ---
        JPanel statsPanel = new JPanel(new GridLayout(1, 3, 16, 0));
        statsPanel.setOpaque(false);
        statsPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));

        statsPanel.add(createStatCard("成功", String.valueOf(success), ThemeUtil.SUCCESS));
        statsPanel.add(createStatCard("失败", String.valueOf(fail),
                fail > 0 ? ThemeUtil.ERROR : ThemeUtil.TEXT_SECONDARY));
        statsPanel.add(createStatCard("节省空间",
                savedBytes > 0 ? FileUtil.formatFileSize(savedBytes) : "—",
                savedBytes > 0 ? ThemeUtil.PRIMARY : ThemeUtil.TEXT_SECONDARY));
        mainPanel.add(statsPanel, BorderLayout.CENTER);

        // --- 详情行 ---
        JPanel detailPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 4));
        detailPanel.setOpaque(false);
        detailPanel.add(createDetailLabel("压缩率: "
                + (savedPercent > 0 ? String.format("−%.1f%%", savedPercent) : "—")));
        detailPanel.add(createDetailLabel("总耗时: " + formatElapsed(totalElapsedMs)));
        detailPanel.add(createDetailLabel("原始: " + FileUtil.formatFileSize(totalOriginal)));
        detailPanel.add(createDetailLabel("压缩后: " + FileUtil.formatFileSize(totalCompressed)));

        JPanel detailWrapper = new JPanel(new BorderLayout());
        detailWrapper.setOpaque(false);
        detailWrapper.add(detailPanel, BorderLayout.CENTER);
        mainPanel.add(detailWrapper, BorderLayout.SOUTH);

        // --- 底部按钮 ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));

        // 打开输出目录按钮（定位到首个成功文件）
        JButton openDirBtn = new JButton("打开输出目录", new FlatSVGIcon("icons/folder.svg"));
        openDirBtn.setFont(ThemeUtil.FONT_BODY);
        openDirBtn.setPreferredSize(new Dimension(150, 36));
        openDirBtn.addActionListener(e -> {
            if (targetResult != null) {
                // 优先定位到首个成功文件
                FileUtil.openFileInExplorer(targetResult.getOutputPath());
            } else {
                // 无成功文件时回退到打开目录
                openOutputDirectory(outputDir);
            }
        });
        buttonPanel.add(openDirBtn);

        // 自动定位 checkbox
        JCheckBox autoRevealCheck = new JCheckBox("完成后自动定位文件");
        autoRevealCheck.setFont(ThemeUtil.FONT_TINY);
        autoRevealCheck.setForeground(ThemeUtil.TEXT_SECONDARY);
        autoRevealCheck.setSelected(appConfig != null && appConfig.isAutoRevealOutput());
        autoRevealCheck.setOpaque(false);
        autoRevealCheck.setFocusPainted(false);
        autoRevealCheck.addActionListener(e -> {
            if (appConfig != null) {
                appConfig.setAutoRevealOutput(autoRevealCheck.isSelected());
            }
        });
        buttonPanel.add(autoRevealCheck);

        // 关闭按钮
        JButton closeBtn = new JButton("确定");
        closeBtn.setFont(ThemeUtil.FONT_TITLE);
        closeBtn.setPreferredSize(new Dimension(100, 36));
        closeBtn.addActionListener(e -> dispose());
        buttonPanel.add(closeBtn);

        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
        pack();
        setLocationRelativeTo(owner);
        setSize(480, getHeight());

        // ESC 关闭
        getRootPane().registerKeyboardAction(
                e -> dispose(),
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建统计卡片面板（主题感知）。
     */
    private static JPanel createStatCard(String label, String value, Color valueColor) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(ThemeUtil.BG_HOVER);
        card.setBorder(BorderFactory.createEmptyBorder(12, 8, 12, 8));

        JLabel valueLabel = new JLabel(value, SwingConstants.CENTER);
        valueLabel.setFont(ThemeUtil.FONT_TITLE.deriveFont(22f));
        valueLabel.setForeground(valueColor);
        card.add(valueLabel, BorderLayout.CENTER);

        JLabel labelComp = new JLabel(label, SwingConstants.CENTER);
        labelComp.setFont(ThemeUtil.FONT_TINY);
        labelComp.setForeground(ThemeUtil.TEXT_SECONDARY);
        card.add(labelComp, BorderLayout.SOUTH);

        return card;
    }

    /**
     * 创建详情小标签（主题感知）。
     */
    private static JLabel createDetailLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(ThemeUtil.FONT_TINY);
        label.setForeground(ThemeUtil.TEXT_SECONDARY);
        return label;
    }

    /**
     * 打开输出目录。
     */
    private static void openOutputDirectory(String dirPath) {
        if (dirPath == null || dirPath.isEmpty()) return;
        try {
            File dir = new File(dirPath);
            if (dir.exists() && dir.isDirectory()) {
                Desktop.getDesktop().open(dir);
            }
        } catch (IOException e) {
            LogUtil.error("[ResultDialog] 无法打开输出目录: " + e.getMessage());
        }
    }

    // ==================== 格式化工具 ====================

    private static String formatElapsed(long ms) {
        if (ms < 1000) return ms + " ms";
        if (ms < 60000) return String.format("%.1f 秒", ms / 1000.0);
        long minutes = ms / 60000;
        long seconds = (ms % 60000) / 1000;
        return minutes + " 分 " + seconds + " 秒";
    }
}
