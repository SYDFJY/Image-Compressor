package com.nchu.imagecompress.view;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.nchu.imagecompress.model.FileInfo;
import com.nchu.imagecompress.model.ImageFileInfo;
import com.nchu.imagecompress.model.VideoFileInfo;
import com.nchu.imagecompress.util.FileUtil;
import com.nchu.imagecompress.util.ThemeUtil;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.coobird.thumbnailator.Thumbnails;

/**
 * 文件列表面板（UI 升级版 — 缩略图 + 信息列表风格）。
 *
 * <p>v2.1 升级：使用 {@link FileInfo} 接口统一管理图片和视频文件，
 * 通过 {@code instanceof} 在渲染器中提供差异化显示。</p>
 *
 * <ul>
 *   <li>图片文件：🖼 图标 / 缩略图 + 尺寸信息</li>
 *   <li>视频文件：🎬 图标 + 时长信息</li>
 *   <li>每行 24×24px 缩略图 + 文件名/大小/尺寸(或时长)/状态四列</li>
 *   <li>选中态：左侧 2px 主色竖条 + 极淡蓝背景</li>
 *   <li>Hover 态：极浅灰背景</li>
 *   <li>失败态：极浅红背景 + 红色文字</li>
 *   <li>空状态：居中插画 + 引导文字 + 导入按钮</li>
 *   <li>行高 44px，弱化表头</li>
 * </ul>
 *
 * @author NCHU-Student
 * @version 2.1.0
 * @since 2026-07-08
 */
public class FileListPanel extends JPanel {

    private final DefaultListModel<FileInfo> listModel;
    private final JList<FileInfo> fileList;
    private final JLabel statsLabel;
    private final JButton clearButton;
    private final JPanel emptyPanel;
    private final JScrollPane scrollPane;
    private final JPanel centerPanel;       // CardLayout 容器
    private final CardLayout centerLayout;  // 切换 scrollPane / emptyPanel
    private final JPanel headerRow;         // 表头行（主题切换时刷新背景）
    private final Map<String, BufferedImage> thumbnailCache = new HashMap<>();

    // v2.2: 搜索/排序
    private final List<FileInfo> allFiles = new ArrayList<>(); // 主数据源
    private JTextField searchField;
    private JComboBox<String> sortCombo;
    private String currentQuery = "";
    private int currentSort = 0; // 0=原始, 1=名称, 2=大小

    // v2.3: 悬停预览
    private HoverPreviewPopup hoverPopup;
    private final javax.swing.Timer hoverTimer;
    private int hoveredIndex = -1;
    private java.awt.Point lastMousePoint = null;
    /** 大缩略图缓存（200×200），key 同 thumbnailCache */
    private final Map<String, BufferedImage> largeThumbnailCache = new HashMap<>();

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
        ThemeUtil.setDynamicForeground(titleLabel, () -> ThemeUtil.TEXT_PRIMARY);
        titlePanel.add(titleLabel, BorderLayout.WEST);

        clearButton = new JButton();
        clearButton.setIcon(new com.formdev.flatlaf.extras.FlatSVGIcon("icons/delete.svg"));
        clearButton.setEnabled(false);
        clearButton.setFocusPainted(false);
        clearButton.setContentAreaFilled(false);
        clearButton.setBorderPainted(false);
        clearButton.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        clearButton.setToolTipText("清空列表");
        titlePanel.add(clearButton, BorderLayout.EAST);

