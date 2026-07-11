package com.nchu.imagecompress.view;

import com.nchu.imagecompress.model.ImageFileInfo;
import com.nchu.imagecompress.util.ThemeUtil;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.coobird.thumbnails.Thumbnails;

/**
 * 文件列表面板（UI 升级版 — 缩略图 + 信息列表风格）。
 *
 * <p>升级要点：
 * <ul>
 *   <li>每行 24×24px 缩略图 + 文件名/大小/尺寸/状态四列</li>
 *   <li>选中态：左侧 2px 主色竖条 + 极淡蓝背景</li>
 *   <li>Hover 态：极浅灰背景</li>
 *   <li>失败态：极浅红背景 + 红色文字</li>
 *   <li>空状态：居中插画 + 引导文字 + 导入按钮</li>
 *   <li>行高 44px，弱化表头</li>
 * </ul>
 *
 * @author NCHU-Student
 * @version 2.0.0
 * @since 2026-07-08
 */
public class FileListPanel extends JPanel {

    private final DefaultListModel<ImageFileInfo> listModel;
    private final JList<ImageFileInfo> fileList;
    private final JLabel statsLabel;
    private final JButton clearButton;
    private final JPanel emptyPanel;
    private final JScrollPane scrollPane;
    private final JPanel centerPanel;       // CardLayout 容器
    private final CardLayout centerLayout;  // 切换 scrollPane / emptyPanel
    private final Map<String, BufferedImage> thumbnailCache = new HashMap<>();

