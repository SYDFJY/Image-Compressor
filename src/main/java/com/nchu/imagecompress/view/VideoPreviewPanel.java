package com.nchu.imagecompress.view;

import com.nchu.imagecompress.model.VideoFileInfo;
import com.nchu.imagecompress.util.ThemeUtil;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * 视频预览面板 — 显示视频文件信息和压缩结果对比。
 *
 * <p>由于 Java Swing 不原生支持视频播放，此面板展示：
 * <ul>
 *   <li>空状态：引导信息</li>
 *   <li>选中视频：文件元数据（时长/分辨率/编码/帧率）</li>
 *   <li>压缩完成：原始 vs 压缩后大小对比</li>
 * </ul>
 *
 * @author NCHU-Student
 * @version 2.0.0
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

    // 压缩对比
    private JPanel comparisonPanel;
    private JLabel originalSizeValue;
    private JLabel compressedSizeValue;
    private JLabel savedValue;

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

        // comparisonPanel 必须在 createInfoPanel() 之前初始化，
        // 因为 createInfoPanel() 内部引用了 comparisonPanel 字段
        comparisonPanel = createComparisonPanel();
        emptyPanel = createEmptyPanel();
        infoPanel = createInfoPanel();

        cardPanel.add(emptyPanel, CARD_EMPTY);
        cardPanel.add(infoPanel, CARD_INFO);

        add(cardPanel, BorderLayout.CENTER);

        // 初始显示空状态
        cardLayout.show(cardPanel, CARD_EMPTY);
    }

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
     * 视频信息面板。
     */
    private JPanel createInfoPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, ThemeUtil.SPACE_MD));
        panel.setOpaque(false);

        // 视频图标 + 文件名
        JPanel headerPanel = new JPanel(new BorderLayout(ThemeUtil.SPACE_SM, 0));
        headerPanel.setOpaque(false);

        videoIconLabel = new JLabel("🎬");
        videoIconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 32));
        headerPanel.add(videoIconLabel, BorderLayout.WEST);

        fileNameLabel = new JLabel();
        fileNameLabel.setFont(ThemeUtil.FONT_TITLE);
        fileNameLabel.setForeground(ThemeUtil.TEXT_PRIMARY);
        headerPanel.add(fileNameLabel, BorderLayout.CENTER);

        panel.add(headerPanel, BorderLayout.NORTH);

        // 元数据表单
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
        gbc.gridy = row; gbc.gridx = 0; gbc.gridwidth = 2;
        gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH;
        metaPanel.add(new JLabel(), gbc);

        panel.add(metaPanel, BorderLayout.CENTER);

        // 压缩对比区（初始隐藏）
        panel.add(comparisonPanel, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * 压缩对比面板。
     */
    private JPanel createComparisonPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, ThemeUtil.BORDER),
                BorderFactory.createEmptyBorder(ThemeUtil.SPACE_MD, 0, 0, 0)));
        panel.setVisible(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 0, 2, ThemeUtil.SPACE_LG);
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 标题行
        gbc.gridx = 0; gbc.gridy = 0;
        JLabel titleLabel = new JLabel("压缩对比");
        titleLabel.setFont(ThemeUtil.FONT_TITLE);
        titleLabel.setForeground(ThemeUtil.TEXT_PRIMARY);
        panel.add(titleLabel, gbc);

        int row = 1;

        // 原始大小
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel origLabel = new JLabel("原始大小:");
        origLabel.setFont(ThemeUtil.FONT_SMALL);
        origLabel.setForeground(ThemeUtil.TEXT_SECONDARY);
        panel.add(origLabel, gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        originalSizeValue = new JLabel("—");
        originalSizeValue.setFont(ThemeUtil.FONT_BODY);
        originalSizeValue.setForeground(ThemeUtil.TEXT_PRIMARY);
        panel.add(originalSizeValue, gbc);
        row++;

        // 压缩后
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel compLabel = new JLabel("压缩后:");
        compLabel.setFont(ThemeUtil.FONT_SMALL);
        compLabel.setForeground(ThemeUtil.TEXT_SECONDARY);
        panel.add(compLabel, gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        compressedSizeValue = new JLabel("—");
        compressedSizeValue.setFont(ThemeUtil.FONT_BODY);
        compressedSizeValue.setForeground(ThemeUtil.SUCCESS);
        panel.add(compressedSizeValue, gbc);
        row++;

        // 节省
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel savedLabel = new JLabel("节省:");
        savedLabel.setFont(ThemeUtil.FONT_SMALL);
        savedLabel.setForeground(ThemeUtil.TEXT_SECONDARY);
        panel.add(savedLabel, gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        savedValue = new JLabel("—");
        savedValue.setFont(ThemeUtil.FONT_BODY);
        savedValue.setForeground(ThemeUtil.SUCCESS);
        panel.add(savedValue, gbc);

        return panel;
    }

    /**
     * 创建元数据行（标签 + 值）。
     */
    private JLabel createMetaRow(JPanel panel, GridBagConstraints gbc, String label, int row) {
        gbc.gridy = row; gbc.gridx = 0;
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
     */
    public void showVideoInfo(VideoFileInfo info) {
        if (info == null) {
            clearPreview();
            return;
        }

        fileNameLabel.setText(info.getFileName());
        durationLabel.setText(info.getFullDurationString());
        resolutionLabel.setText(info.getWidth() > 0
                ? info.getWidth() + " × " + info.getHeight() : "未知");
        codecLabel.setText(info.getVideoCodec() != null ? info.getVideoCodec() : "未知");
        fpsLabel.setText(info.getFps() > 0 ? String.format("%.2f fps", info.getFps()) : "未知");
        sizeLabel.setText(info.getFormattedSize());
        bitrateLabel.setText(info.getBitrate() > 0
                ? formatBitrate(info.getBitrate()) : "未知");

        // 隐藏对比区
        comparisonPanel.setVisible(false);
        cardLayout.show(cardPanel, CARD_INFO);
    }

    /**
     * 显示压缩结果对比。
     */
    public void showComparison(long originalSize, long compressedSize, double ratio) {
        originalSizeValue.setText(VideoFileInfo.formatFileSize(originalSize));
        compressedSizeValue.setText(VideoFileInfo.formatFileSize(compressedSize));
        savedValue.setText(String.format("%.1f%% (%s)",
                ratio,
                VideoFileInfo.formatFileSize(originalSize - compressedSize)));
        comparisonPanel.setVisible(true);
        cardLayout.show(cardPanel, CARD_INFO);
    }

    /**
     * 清空预览。
     */
    public void clearPreview() {
        cardLayout.show(cardPanel, CARD_EMPTY);
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