        // === 搜索栏（v2.2 — 实时过滤 + 排序） ===
        JPanel searchPanel = createSearchPanel();
        searchPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, ThemeUtil.SPACE_MD, 0));

        // 北区合并：标题 + 搜索
        JPanel northPanel = new JPanel(new BorderLayout(0, 0));
        northPanel.setOpaque(false);
        northPanel.add(titlePanel, BorderLayout.NORTH);
        northPanel.add(searchPanel, BorderLayout.SOUTH);
        add(northPanel, BorderLayout.NORTH);

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
        headerRow = createHeaderRow();
        listWithHeader.add(headerRow, BorderLayout.NORTH);
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
        ThemeUtil.setDynamicForeground(statsLabel, () -> ThemeUtil.TEXT_TERTIARY);
        statsLabel.setBorder(BorderFactory.createEmptyBorder(ThemeUtil.SPACE_SM, 0, 0, 0));
        add(statsLabel, BorderLayout.SOUTH);

        // === 悬停预览 ===
        hoverPopup = new HoverPreviewPopup();
        hoverTimer = new javax.swing.Timer(500, e -> triggerHoverPreview());
        hoverTimer.setRepeats(false);

        fileList.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                int index = fileList.locationToIndex(e.getPoint());
                if (index < 0 || index >= listModel.size()) {
                    cancelHover();
                    return;
                }
                if (index != hoveredIndex) {
                    // 行号变化：隐藏旧预览，重新计时
                    hoverPopup.hide();
                    hoveredIndex = index;
                    lastMousePoint = e.getPoint();
                    hoverTimer.restart();
                }
                // 同行微移不重置计时（防止闪烁）
            }
        });

        fileList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                cancelHover();
            }
        });

        // 主题切换时刷新所有显式设置的背景色（防止背景停留在旧主题颜色）
        ThemeUtil.addThemeChangeListener(() -> {
            setBackground(ThemeUtil.BG_CARD);
            fileList.setBackground(ThemeUtil.BG_CARD);
            scrollPane.getViewport().setBackground(ThemeUtil.BG_CARD);
            headerRow.setBackground(ThemeUtil.BG_HOVER);
            repaint();
        });
    }

    /** 创建强表头行（28px 高，深底色，加粗文字，底部 1px 分割线） */
    private JPanel createHeaderRow() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ThemeUtil.BG_HOVER);
        header.setOpaque(true);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeUtil.BORDER),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        header.setPreferredSize(new Dimension(0, 28));

        JPanel cols = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        cols.setOpaque(false);
        cols.setBorder(BorderFactory.createEmptyBorder(0, 30, 0, 0));
        cols.add(headerLabel("文件名", 170));
        cols.add(headerLabel("大小", 65));
        cols.add(headerLabel("信息", 70));
        cols.add(headerLabel("状态", 50));
        header.add(cols, BorderLayout.CENTER);
        return header;
    }

    /** 表头标签 — 参考蓝韵音乐 section-title 样式（小号加粗 + 三级文字色） */
    private static JLabel headerLabel(String text, int width) {
        JLabel label = new JLabel(text.toUpperCase());
        label.setFont(ThemeUtil.FONT_SMALL.deriveFont(Font.BOLD));
        ThemeUtil.setDynamicForeground(label, () -> ThemeUtil.TEXT_TERTIARY);
        label.setPreferredSize(new Dimension(width, 18));
        return label;
    }

    /** 创建空状态面板（虚线边框拖拽区，参考蓝韵音乐 upload zone 设计） */
    private JPanel createEmptyPanel() {
        // 外层虚线边框拖拽区
        JPanel dropZone = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                float[] dash = {6f, 4f};
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND, 0, dash, 0));
                g2.setColor(ThemeUtil.BORDER);
                int arc = ThemeUtil.ARC_CARD;
                g2.drawRoundRect(2, 2, getWidth() - 4, getHeight() - 4, arc, arc);
                g2.dispose();
            }
        };
        dropZone.setOpaque(false);
        dropZone.setBorder(BorderFactory.createEmptyBorder(32, 32, 32, 32));

        JPanel innerPanel = new JPanel(new BorderLayout(0, ThemeUtil.SPACE_MD));
        innerPanel.setOpaque(false);

        // Emoji 图标
        JLabel iconLabel = new JLabel("📁", SwingConstants.CENTER);
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 40));
        innerPanel.add(iconLabel, BorderLayout.NORTH);

        // 两行引导文字（参考蓝韵空状态样式）
        JPanel textPanel = new JPanel(new BorderLayout(0, 4));
        textPanel.setOpaque(false);
        JLabel guideTitle = new JLabel("拖拽文件到此处", SwingConstants.CENTER);
        guideTitle.setFont(ThemeUtil.FONT_TITLE);
        ThemeUtil.setDynamicForeground(guideTitle, () -> ThemeUtil.TEXT_PRIMARY);
        JLabel guideSub = new JLabel("或点击「导入文件」按钮添加", SwingConstants.CENTER);
        guideSub.setFont(ThemeUtil.FONT_SMALL);
        ThemeUtil.setDynamicForeground(guideSub, () -> ThemeUtil.TEXT_TERTIARY);
        textPanel.add(guideTitle, BorderLayout.NORTH);
        textPanel.add(guideSub, BorderLayout.SOUTH);

        innerPanel.add(textPanel, BorderLayout.CENTER);
        dropZone.add(innerPanel, BorderLayout.CENTER);

        return dropZone;
    }

    /** 切换空状态/列表显示 */
    private void toggleEmptyState() {
        boolean empty = listModel.isEmpty();
        centerLayout.show(centerPanel, empty ? "empty" : "list");
    }

    // ==================== 数据操作（FileInfo 统一接口） ====================

    /**
     * 批量设置文件列表（替换全部内容）。
     *
     * @param files 文件信息列表（可为 ImageFileInfo 或 VideoFileInfo）
     */
    public void setFileList(List<? extends FileInfo> files) {
        allFiles.clear();
        allFiles.addAll(files);
        applyFilter();
    }

    /**
     * 添加单个文件到列表。
     *
     * @param file 文件信息（可为 ImageFileInfo 或 VideoFileInfo）
     */
    public void addFile(FileInfo file) {
        allFiles.add(file);
        applyFilter();
    }

    /**
     * 移除指定索引的文件。
     */
    public void removeFile(int index) {
        if (index >= 0 && index < listModel.size()) {
            FileInfo removed = listModel.remove(index);
            allFiles.remove(removed);
            updateStats();
        }
    }

    /**
     * 清空所有文件。
     */
    public void clearAllFiles() {
        allFiles.clear();
        listModel.clear();
        searchField.setText("");
        currentQuery = "";
        thumbnailCache.clear();
        largeThumbnailCache.clear();
        updateStats();
    }

    // ==================== 搜索/排序（v2.2） ====================

    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new BorderLayout(ThemeUtil.SPACE_SM, 0));
        panel.setOpaque(false);

        // 搜索框
        searchField = new JTextField();
        searchField.setFont(ThemeUtil.FONT_SMALL);
        searchField.putClientProperty("JTextField.placeholderText", "搜索文件名...");
        searchField.putClientProperty("JTextField.showClearButton", true);
        searchField.setBorder(ThemeUtil.createDynamicLineBorder());
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                currentQuery = searchField.getText().trim().toLowerCase();
                applyFilter();
            }
        });
        panel.add(searchField, BorderLayout.CENTER);

        // 排序下拉
        sortCombo = new JComboBox<>(new String[]{"原始顺序", "按名称", "按大小"});
        sortCombo.setFont(ThemeUtil.FONT_SMALL);
        sortCombo.setBorder(ThemeUtil.createDynamicLineBorder());
        sortCombo.addActionListener(e -> {
            currentSort = sortCombo.getSelectedIndex();
            applyFilter();
        });
        panel.add(sortCombo, BorderLayout.EAST);

        return panel;
    }

    /**
     * 根据搜索关键词和排序方式重建显示列表。
     */
    private void applyFilter() {
        listModel.clear();

        for (FileInfo file : allFiles) {
            if (currentQuery.isEmpty()
                    || file.getFileName().toLowerCase().contains(currentQuery)) {
                listModel.addElement(file);
            }
        }

        // 排序
        if (currentSort == 1) {
            sortByName();
        } else if (currentSort == 2) {
            sortBySize();
        }

        updateStats();
    }

    private void sortByName() {
        List<FileInfo> sorted = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) sorted.add(listModel.get(i));
        Collections.sort(sorted, new Comparator<FileInfo>() {
            @Override
            public int compare(FileInfo a, FileInfo b) {
                return a.getFileName().compareToIgnoreCase(b.getFileName());
            }
        });
        listModel.clear();
        for (FileInfo f : sorted) listModel.addElement(f);
    }

    private void sortBySize() {
        List<FileInfo> sorted = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) sorted.add(listModel.get(i));
        Collections.sort(sorted, new Comparator<FileInfo>() {
            @Override
            public int compare(FileInfo a, FileInfo b) {
                return Long.compare(a.getOriginalSize(), b.getOriginalSize());
            }
        });
        listModel.clear();
        for (FileInfo f : sorted) listModel.addElement(f);
    }

    /**
     * 更新指定索引文件的状态（使用 FileInfo.Status 统一枚举）。
     */
    public void updateFileStatus(int index, FileInfo.Status status) {
        if (index >= 0 && index < listModel.size()) {
            listModel.get(index).setFileInfoStatus(status);
            fileList.repaint();
        }
    }

    /**
     * 获取当前选中的索引。
     */
    public int getSelectedIndex() { return fileList.getSelectedIndex(); }

    /**
     * 获取当前选中的文件信息。
     */
    public FileInfo getSelectedFile() { return fileList.getSelectedValue(); }

    /**
     * 获取文件列表（返回 FileInfo 列表，调用方按需 cast）。
     */
    public List<FileInfo> getFileList() {
        List<FileInfo> files = new ArrayList<>(listModel.size());
        for (int i = 0; i < listModel.size(); i++) {
            files.add(listModel.get(i));
        }
        return files;
    }

    /**
     * 获取文件列表中的图片文件（过滤掉视频文件）。
     * 用于兼容旧代码中需要 {@code List<ImageFileInfo>} 的场景。
     */
    @SuppressWarnings("unchecked")
    public List<ImageFileInfo> getImageFileList() {
        List<ImageFileInfo> images = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) {
            FileInfo info = listModel.get(i);
            if (info instanceof ImageFileInfo) {
                images.add((ImageFileInfo) info);
            }
        }
        return images;
    }

    /**
     * 获取文件列表中的视频文件。
     */
    @SuppressWarnings("unchecked")
    public List<VideoFileInfo> getVideoFileList() {
        List<VideoFileInfo> videos = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) {
            FileInfo info = listModel.get(i);
            if (info instanceof VideoFileInfo) {
                videos.add((VideoFileInfo) info);
            }
        }
        return videos;
    }

    public JButton getClearButton() { return clearButton; }
    public JList<FileInfo> getFileJList() { return fileList; }

    // ==================== 缩略图 ====================

    /**
     * 异步加载文件缩略图（仅对图片文件生效，视频文件跳过）。
     * 同时预缓存 200×200 大缩略图供悬停预览使用。
     * 应在文件导入后调用。
     */
    public void loadThumbnail(FileInfo info) {
        // 视频文件无缩略图（悬停预览走 FFmpeg 异步提取）
        if (info.getFileType() == FileInfo.FileType.VIDEO) {
            return;
        }
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

        // 同时预生成 200×200 大缩略图（供悬停预览）
        String largeKey = key + "#200";
        if (!largeThumbnailCache.containsKey(largeKey)) {
            try {
                BufferedImage largeThumb = Thumbnails.of(src)
                        .size(200, 200)
                        .asBufferedImage();
                largeThumbnailCache.put(largeKey, largeThumb);
            } catch (IOException ignored) {
                // 大缩略图生成失败不影响列表显示
            }
        }
    }

    /** 获取缓存的缩略图（可能为 null） */
    public BufferedImage getThumbnail(String path) {
        return thumbnailCache.get(path);
    }

    /** 获取悬停预览弹窗（供 Controller 在窗口关闭时 dispose） */
    public HoverPreviewPopup getHoverPopup() {
        return hoverPopup;
    }

    // ==================== 悬停预览 ====================

    /**
     * 悬停计时器触发：显示当前悬停文件的预览弹窗。
     */
    private void triggerHoverPreview() {
        if (hoveredIndex < 0 || hoveredIndex >= listModel.size()) return;
        FileInfo info = listModel.get(hoveredIndex);
        if (info == null || info.getSourceFile() == null) return;

        // 转换坐标为屏幕坐标
        if (lastMousePoint == null) return;
        java.awt.Point screenPoint = new java.awt.Point(lastMousePoint);
        javax.swing.SwingUtilities.convertPointToScreen(screenPoint, fileList);

        if (info.getFileType() == FileInfo.FileType.IMAGE) {
            // 图片：从大缩略图缓存取
            String largeKey = info.getSourceFile().getAbsolutePath() + "#200";
            BufferedImage largeThumb = largeThumbnailCache.get(largeKey);
            if (largeThumb != null) {
                hoverPopup.show(screenPoint, largeThumb);
            }
        } else if (info.getFileType() == FileInfo.FileType.VIDEO) {
            // 视频：检查缓存，未命中则异步提取
            String videoKey = info.getSourceFile().getAbsolutePath() + "#video200";
            BufferedImage cached = largeThumbnailCache.get(videoKey);
            if (cached != null) {
                hoverPopup.show(screenPoint, cached);
            } else {
                // 异步提取视频首帧
                final int idx = hoveredIndex;
                final java.awt.Point pt = new java.awt.Point(screenPoint);
                new Thread(() -> {
                    try {
                        Process proc = com.nchu.imagecompress.util.FfmpegRenderUtil
                                .startSingleFrame(info.getSourceFile(), 200, 0);
                        java.awt.Dimension dim = com.nchu.imagecompress.util.FfmpegRenderUtil
                                .calculatePreviewSize(
                                        ((com.nchu.imagecompress.model.VideoFileInfo) info).getWidth(),
                                        ((com.nchu.imagecompress.model.VideoFileInfo) info).getHeight(),
                                        200);
                        byte[] rgb = com.nchu.imagecompress.util.FfmpegRenderUtil
                                .readFrame(proc.getInputStream(), dim.width, dim.height);
                        if (rgb != null) {
                            BufferedImage frame = com.nchu.imagecompress.util.FfmpegRenderUtil
                                    .rgbToImage(rgb, dim.width, dim.height);
                            largeThumbnailCache.put(videoKey, frame);
                            // 只有当前悬停行没变时才显示
                            if (hoveredIndex == idx) {
                                javax.swing.SwingUtilities.invokeLater(() ->
                                        hoverPopup.show(pt, frame));
                            }
                        }
                        proc.destroy();
                    } catch (Exception ignored) {
                        // 视频首帧提取失败，静默跳过
                    }
                }, "Video-Thumbnail-Extractor").start();
            }
        }
    }

    /** 取消悬停：隐藏弹窗 + 停止计时器 + 重置状态 */
    private void cancelHover() {
        hoverTimer.stop();
        hoverPopup.hide();
        hoveredIndex = -1;
        lastMousePoint = null;
    }

    // ==================== 内部方法 ====================

    private void updateStats() {
        int count = listModel.size();
        long totalBytes = 0;
        int imageCount = 0;
        int videoCount = 0;
        for (int i = 0; i < count; i++) {
            FileInfo info = listModel.get(i);
            totalBytes += info.getOriginalSize();
            if (info.getFileType() == FileInfo.FileType.VIDEO) {
                videoCount++;
            } else {
                imageCount++;
            }
        }
        // 构建统计文本
        StringBuilder sb = new StringBuilder("共 ").append(count).append(" 个文件");
        if (imageCount > 0 && videoCount > 0) {
            sb.append("（").append(imageCount).append(" 图片 + ").append(videoCount).append(" 视频）");
        } else if (videoCount > 0) {
            sb.append("（视频）");
        }
        sb.append(" · 总计 ").append(FileUtil.formatFileSize(totalBytes));
        statsLabel.setText(sb.toString());
        clearButton.setEnabled(count > 0);
        toggleEmptyState();
    }


    // ==================== 缩略图列表渲染器（双模：图片 + 视频） ====================

    /**
     * 列表单元格渲染器（v2.1 升级 — 支持图片 + 视频双模显示）。
     *
     * <p>结构：左侧 2px 强调条 | 24px 图标/缩略图 | 文件名 | 大小 | 信息(尺寸或时长) | 状态</p>
     * <p>通过 {@code instanceof} 区分图片和视频的差异化显示。</p>
     */
    private class ThumbnailCellRenderer extends JPanel implements ListCellRenderer<FileInfo> {

        private final JPanel accentBar;
        private final JLabel thumbLabel;
        private final JLabel nameLabel;
        private final JLabel sizeLabel;
        private final JLabel infoLabel;
        private final JLabel statusLabel;

        ThumbnailCellRenderer() {
            setLayout(new BorderLayout(0, 0));
            setOpaque(true);

            // 左侧强调条
            accentBar = new JPanel();
            accentBar.setPreferredSize(new Dimension(2, 44));
            accentBar.setBackground(ThemeUtil.PRIMARY);
            accentBar.setOpaque(true);

            // 图标/缩略图
            thumbLabel = new JLabel();
            thumbLabel.setPreferredSize(new Dimension(24, 24));
            thumbLabel.setHorizontalAlignment(SwingConstants.CENTER);

            // 文件名
            nameLabel = new JLabel();
            nameLabel.setFont(ThemeUtil.FONT_BODY);

            // 大小
            sizeLabel = new JLabel();
            sizeLabel.setFont(ThemeUtil.FONT_SMALL);

            // 信息列（图片：尺寸 / 视频：时长）
            infoLabel = new JLabel();
            infoLabel.setFont(ThemeUtil.FONT_SMALL);
            ThemeUtil.setDynamicForeground(infoLabel, () -> ThemeUtil.TEXT_TERTIARY);

            // 状态
            statusLabel = new JLabel();
            statusLabel.setFont(ThemeUtil.FONT_SMALL);
            statusLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            statusLabel.setPreferredSize(new Dimension(60, 44));

            // 组装
            JPanel textPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
            textPanel.setOpaque(false);
            textPanel.add(nameLabel);
            textPanel.add(sizeLabel);
            textPanel.add(infoLabel);

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
                JList<? extends FileInfo> list, FileInfo value,
                int index, boolean isSelected, boolean cellHasFocus) {

            if (value == null) {
                resetDisplay();
                return this;
            }

            boolean isVideo = (value instanceof VideoFileInfo);
            boolean isImage = (value instanceof ImageFileInfo);

            // === 图标 / 缩略图 ===
            if (isImage) {
                // 图片：优先使用缓存的真实缩略图，回退为 emoji
                BufferedImage cached = (value.getSourceFile() != null)
                        ? thumbnailCache.get(value.getSourceFile().getAbsolutePath()) : null;
                if (cached != null) {
                    thumbLabel.setIcon(new javax.swing.ImageIcon(cached));
                    thumbLabel.setText("");
                } else {
                    thumbLabel.setIcon(getFormatIcon(getExt(value.getFileName()), false));
                    thumbLabel.setText("");
                }
            } else {
                // 视频：使用 SVG 图标
                thumbLabel.setIcon(getFormatIcon(getExt(value.getFileName()), true));
                thumbLabel.setText("");
            }

            // === 文件名 ===
            nameLabel.setText(value.getFileName());
            ThemeUtil.setDynamicForeground(nameLabel, () -> ThemeUtil.TEXT_PRIMARY);

            // === 大小 ===
            sizeLabel.setText(value.getFormattedSize());
            ThemeUtil.setDynamicForeground(sizeLabel, () -> ThemeUtil.TEXT_SECONDARY);

            // === 信息列（图片：尺寸 / 视频：时长） ===
            if (isImage) {
                ImageFileInfo imgInfo = (ImageFileInfo) value;
                if (imgInfo.getWidth() > 0 && imgInfo.getHeight() > 0) {
                    infoLabel.setText(imgInfo.getWidth() + "×" + imgInfo.getHeight());
                    infoLabel.setVisible(true);
                } else {
                    infoLabel.setVisible(false);
                }
            } else if (isVideo) {
                String duration = value.getDurationString();
                if (!duration.isEmpty()) {
                    infoLabel.setText(duration);
                    infoLabel.setVisible(true);
                } else {
                    infoLabel.setText("--:--");
                    infoLabel.setVisible(true);
                }
            } else {
                infoLabel.setVisible(false);
            }

            // === 状态 ===
            switch (value.getFileInfoStatus()) {
                case SUCCESS:
                    statusLabel.setText("● 成功");
                    ThemeUtil.setDynamicForeground(statusLabel, () -> ThemeUtil.SUCCESS);
                    break;
                case FAILED:
                    statusLabel.setText("● 失败");
                    ThemeUtil.setDynamicForeground(statusLabel, () -> ThemeUtil.ERROR);
                    break;
                case PROCESSING:
                    statusLabel.setText("● 处理中");
                    ThemeUtil.setDynamicForeground(statusLabel, () -> ThemeUtil.PRIMARY);
                    break;
                default:
                    statusLabel.setText("待处理");
                    ThemeUtil.setDynamicForeground(statusLabel, () -> ThemeUtil.TEXT_SECONDARY);
            }

            // v2.5: 有逐文件参数覆盖时在文件名后加 ⚙ 标记
            if (isImage && ((ImageFileInfo) value).hasPerFileConfig()) {
                String currentText = nameLabel.getText();
                if (!currentText.contains("⚙")) {
                    nameLabel.setText(currentText + "  ⚙");
                }
                ThemeUtil.setDynamicForeground(nameLabel, () -> ThemeUtil.PRIMARY);
            }

            // === 背景与强调条 ===
            if (isSelected) {
                setBackground(ThemeUtil.BG_SELECTED);
                accentBar.setVisible(true);
                nameLabel.setFont(ThemeUtil.FONT_TITLE);
            } else if (value.getFileInfoStatus() == FileInfo.Status.FAILED) {
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
            thumbLabel.setIcon(null);
            thumbLabel.setText("");
            nameLabel.setText("");
            sizeLabel.setText("");
            infoLabel.setText("");
            statusLabel.setText("");
            accentBar.setVisible(false);
            setBackground(ThemeUtil.BG_CARD);
        }

        private String getExt(String name) {
            int dot = name.lastIndexOf('.');
            return dot > 0 ? name.substring(dot + 1).toLowerCase() : "";
        }

        /**
         * 根据扩展名返回对应 SVG 图标（替代 emoji，消除方框问题）。
         *
         * @param ext    文件扩展名（小写）
         * @param isVideo 是否为视频文件
         */
        private javax.swing.Icon getFormatIcon(String ext, boolean isVideo) {
            if (isVideo) {
                return new FlatSVGIcon("icons/film.svg");
            }
            // 图片格式统一用图片图标
            return new FlatSVGIcon("icons/image.svg");
        }
    }
}
