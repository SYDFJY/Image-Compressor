package com.nchu.imagecompress.view;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.nchu.imagecompress.model.VideoFileInfo;
import com.nchu.imagecompress.util.ThemeUtil;
import com.nchu.imagecompress.util.VideoUtil;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;

/**
 * 视频预览面板（v3 — 双 Tab 布局：预览 + 信息）。
 *
 * <pre>
 * ┌───────────────────────────────────────┐
 * │  [ 🎬 视频预览 ]  [ 📋 视频信息 ]     │  ← JTabbedPane
 * ├───────────────────────────────────────┤
 * │  Tab "视频预览"：                      │
 * │  ┌─────────────────────────────────┐  │
 * │  │  VideoPlayerPanel              │  │
 * │  │  (VLCJ 内嵌播放器 或 降级提示)   │  │
 * │  └─────────────────────────────────┘  │
 * ├───────────────────────────────────────┤
 * │  Tab "视频信息"：                      │
 * │  🎬 文件名                            │
 * │  时长 / 分辨率 / 编码 / 帧率 / 大小    │
 * │  比特率                               │
 * └───────────────────────────────────────┘
 * │  压缩对比浮层 (overlayBar)             │
 * └───────────────────────────────────────┘
 * </pre>
 *
 * @author NCHU-Student
 * @version 3.0.0
 * @since 2026-07-11
 */
public class VideoPreviewPanel extends JPanel {

    // ==================== 子面板 ====================

    private final JTabbedPane tabbedPane;
    private final VideoPlayerPanel videoPlayerPanel;
    private final JPanel infoCardPanel;
    private final CardLayout infoCardLayout;
    private final JPanel infoEmptyPanel;
    private final JPanel infoDetailPanel;

    // ==================== 信息面板控件 ====================

    private JLabel videoIconLabel;
    private JLabel fileNameLabel;
    private JLabel durationLabel;
    private JLabel resolutionLabel;
    private JLabel codecLabel;
    private JLabel fpsLabel;
    private JLabel sizeLabel;
    private JLabel bitrateLabel;

    /** FFmpeg 未安装时的警告面板（初始隐藏） */
    private JPanel ffmpegWarningPanel;

    // ==================== 对比浮层 ====================

    private JPanel overlayBar;
    private JLabel origSizeValue;
    private JLabel compSizeValue;
    private JLabel ratioValue;
    private JLabel bitrateChangeValue;

    // ==================== 当前选中视频引用 ====================

    private VideoFileInfo currentVideoInfo;

    private static final String INFO_EMPTY = "INFO_EMPTY";
    private static final String INFO_DETAIL = "INFO_DETAIL";

    public VideoPreviewPanel() {
        setLayout(new BorderLayout(0, 0));
        setBackground(ThemeUtil.BG_CARD);

        // === Tab 页 ===
        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setFont(ThemeUtil.FONT_SMALL);
        tabbedPane.setBackground(ThemeUtil.BG_CARD);
        tabbedPane.setForeground(ThemeUtil.TEXT_SECONDARY);

        // Tab 1: 视频预览（VLCJ 内嵌播放器）
        videoPlayerPanel = new VideoPlayerPanel();
        tabbedPane.addTab("视频预览", new FlatSVGIcon("icons/film.svg"), videoPlayerPanel);

        // Tab 2: 视频信息
        infoCardLayout = new CardLayout();
        infoCardPanel = new JPanel(infoCardLayout);
        infoCardPanel.setOpaque(false);

        infoEmptyPanel = createInfoEmptyPanel();
        infoDetailPanel = createInfoDetailPanel();

        infoCardPanel.add(infoEmptyPanel, INFO_EMPTY);
        infoCardPanel.add(infoDetailPanel, INFO_DETAIL);
        infoCardLayout.show(infoCardPanel, INFO_EMPTY);

        tabbedPane.addTab("视频信息", new FlatSVGIcon("icons/clipboard.svg"), infoCardPanel);

        add(tabbedPane, BorderLayout.CENTER);

        // === 底部：对比浮层（初始隐藏） ===
        overlayBar = createOverlayBar();
        overlayBar.setVisible(false);
        add(overlayBar, BorderLayout.SOUTH);

        // 主题切换时刷新显式背景色
        ThemeUtil.addThemeChangeListener(() -> {
            setBackground(ThemeUtil.BG_CARD);
            tabbedPane.setBackground(ThemeUtil.BG_CARD);
            overlayBar.repaint();
            repaint();
        });
    }

    // ==================== 信息面板构建 ====================

