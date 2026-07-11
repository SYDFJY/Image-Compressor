package com.nchu.imagecompress.view;

import com.nchu.imagecompress.model.VideoFileInfo;
import com.nchu.imagecompress.util.ThemeUtil;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
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
 * 视频预览面板 — 显示视频文件信息、压缩对比浮层、播放按钮。
 *
 * <p>支持两个状态：
 * <ul>
 *   <li>空状态：引导信息</li>
 *   <li>视频信息：文件元数据 +（压缩后的）对比 overlay bar + ▶ 播放按钮</li>
 * </ul>
 *
 * <p>对比数据持久化在 {@link VideoFileInfo} Model 上，不受文件列表切换影响。</p>
 *
 * @author NCHU-Student
 * @version 2.1.0
 * @since 2026-07-11
 */
public class VideoPreviewPanel extends JPanel {

    // ==================== 视图卡片 ====================

    private final CardLayout cardLayout;
    private final JPanel cardPanel;
    private final JPanel emptyPanel;
    private final JPanel infoPanel;

    // ==================== 信息面板控件 ====================

    private JLabel videoIconLabel;
    private JLabel fileNameLabel;
    private JLabel durationLabel;
    private JLabel resolutionLabel;
    private JLabel codecLabel;
    private JLabel fpsLabel;
    private JLabel sizeLabel;
    private JLabel bitrateLabel;

    // ==================== 播放按钮 ====================

    private JButton playOriginalBtn;
    private JButton playCompressedBtn;

    // ==================== 对比浮层 ====================

    private JPanel overlayBar;
    private JLabel origSizeValue;
    private JLabel compSizeValue;
    private JLabel ratioValue;
    private JLabel bitrateChangeValue;

    // ==================== 当前选中视频引用 ====================

    private VideoFileInfo currentVideoInfo;

    private static final String CARD_EMPTY = "EMPTY";
    private static final String CARD_INFO = "INFO";