    public FileListPanel() {
        setLayout(new BorderLayout(0, 0));
        setBackground(ThemeUtil.BG_CARD);
        setBorder(BorderFactory.createEmptyBorder(ThemeUtil.SPACE_LG, ThemeUtil.SPACE_LG,
                ThemeUtil.SPACE_LG, ThemeUtil.SPACE_LG));

        // === 顶部：标题栏（弱化处理） ===
        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setOpaque(false);
        titlePanel.setBorder(BorderFactory.createEmptyBorder(0, 0, ThemeUtil.SPACE_MD, 0));

        JLabel titleLabel = new JLabel("文件列表");
        titleLabel.setFont(ThemeUtil.FONT_TITLE);
        titleLabel.setForeground(ThemeUtil.TEXT_PRIMARY);
        titlePanel.add(titleLabel, BorderLayout.WEST);

        clearButton = new JButton("清空");
        clearButton.setFont(ThemeUtil.FONT_SMALL);
        clearButton.setEnabled(false);
        clearButton.setFocusPainted(false);
        clearButton.setForeground(ThemeUtil.TEXT_TERTIARY);
        clearButton.setBackground(ThemeUtil.BG_CARD);
        clearButton.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        titlePanel.add(clearButton, BorderLayout.EAST);

        add(titlePanel, BorderLayout.NORTH);

        // === 中部：文件列表（CardLayout 切换 scrollPane / emptyPanel） ===
        centerLayout = new CardLayout();
        centerPanel = new JPanel(centerLayout);
        centerPanel.setOpaque(false);

        listModel = new DefaultListModel<>();
        fileList = new JList<>(listModel);
        fileList.setSelectionMode(DefaultListSelectionModel.SINGLE_SELECTION);
        fileList.setCellRenderer(new ThumbnailCellRenderer());
        fileList.setFixedCellHeight(44);
        fileList.setVisibleRowCount(10);
        fileList.setBackground(ThemeUtil.BG_CARD);

        scrollPane = new JScrollPane(fileList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getViewport().setBackground(ThemeUtil.BG_CARD);

        // 将滚动列表包裹在带表头的容器中
        JPanel listWithHeader = new JPanel(new BorderLayout());
        listWithHeader.setOpaque(false);
        listWithHeader.add(createHeaderRow(), BorderLayout.NORTH);
        listWithHeader.add(scrollPane, BorderLayout.CENTER);
        centerPanel.add(listWithHeader, "list");

        // === 空状态面板 ===
        emptyPanel = createEmptyPanel();
        centerPanel.add(emptyPanel, "empty");

        add(centerPanel, BorderLayout.CENTER);

        // 初始显示空状态
        centerLayout.show(centerPanel, "empty");

        // === 底部：统计栏 ===
        statsLabel = new JLabel("共 0 个文件");
        statsLabel.setFont(ThemeUtil.FONT_SMALL);
        statsLabel.setForeground(ThemeUtil.TEXT_TERTIARY);
        statsLabel.setBorder(BorderFactory.createEmptyBorder(ThemeUtil.SPACE_SM, 0, 0, 0));
        add(statsLabel, BorderLayout.SOUTH);
    }

    /** 创建弱化表头行（12px 灰色文字，底部 1px 分割线） */
    private JPanel createHeaderRow() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeUtil.BORDER),
                BorderFactory.createEmptyBorder(2, 2, 4, 2)));
        header.setPreferredSize(new Dimension(0, 22));

        JPanel cols = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        cols.setOpaque(false);
        cols.setBorder(BorderFactory.createEmptyBorder(0, 38, 0, 0));
        cols.add(headerLabel("文件名", 170));
        cols.add(headerLabel("大小", 65));
        cols.add(headerLabel("尺寸", 70));
        cols.add(headerLabel("状态", 45));
        header.add(cols, BorderLayout.CENTER);
        return header;
    }

    private static JLabel headerLabel(String text, int width) {
        JLabel label = new JLabel(text);
        label.setFont(ThemeUtil.FONT_TINY);
        label.setForeground(ThemeUtil.TEXT_TERTIARY);
        label.setPreferredSize(new Dimension(width, 16));
        return label;
    }

    /** 创建空状态面板 */
    private JPanel createEmptyPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(40, 20, 40, 20));

        JPanel centerPanel = new JPanel(new BorderLayout(0, ThemeUtil.SPACE_LG));
        centerPanel.setOpaque(false);

        // Emoji 图标
        JLabel iconLabel = new JLabel("📂", SwingConstants.CENTER);
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 40));
        centerPanel.add(iconLabel, BorderLayout.NORTH);

        // 引导文字
        JPanel textPanel = new JPanel(new BorderLayout(0, 4));
        textPanel.setOpaque(false);
        JLabel guideLabel = new JLabel("拖拽图片到此处，或点击导入文件", SwingConstants.CENTER);
        guideLabel.setFont(ThemeUtil.FONT_BODY);
        guideLabel.setForeground(ThemeUtil.TEXT_SECONDARY);
        textPanel.add(guideLabel, BorderLayout.CENTER);

        centerPanel.add(textPanel, BorderLayout.CENTER);

        panel.add(centerPanel, BorderLayout.CENTER);
        return panel;
    }

    /** 切换空状态/列表显示 */
    private void toggleEmptyState() {
        boolean empty = listModel.isEmpty();
        centerLayout.show(centerPanel, empty ? "empty" : "list");
    }

    // ==================== 数据操作（API 兼容） ====================

    public void setFileList(List<ImageFileInfo> files) {
        listModel.clear();
        for (ImageFileInfo file : files) {
            listModel.addElement(file);
        }
        updateStats();
    }

    public void addFile(ImageFileInfo file) {
        listModel.addElement(file);
        updateStats();
    }

    public void removeFile(int index) {
        if (index >= 0 && index < listModel.size()) {
            listModel.remove(index);
            updateStats();
        }
    }

    public void clearAllFiles() {
        listModel.clear();
        updateStats();
    }

    public void updateFileStatus(int index, ImageFileInfo.Status status) {
        if (index >= 0 && index < listModel.size()) {
            listModel.get(index).setStatus(status);
            fileList.repaint();
        }
    }

    public int getSelectedIndex() { return fileList.getSelectedIndex(); }

    public ImageFileInfo getSelectedFile() { return fileList.getSelectedValue(); }

    public List<ImageFileInfo> getFileList() {
        List<ImageFileInfo> files = new java.util.ArrayList<>(listModel.size());
        for (int i = 0; i < listModel.size(); i++) {
            files.add(listModel.get(i));
        }
        return files;
    }

    public JButton getClearButton() { return clearButton; }
    public JList<ImageFileInfo> getFileJList() { return fileList; }
    public JButton getImportButton() { return null; } // 通过 MainFrame 的按钮触发导入

    // ==================== 内部方法 ====================

    /**
     * 异步加载文件缩略图（28×28px）并缓存，加载失败静默忽略。
     * 应在文件导入后调用。
     */
    public void loadThumbnail(ImageFileInfo info) {
        File src = info.getSourceFile();
        if (src == null) return;
        String key = src.getAbsolutePath();
        if (thumbnailCache.containsKey(key)) return;
        try {
            BufferedImage thumb = Thumbnails.of(src)
                    .size(28, 28)
                    .asBufferedImage();
            thumbnailCache.put(key, thumb);
        } catch (IOException ignored) {
            // 缩略图加载失败，回退为 emoji
        }
    }

    /** 获取缓存的缩略图（可能为 null） */
    public BufferedImage getThumbnail(String path) {
        return thumbnailCache.get(path);
    }

    private void updateStats() {
        int count = listModel.size();
        long totalBytes = 0;
        for (int i = 0; i < count; i++) {
            totalBytes += listModel.get(i).getOriginalSize();
        }
        statsLabel.setText("共 " + count + " 个文件 · 总计 " + formatFileSize(totalBytes));
        clearButton.setEnabled(count > 0);
        toggleEmptyState();
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        return String.format("%.2f GB", mb / 1024.0);
    }

    // ==================== 缩略图列表渲染器 ====================

    /**
     * 列表单元格渲染器（升级版）。
     *
     * <p>结构：左侧 2px 强调条 | 24x24 缩略图 | 文件名 | 尺寸 | 状态</p>
     */
    private class ThumbnailCellRenderer extends JPanel implements ListCellRenderer<ImageFileInfo> {

        private final JPanel accentBar;
        private final JLabel thumbLabel;
        private final JLabel nameLabel;
        private final JLabel sizeLabel;
        private final JLabel dimLabel;
        private final JLabel statusLabel;
        private final JPanel textPanel;

        ThumbnailCellRenderer() {
            setLayout(new BorderLayout(0, 0));
            setOpaque(true);

            // 左侧强调条
            accentBar = new JPanel();
            accentBar.setPreferredSize(new Dimension(3, 44));
            accentBar.setBackground(ThemeUtil.PRIMARY);
            accentBar.setOpaque(true);

            // 缩略图
            thumbLabel = new JLabel();
            thumbLabel.setPreferredSize(new Dimension(24, 24));
            thumbLabel.setHorizontalAlignment(SwingConstants.CENTER);

            // 文字区：文件名 + (尺寸 / 大小)
            nameLabel = new JLabel();
            nameLabel.setFont(ThemeUtil.FONT_BODY);

            sizeLabel = new JLabel();
            sizeLabel.setFont(ThemeUtil.FONT_SMALL);

            dimLabel = new JLabel();
            dimLabel.setFont(ThemeUtil.FONT_SMALL);
            dimLabel.setForeground(ThemeUtil.TEXT_TERTIARY);

            textPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
            textPanel.setOpaque(false);
            textPanel.add(nameLabel);
            textPanel.add(sizeLabel);
            textPanel.add(dimLabel);

            // 状态
            statusLabel = new JLabel();
            statusLabel.setFont(ThemeUtil.FONT_SMALL);
            statusLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            statusLabel.setPreferredSize(new Dimension(60, 44));

            // 组装
            JPanel centerPanel = new JPanel(new BorderLayout(12, 0));
            centerPanel.setOpaque(false);
            centerPanel.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 0));
            centerPanel.add(thumbLabel, BorderLayout.WEST);
            centerPanel.add(textPanel, BorderLayout.CENTER);
            centerPanel.add(statusLabel, BorderLayout.EAST);

            add(accentBar, BorderLayout.WEST);
            add(centerPanel, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends ImageFileInfo> list, ImageFileInfo value,
                int index, boolean isSelected, boolean cellHasFocus) {

            if (value == null) {
                resetDisplay();
                return this;
            }

            // 缩略图：优先使用缓存的 28×28 真实缩略图，回退为 emoji
            BufferedImage cached = (value.getSourceFile() != null)
                    ? thumbnailCache.get(value.getSourceFile().getAbsolutePath()) : null;
            if (cached != null) {
                thumbLabel.setIcon(new javax.swing.ImageIcon(cached));
                thumbLabel.setText("");
            } else {
                thumbLabel.setIcon(null);
                thumbLabel.setText(getFormatEmoji(getExt(value.getFileName())));
                thumbLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
            }

            // 文件名
            nameLabel.setText(value.getFileName());
            nameLabel.setForeground(ThemeUtil.TEXT_PRIMARY);

            // 大小
            sizeLabel.setText(value.getFormattedSize());
            sizeLabel.setForeground(ThemeUtil.TEXT_SECONDARY);

            // 尺寸
            if (value.getWidth() > 0 && value.getHeight() > 0) {
                dimLabel.setText(value.getWidth() + "×" + value.getHeight());
                dimLabel.setVisible(true);
            } else {
                dimLabel.setVisible(false);
            }

            // 状态
            switch (value.getStatus()) {
                case SUCCESS:
                    statusLabel.setText("● 成功");
                    statusLabel.setForeground(ThemeUtil.SUCCESS);
                    break;
                case FAILED:
                    statusLabel.setText("● 失败");
                    statusLabel.setForeground(ThemeUtil.ERROR);
                    break;
                case PROCESSING:
                    statusLabel.setText("● 处理中");
                    statusLabel.setForeground(ThemeUtil.PRIMARY);
                    break;
                default:
                    statusLabel.setText("待处理");
                    statusLabel.setForeground(ThemeUtil.TEXT_SECONDARY);
            }

            // 背景与强调条
            if (isSelected) {
                setBackground(ThemeUtil.BG_SELECTED);
                accentBar.setVisible(true);
                nameLabel.setFont(ThemeUtil.FONT_TITLE); // 加粗
            } else if (value.getStatus() == ImageFileInfo.Status.FAILED) {
                setBackground(ThemeUtil.ERROR_LIGHT);
                accentBar.setVisible(false);
                nameLabel.setFont(ThemeUtil.FONT_BODY);
            } else {
                setBackground(ThemeUtil.BG_CARD);
                accentBar.setVisible(false);
                nameLabel.setFont(ThemeUtil.FONT_BODY);
            }

            // 底部分隔线
            setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeUtil.BORDER));

            return this;
        }

        private void resetDisplay() {
            thumbLabel.setText("");
            nameLabel.setText("");
            sizeLabel.setText("");
            dimLabel.setText("");
            statusLabel.setText("");
            accentBar.setVisible(false);
            setBackground(ThemeUtil.BG_CARD);
        }

        private String getExt(String name) {
            int dot = name.lastIndexOf('.');
            return dot > 0 ? name.substring(dot + 1).toLowerCase() : "";
        }

        private String getFormatEmoji(String ext) {
            switch (ext) {
                case "jpg": case "jpeg": case "png": return "🖼";
                case "gif": return "🎞";
                case "bmp": return "🖼";
                case "webp": return "🌐";
                case "tiff": case "tif": return "📷";
                case "ico": return "🔲";
                default: return "📄";
            }
        }
    }
}
