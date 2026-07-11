package com.nchu.imagecompress.view;

import com.nchu.imagecompress.model.CompressResult;
import com.nchu.imagecompress.util.LogUtil;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;

/**
 * 批量压缩结果弹窗。
 *
 * <p>压缩完成后弹出，显示成功/失败统计、节省空间、
 * 总耗时等信息，并提供"打开输出目录"按钮。</p>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-08
 */
public class ResultDialog extends JDialog {

    /** 配色 */
    private static final Color SUCCESS_COLOR = new Color(0x67C23A);
    private static final Color FAIL_COLOR = new Color(0xF56C6C);
    private static final Color ACCENT_COLOR = com.nchu.imagecompress.util.ThemeUtil.PRIMARY;
    private static final Color TEXT_MAIN = new Color(0x303133);
    private static final Color TEXT_SECONDARY = new Color(0x909399);

    /**
     * 显示批量压缩结果弹窗。
     *
     * @param owner       父窗口
     * @param results     所有压缩结果
     * @param outputDir   输出目录路径
     * @param totalElapsedMs 总耗时（毫秒）
     */
    public static void show(Frame owner, List<CompressResult> results,
                            String outputDir, long totalElapsedMs) {
        ResultDialog dialog = new ResultDialog(owner, results, outputDir, totalElapsedMs);
        dialog.setVisible(true);
    }

    /**
     * 私有构造，通过静态工厂方法调用。
     */
    private ResultDialog(Frame owner, List<CompressResult> results,
                         String outputDir, long totalElapsedMs) {
        super(owner, "压缩结果", true);
        setResizable(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

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

        // --- 顶部：图标 + 标题 ---
        JPanel headerPanel = new JPanel(new BorderLayout());
        JLabel iconLabel = new JLabel(success > 0 ? "✅" : "❌");
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 36));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);

        String titleText = success > 0
                ? "压缩完成！" : "压缩失败";
        JLabel titleLabel = new JLabel(titleText);
        titleLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 20));
        titleLabel.setForeground(TEXT_MAIN);
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel titlePanel = new JPanel(new BorderLayout(0, 8));
        titlePanel.add(iconLabel, BorderLayout.NORTH);
        titlePanel.add(titleLabel, BorderLayout.SOUTH);
        headerPanel.add(titlePanel, BorderLayout.CENTER);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // --- 中部：统计数据卡片 ---
        JPanel statsPanel = new JPanel(new GridLayout(1, 3, 16, 0));
        statsPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));

        statsPanel.add(createStatCard("成功", String.valueOf(success), SUCCESS_COLOR));
        statsPanel.add(createStatCard("失败", String.valueOf(fail),
                fail > 0 ? FAIL_COLOR : TEXT_SECONDARY));
        statsPanel.add(createStatCard("节省空间",
                savedBytes > 0 ? formatFileSize(savedBytes) : "—",
                savedBytes > 0 ? ACCENT_COLOR : TEXT_SECONDARY));
        mainPanel.add(statsPanel, BorderLayout.CENTER);

        // --- 详情行 ---
        JPanel detailPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 4));
        detailPanel.add(createDetailLabel("压缩率: "
                + (savedPercent > 0 ? String.format("−%.1f%%", savedPercent) : "—")));
        detailPanel.add(createDetailLabel("总耗时: " + formatElapsed(totalElapsedMs)));
        detailPanel.add(createDetailLabel("原始: " + formatFileSize(totalOriginal)));
        detailPanel.add(createDetailLabel("压缩后: " + formatFileSize(totalCompressed)));

        JPanel detailWrapper = new JPanel(new BorderLayout());
        detailWrapper.add(detailPanel, BorderLayout.CENTER);
        mainPanel.add(detailWrapper, BorderLayout.SOUTH);

        // --- 底部按钮 ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));

        // 打开输出目录按钮
        JButton openDirBtn = new JButton("📂 打开输出目录");
        openDirBtn.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        openDirBtn.setPreferredSize(new Dimension(150, 36));
        openDirBtn.addActionListener(e -> {
            openOutputDirectory(outputDir);
        });
        buttonPanel.add(openDirBtn);

        // 关闭按钮
        JButton closeBtn = new JButton("确定");
        closeBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
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
     * 创建统计卡片面板。
     */
    private static JPanel createStatCard(String label, String value, Color valueColor) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(new Color(0xF5F7FA));
        card.setBorder(BorderFactory.createEmptyBorder(12, 8, 12, 8));

        JLabel valueLabel = new JLabel(value, SwingConstants.CENTER);
        valueLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 22));
        valueLabel.setForeground(valueColor);
        card.add(valueLabel, BorderLayout.CENTER);

        JLabel labelComp = new JLabel(label, SwingConstants.CENTER);
        labelComp.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
        labelComp.setForeground(TEXT_SECONDARY);
        card.add(labelComp, BorderLayout.SOUTH);

        return card;
    }

    /**
     * 创建详情小标签。
     */
    private static JLabel createDetailLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
        label.setForeground(TEXT_SECONDARY);
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

    private static String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return new DecimalFormat("#.#").format(kb) + " KB";
        double mb = kb / 1024.0;
        if (mb < 1024) return new DecimalFormat("#.#").format(mb) + " MB";
        return new DecimalFormat("#.##").format(mb / 1024.0) + " GB";
    }

    private static String formatElapsed(long ms) {
        if (ms < 1000) return ms + " ms";
        if (ms < 60000) return String.format("%.1f 秒", ms / 1000.0);
        long minutes = ms / 60000;
        long seconds = (ms % 60000) / 1000;
        return minutes + " 分 " + seconds + " 秒";
    }
}