    public VideoPreviewPanel() {
        setLayout(new BorderLayout(0, 0));
        setBackground(ThemeUtil.BG_CARD);
        setBorder(BorderFactory.createEmptyBorder(ThemeUtil.SPACE_LG, ThemeUtil.SPACE_LG,
                ThemeUtil.SPACE_LG, ThemeUtil.SPACE_LG));

        // === 顶部：标题 ===
        JLabel titleLabel = new JLabel("视频信息");
        titleLabel.setFont(ThemeUtil.FONT_TITLE);
        titleLabel.setForeground(ThemeUtil.TEXT_PRIMARY);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, ThemeUtil.SPACE_SM, 0));
        add(titleLabel, BorderLayout.NORTH);

        // === 中部：CardLayout 切换空状态/信息面板 ===
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.setOpaque(false);

        emptyPanel = createEmptyPanel();
        infoPanel = createInfoPanel();

        cardPanel.add(emptyPanel, CARD_EMPTY);
        cardPanel.add(infoPanel, CARD_INFO);

        add(cardPanel, BorderLayout.CENTER);

        // === 底部：对比浮层（初始隐藏） ===
        overlayBar = createOverlayBar();
        overlayBar.setVisible(false);
        add(overlayBar, BorderLayout.SOUTH);

        // 初始显示空状态
        cardLayout.show(cardPanel, CARD_EMPTY);
    }

    // ==================== 子面板构建 ====================

    /**
     * 空状态面板。
     */
    private JPanel createEmptyPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(40, 20, 40, 20));

        JPanel centerPanel = new JPanel(new BorderLayout(0, ThemeUtil.SPACE_LG));
        centerPanel.setOpaque(false);

        JLabel iconLabel = new JLabel("🎬", SwingConstants.CENTER);
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 48));
        centerPanel.add(iconLabel, BorderLayout.NORTH);

        JLabel guideLabel = new JLabel("请选择视频文件预览", SwingConstants.CENTER);
        guideLabel.setFont(ThemeUtil.FONT_BODY);
        guideLabel.setForeground(ThemeUtil.TEXT_SECONDARY);
        centerPanel.add(guideLabel, BorderLayout.CENTER);

        panel.add(centerPanel, BorderLayout.CENTER);
        return panel;
    }

    /**
     * 视频信息面板（元数据表单 + 播放按钮）。
     */
    private JPanel createInfoPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, ThemeUtil.SPACE_MD));
        panel.setOpaque(false);

        // --- 头部：🎬 图标 + 文件名 + [▶ 播放] ---
        JPanel headerPanel = new JPanel(new BorderLayout(ThemeUtil.SPACE_SM, 0));
        headerPanel.setOpaque(false);

        videoIconLabel = new JLabel("🎬");
        videoIconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 32));
        headerPanel.add(videoIconLabel, BorderLayout.WEST);

        fileNameLabel = new JLabel();
        fileNameLabel.setFont(ThemeUtil.FONT_TITLE);
        fileNameLabel.setForeground(ThemeUtil.TEXT_PRIMARY);
        headerPanel.add(fileNameLabel, BorderLayout.CENTER);

        playOriginalBtn = createPlayButton("▶ 播放", "播放原始视频");
        headerPanel.add(playOriginalBtn, BorderLayout.EAST);

        panel.add(headerPanel, BorderLayout.NORTH);

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
     * 创建播放按钮（▶ 符号 + 扁平样式）。
     */
    private JButton createPlayButton(String text, String tooltip) {
        JButton btn = new JButton(text);
        btn.setFont(ThemeUtil.FONT_SMALL);
        btn.setToolTipText(tooltip);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        btn.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        return btn;
    }

    /**
     * 创建持久化对比浮层（样式对齐 PreviewPanel.overlayBar）。
     *
     * <p>4 列布局：原始大小 | 压缩后 | 压缩率 | 比特率变化，
     * 压缩后列内嵌一个小 ▶ 播放按钮。</p>
     */
    private JPanel createOverlayBar() {
        JPanel bar = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(255, 255, 255, 220));
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

        // 列 2：压缩后 + 播放按钮
        JPanel compCol = new JPanel(new BorderLayout(4, 0));
        compCol.setOpaque(false);
        JPanel compLabels = new JPanel(new GridLayout(2, 1));
        compLabels.setOpaque(false);
        JLabel compTitle = new JLabel("压缩后");
        compTitle.setFont(ThemeUtil.FONT_SMALL);
        compTitle.setForeground(ThemeUtil.TEXT_SECONDARY);
        compLabels.add(compTitle);
        compSizeValue = new JLabel("—");
        compSizeValue.setFont(ThemeUtil.FONT_BODY);
        compSizeValue.setForeground(ThemeUtil.TEXT_PRIMARY);
        compLabels.add(compSizeValue);

        compCol.add(compLabels, BorderLayout.CENTER);
        playCompressedBtn = createPlayButton("▶", "播放压缩后视频");
        playCompressedBtn.setVisible(false);
        compCol.add(playCompressedBtn, BorderLayout.EAST);
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
     * 创建 overlay bar 中的单列数据项（标签在上，值在下）。
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
     * 显示视频文件信息。
     * 自动检测 Model 上是否已有压缩数据，有则展示 overlay bar。
     */
    public void showVideoInfo(VideoFileInfo info) {
        if (info == null) {
            clearPreview();
            return;
        }

        this.currentVideoInfo = info;

        // 填充元数据
        fileNameLabel.setText(info.getFileName());
        durationLabel.setText(info.getFullDurationString());
        resolutionLabel.setText(info.getWidth() > 0
                ? info.getWidth() + " × " + info.getHeight() : "未知");
        codecLabel.setText(info.getVideoCodec() != null ? info.getVideoCodec() : "未知");
        fpsLabel.setText(info.getFps() > 0 ? String.format("%.2f fps", info.getFps()) : "未知");
        sizeLabel.setText(info.getFormattedSize());
        bitrateLabel.setText(info.getBitrate() > 0
                ? formatBitrate(info.getBitrate()) : "未知");

        // 根据 Model 是否有压缩数据，展示/隐藏 overlay bar
        if (info.hasCompressedData()) {
            refreshOverlayBar(info);
            overlayBar.setVisible(true);
            playCompressedBtn.setVisible(info.getCompressedPath() != null
                    && !info.getCompressedPath().isEmpty());
        } else {
            overlayBar.setVisible(false);
            playCompressedBtn.setVisible(false);
        }

        cardLayout.show(cardPanel, CARD_INFO);
    }

    /**
     * 显示压缩结果（Controller 在压缩完成后调用）。
     * 将压缩数据写入 Model 并刷新 overlay bar。
     */
    public void showCompressionResult(VideoFileInfo info) {
        if (info == null || !info.hasCompressedData()) {
            overlayBar.setVisible(false);
            playCompressedBtn.setVisible(false);
            return;
        }

        this.currentVideoInfo = info;
        refreshOverlayBar(info);
        overlayBar.setVisible(true);
        playCompressedBtn.setVisible(info.getCompressedPath() != null
                && !info.getCompressedPath().isEmpty());
        cardLayout.show(cardPanel, CARD_INFO);
    }

    /**
     * 刷新 overlay bar 数据。
     */
    private void refreshOverlayBar(VideoFileInfo info) {
        // 原始大小
        origSizeValue.setText(VideoFileInfo.formatFileSize(info.getOriginalSize()));

        // 压缩后大小
        compSizeValue.setText(VideoFileInfo.formatFileSize(info.getCompressedSize()));
        compSizeValue.setForeground(ThemeUtil.TEXT_PRIMARY);

        // 压缩率（条件着色）
        double ratio = info.getCompressionRatio();
        if (ratio >= 0) {
            ratioValue.setText(String.format("−%.1f%%", ratio));
            ratioValue.setForeground(getRatioColor(ratio));
        } else {
            ratioValue.setText("—");
            ratioValue.setForeground(ThemeUtil.TEXT_TERTIARY);
        }

        // 比特率变化
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
     * 清空预览。
     */
    public void clearPreview() {
        currentVideoInfo = null;
        overlayBar.setVisible(false);
        playCompressedBtn.setVisible(false);
        cardLayout.show(cardPanel, CARD_EMPTY);
    }

    // ==================== Getter（供 Controller 绑定事件） ====================

    /** 获取「播放原始视频」按钮 */
    public JButton getPlayOriginalButton() { return playOriginalBtn; }

    /** 获取「播放压缩后视频」按钮 */
    public JButton getPlayCompressedButton() { return playCompressedBtn; }

    /** 获取当前选中的视频信息 */
    public VideoFileInfo getCurrentVideoInfo() { return currentVideoInfo; }

    // ==================== 工具方法 ====================

    /**
     * 根据压缩率返回对应颜色。
     * 对齐 PreviewPanel 的条件着色逻辑：>30% 绿色，>10% 蓝色，其余灰色。
     */
    private static Color getRatioColor(double ratio) {
        if (ratio > 30) return ThemeUtil.SUCCESS;        // 绿色：节省 30% 以上
        if (ratio > 10) return ThemeUtil.PRIMARY;        // 蓝色：节省 10%-30%
        return ThemeUtil.TEXT_TERTIARY;                   // 灰色：节省不足 10%
    }

    /**
     * 格式化比特率。
     */
    private static String formatBitrate(long bps) {
        if (bps < 1000) return bps + " bps";
        double kbps = bps / 1000.0;
        if (kbps < 1000) return String.format("%.0f kbps", kbps);
        double mbps = kbps / 1000.0;
        return String.format("%.1f Mbps", mbps);
    }
}