    /**
     * 信息 Tab 空状态。
     */
    private JPanel createInfoEmptyPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(30, 20, 30, 20));

        JPanel centerPanel = new JPanel(new BorderLayout(0, ThemeUtil.SPACE_LG));
        centerPanel.setOpaque(false);

        JLabel iconLabel = new JLabel(new FlatSVGIcon("icons/clipboard.svg", 48, 48));
        centerPanel.add(iconLabel, BorderLayout.NORTH);

        JLabel guideLabel = new JLabel("请选择视频文件查看信息", SwingConstants.CENTER);
        guideLabel.setFont(ThemeUtil.FONT_BODY);
        guideLabel.setForeground(ThemeUtil.TEXT_SECONDARY);
        centerPanel.add(guideLabel, BorderLayout.CENTER);

        panel.add(centerPanel, BorderLayout.CENTER);
        return panel;
    }

    /**
     * 信息 Tab 详情面板（元数据表单，不含播放按钮）。
     */
    private JPanel createInfoDetailPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, ThemeUtil.SPACE_MD));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(
                ThemeUtil.SPACE_MD, ThemeUtil.SPACE_LG,
                ThemeUtil.SPACE_MD, ThemeUtil.SPACE_LG));

        // --- 头部：🎬 图标 + 文件名 ---
        JPanel headerPanel = new JPanel(new BorderLayout(ThemeUtil.SPACE_SM, 0));
        headerPanel.setOpaque(false);

        videoIconLabel = new JLabel(new FlatSVGIcon("icons/film.svg"));
        headerPanel.add(videoIconLabel, BorderLayout.WEST);

        fileNameLabel = new JLabel();
        fileNameLabel.setFont(ThemeUtil.FONT_TITLE);
        fileNameLabel.setForeground(ThemeUtil.TEXT_PRIMARY);
        headerPanel.add(fileNameLabel, BorderLayout.CENTER);

        // --- FFmpeg 未安装警告条（默认隐藏） ---
        ffmpegWarningPanel = createFfmpegWarningPanel();
        ffmpegWarningPanel.setVisible(false);

        // 头部 + 警告 + 元数据放入同一个容器
        JPanel topArea = new JPanel(new BorderLayout(0, ThemeUtil.SPACE_SM));
        topArea.setOpaque(false);
        topArea.add(headerPanel, BorderLayout.NORTH);
        topArea.add(ffmpegWarningPanel, BorderLayout.CENTER);

        panel.add(topArea, BorderLayout.NORTH);

        // --- 元数据表单 ---
        JPanel metaPanel = new JPanel(new GridBagLayout());
        metaPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(ThemeUtil.SPACE_SM, 0, ThemeUtil.SPACE_SM, ThemeUtil.SPACE_LG);

        int row = 0;
        durationLabel = createMetaRow(metaPanel, gbc, "时长", row++);
        resolutionLabel = createMetaRow(metaPanel, gbc, "分辨率", row++);
        codecLabel = createMetaRow(metaPanel, gbc, "编码", row++);
        fpsLabel = createMetaRow(metaPanel, gbc, "帧率", row++);
        sizeLabel = createMetaRow(metaPanel, gbc, "大小", row++);
        bitrateLabel = createMetaRow(metaPanel, gbc, "比特率", row++);

        // 弹性填充
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        metaPanel.add(new JLabel(), gbc);

        panel.add(metaPanel, BorderLayout.CENTER);

        return panel;
    }

    /**
     * 创建 FFmpeg 未安装时的警告提示面板。
     */
    private JPanel createFfmpegWarningPanel() {
        JPanel warnPanel = new JPanel(new BorderLayout(ThemeUtil.SPACE_SM, 0));
        warnPanel.setBackground(new Color(0xFFF3E0)); // 浅橙色警告背景
        warnPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xFFB74D), 1),
                BorderFactory.createEmptyBorder(ThemeUtil.SPACE_SM, ThemeUtil.SPACE_MD,
                        ThemeUtil.SPACE_SM, ThemeUtil.SPACE_MD)));

        JLabel warnIcon = new JLabel("⚠");
        warnIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
        warnPanel.add(warnIcon, BorderLayout.WEST);

        JPanel textPanel = new JPanel(new GridLayout(2, 1));
        textPanel.setOpaque(false);
        JLabel titleLabel = new JLabel("FFmpeg 未安装，无法读取视频元数据");
        titleLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 12));
        titleLabel.setForeground(new Color(0xE65100));
        textPanel.add(titleLabel);

        JLabel guideLabel = new JLabel("运行 winget install ffmpeg 或访问 ffmpeg.org 下载");
        guideLabel.setFont(ThemeUtil.FONT_SMALL);
        guideLabel.setForeground(new Color(0xBF360C));
        textPanel.add(guideLabel);

        warnPanel.add(textPanel, BorderLayout.CENTER);

        return warnPanel;
    }

    // ==================== 对比浮层 ====================

    /**
     * 创建持久化对比浮层（样式对齐 PreviewPanel.overlayBar）。
     */
    private JPanel createOverlayBar() {
        JPanel bar = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                Color bg = ThemeUtil.BG_CARD;
                g2.setColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 220));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(),
                        ThemeUtil.ARC_BUTTON, ThemeUtil.ARC_BUTTON);
                g2.dispose();
            }
        };
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(
                ThemeUtil.SPACE_MD, ThemeUtil.SPACE_LG,
                ThemeUtil.SPACE_MD, ThemeUtil.SPACE_LG));

        // 顶部标题行
        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);
        JLabel title = new JLabel("压缩对比");
        title.setFont(ThemeUtil.FONT_SMALL);
        title.setForeground(ThemeUtil.TEXT_SECONDARY);
        headerRow.add(title, BorderLayout.WEST);

        // 4 列数据行
        JPanel dataRow = new JPanel(new GridLayout(1, 4, 12, 0));
        dataRow.setOpaque(false);

        // 列 1：原始大小
        dataRow.add(createDataItem("原始大小", "—"));
        origSizeValue = (JLabel) ((JPanel) dataRow.getComponent(0)).getComponent(1);

        // 列 2：压缩后
        JPanel compCol = new JPanel(new GridLayout(2, 1));
        compCol.setOpaque(false);
        JLabel compTitle = new JLabel("压缩后");
        compTitle.setFont(ThemeUtil.FONT_SMALL);
        compTitle.setForeground(ThemeUtil.TEXT_SECONDARY);
        compCol.add(compTitle);
        compSizeValue = new JLabel("—");
        compSizeValue.setFont(ThemeUtil.FONT_BODY);
        compSizeValue.setForeground(ThemeUtil.TEXT_PRIMARY);
        compCol.add(compSizeValue);
        dataRow.add(compCol);

        // 列 3：压缩率
        dataRow.add(createDataItem("压缩率", "—"));
        ratioValue = (JLabel) ((JPanel) dataRow.getComponent(2)).getComponent(1);

        // 列 4：比特率变化
        dataRow.add(createDataItem("比特率", "—"));
        bitrateChangeValue = (JLabel) ((JPanel) dataRow.getComponent(3)).getComponent(1);

        bar.add(headerRow, BorderLayout.NORTH);
        bar.add(dataRow, BorderLayout.CENTER);

        return bar;
    }

    /**
     * 创建 overlay bar 中的单列数据项。
     */
    private JPanel createDataItem(String label, String value) {
        JPanel item = new JPanel(new GridLayout(2, 1));
        item.setOpaque(false);

        JLabel titleLabel = new JLabel(label);
        titleLabel.setFont(ThemeUtil.FONT_SMALL);
        titleLabel.setForeground(ThemeUtil.TEXT_SECONDARY);
        item.add(titleLabel);

        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(ThemeUtil.FONT_BODY);
        valueLabel.setForeground(ThemeUtil.TEXT_PRIMARY);
        item.add(valueLabel);

        return item;
    }

    /**
     * 创建元数据行（标签 + 值）。
     */
    private JLabel createMetaRow(JPanel panel, GridBagConstraints gbc, String label, int row) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;

        JLabel labelComp = new JLabel(label + ":");
        labelComp.setFont(ThemeUtil.FONT_SMALL);
        labelComp.setForeground(ThemeUtil.TEXT_SECONDARY);
        panel.add(labelComp, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel valueComp = new JLabel("—");
        valueComp.setFont(ThemeUtil.FONT_BODY);
        valueComp.setForeground(ThemeUtil.TEXT_PRIMARY);
        panel.add(valueComp, gbc);

        return valueComp;
    }

    // ==================== 公共方法 ====================

    /**
     * 显示视频文件信息并自动开始播放。
     * 自动检测 Model 上是否已有压缩数据，有则展示 overlay bar。
     */
    public void showVideoInfo(VideoFileInfo info) {
        if (info == null) {
            clearPreview();
            return;
        }

        this.currentVideoInfo = info;

        // --- 填充信息 Tab 的元数据 ---
        fileNameLabel.setText(info.getFileName());
        durationLabel.setText(info.getFullDurationString());
        resolutionLabel.setText(info.getWidth() > 0
                ? info.getWidth() + " × " + info.getHeight() : "未知");
        codecLabel.setText(info.getVideoCodec() != null ? info.getVideoCodec() : "未知");
        fpsLabel.setText(info.getFps() > 0 ? String.format("%.2f fps", info.getFps()) : "未知");
        sizeLabel.setText(info.getFormattedSize());
        bitrateLabel.setText(info.getBitrate() > 0
                ? formatBitrate(info.getBitrate()) : "未知");

        // --- FFmpeg 未安装且元数据全为空时，显示安装引导 ---
        boolean metadataMissing = info.getWidth() <= 0 && info.getFps() <= 0
                && info.getBitrate() <= 0 && info.getVideoCodec() == null;
        boolean ffmpegUnavailable = !VideoUtil.checkFfmpegAvailable();
        ffmpegWarningPanel.setVisible(metadataMissing && ffmpegUnavailable);

        infoCardLayout.show(infoCardPanel, INFO_DETAIL);

        // --- 自动加载到播放器（切换到预览 Tab） ---
        if (info.getSourceFile() != null && info.getSourceFile().exists()) {
            videoPlayerPanel.play(info);  // 传递完整 VideoFileInfo，含 fps/分辨率
            tabbedPane.setSelectedIndex(0); // 切换到预览 Tab
        }

        // --- 根据 Model 是否有压缩数据，展示/隐藏 overlay bar ---
        if (info.hasCompressedData()) {
            refreshOverlayBar(info);
            overlayBar.setVisible(true);
        } else {
            overlayBar.setVisible(false);
        }
    }

    /**
     * 显示压缩结果。将压缩数据写入 Model 并刷新 overlay bar。
     */
    public void showCompressionResult(VideoFileInfo info) {
        if (info == null || !info.hasCompressedData()) {
            overlayBar.setVisible(false);
            return;
        }

        this.currentVideoInfo = info;
        refreshOverlayBar(info);
        overlayBar.setVisible(true);
    }

    /**
     * 刷新 overlay bar 数据。
     */
    private void refreshOverlayBar(VideoFileInfo info) {
        origSizeValue.setText(VideoFileInfo.formatFileSize(info.getOriginalSize()));
        compSizeValue.setText(VideoFileInfo.formatFileSize(info.getCompressedSize()));
        compSizeValue.setForeground(ThemeUtil.TEXT_PRIMARY);

        double ratio = info.getCompressionRatio();
        if (ratio >= 0) {
            ratioValue.setText(String.format("−%.1f%%", ratio));
            ratioValue.setForeground(getRatioColor(ratio));
        } else {
            ratioValue.setText("—");
            ratioValue.setForeground(ThemeUtil.TEXT_TERTIARY);
        }

        if (info.getBitrate() > 0 && info.getCompressedBitrate() > 0) {
            bitrateChangeValue.setText(formatBitrate(info.getBitrate())
                    + " → " + formatBitrate(info.getCompressedBitrate()));
            bitrateChangeValue.setForeground(ThemeUtil.TEXT_PRIMARY);
        } else if (info.getCompressedBitrate() > 0) {
            bitrateChangeValue.setText(formatBitrate(info.getCompressedBitrate()));
            bitrateChangeValue.setForeground(ThemeUtil.TEXT_PRIMARY);
        } else {
            bitrateChangeValue.setText("—");
            bitrateChangeValue.setForeground(ThemeUtil.TEXT_TERTIARY);
        }
    }

    /**
     * 清空预览（停止播放 + 清空信息）。
     */
    public void clearPreview() {
        currentVideoInfo = null;
        videoPlayerPanel.stop();
        overlayBar.setVisible(false);
        infoCardLayout.show(infoCardPanel, INFO_EMPTY);
    }

    // ==================== Getter（供 Controller 使用） ====================

    /** 获取内嵌视频播放器面板 */
    public VideoPlayerPanel getVideoPlayerPanel() {
        return videoPlayerPanel;
    }

    /** 获取当前选中的视频信息 */
    public VideoFileInfo getCurrentVideoInfo() {
        return currentVideoInfo;
    }

    /**
     * 释放资源（应用退出时调用）。
     */
    public void release() {
        videoPlayerPanel.release();
    }

    // ==================== 工具方法 ====================

    private static Color getRatioColor(double ratio) {
        if (ratio > 30) return ThemeUtil.SUCCESS;
        if (ratio > 10) return ThemeUtil.PRIMARY;
        return ThemeUtil.TEXT_TERTIARY;
    }

    private static String formatBitrate(long bps) {
        if (bps < 1000) return bps + " bps";
        double kbps = bps / 1000.0;
        if (kbps < 1000) return String.format("%.0f kbps", kbps);
        double mbps = kbps / 1000.0;
        return String.format("%.1f Mbps", mbps);
    }
}
