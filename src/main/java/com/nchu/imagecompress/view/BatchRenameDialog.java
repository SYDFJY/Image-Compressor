package com.nchu.imagecompress.view;

import com.nchu.imagecompress.model.FileInfo;
import com.nchu.imagecompress.model.RenameRule;
import com.nchu.imagecompress.service.RenameService;
import com.nchu.imagecompress.util.ThemeUtil;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.function.Consumer;

/**
 * 批量重命名对话框 — 规则编辑器 + 预览表格 + 应用按钮。
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-22
 */
public class BatchRenameDialog extends JDialog {

    private final RenameService renameService = new RenameService();
    private final RenameRule rule = new RenameRule();
    private final List<FileInfo> fileList;
    private final Consumer<List<FileInfo>> onApply;

    private JTextField patternField;
    private JTextField customTextField;
    private JSpinner counterStartSpinner;
    private JSpinner counterDigitsSpinner;
    private JComboBox<String> datePatternCombo;
    private JTable previewTable;
    private DefaultTableModel tableModel;
    private JButton applyButton;
    private JLabel statusLabel;

    private List<RenameService.RenameEntry> lastPreviewEntries;

    /**
     * @param owner    父窗口
     * @param fileList 待重命名文件列表
     * @param onApply  应用回调（收到更新后的 FileInfo 列表）
     */
    public BatchRenameDialog(Frame owner, List<FileInfo> fileList,
                             Consumer<List<FileInfo>> onApply) {
        super(owner, "批量重命名", true);
        this.fileList = fileList;
        this.onApply = onApply;

        setResizable(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout(0, 12));
        mainPanel.setBackground(ThemeUtil.BG_CARD);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        // === 顶部：规则编辑 ===
        mainPanel.add(createRulePanel(), BorderLayout.NORTH);

        // === 中部：预览表格 ===
        mainPanel.add(createPreviewPanel(), BorderLayout.CENTER);

        // === 底部：按钮 ===
        mainPanel.add(createButtonPanel(), BorderLayout.SOUTH);

        add(mainPanel);
        setSize(700, 520);
        setLocationRelativeTo(owner);

        // ESC 关闭
        getRootPane().registerKeyboardAction(
                e -> dispose(),
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);

        // 初始预览
        refreshPreview();

        // 显示模态对话框
        setVisible(true);
    }

    private JPanel createRulePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(ThemeUtil.BG_HOVER);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeUtil.BORDER, 1),
                BorderFactory.createEmptyBorder(12, 12, 8, 12)));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;

        // 模板输入
        addLabel(panel, gbc, "模板:", row);
        patternField = new JTextField("{date}_{counter:3}");
        patternField.setFont(ThemeUtil.FONT_BODY);
        patternField.setBorder(ThemeUtil.createDynamicLineBorder());
        patternField.setToolTipText("{date} {counter} {name} {ext} {text}  |  示例: {date}_{text}_{counter:3}");
        patternField.getDocument().addDocumentListener(new SimpleDocumentListener(this::refreshPreview));
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1.0;
        panel.add(patternField, gbc);
        row++;

        // 自定义文本
        addLabel(panel, gbc, "自定义文本:", row);
        customTextField = new JTextField();
        customTextField.setFont(ThemeUtil.FONT_BODY);
        customTextField.setBorder(ThemeUtil.createDynamicLineBorder());
        customTextField.getDocument().addDocumentListener(new SimpleDocumentListener(this::refreshPreview));
        gbc.gridx = 1; gbc.gridy = row;
        panel.add(customTextField, gbc);
        row++;

        // 起始序号 + 位数 + 日期格式（同行）
        addLabel(panel, gbc, "起始序号:", row);
        JPanel counterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        counterPanel.setOpaque(false);
        counterStartSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 9999, 1));
        counterStartSpinner.setFont(ThemeUtil.FONT_SMALL);
        counterStartSpinner.addChangeListener(e -> refreshPreview());
        counterPanel.add(counterStartSpinner);
        counterPanel.add(new JLabel("位数:"));
        counterDigitsSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 6, 1));
        counterDigitsSpinner.setFont(ThemeUtil.FONT_SMALL);
        counterDigitsSpinner.addChangeListener(e -> refreshPreview());
        counterPanel.add(counterDigitsSpinner);
        counterPanel.add(new JLabel("日期格式:"));
        datePatternCombo = new JComboBox<>(new String[]{"yyyyMMdd", "yyyy-MM-dd", "yyyyMMdd_HHmmss", "自动检测"});
        datePatternCombo.setFont(ThemeUtil.FONT_SMALL);
        datePatternCombo.addActionListener(e -> refreshPreview());
        counterPanel.add(datePatternCombo);
        gbc.gridx = 1; gbc.gridy = row;
        panel.add(counterPanel, gbc);
        row++;

        // Token 提示
        JLabel tokenHint = new JLabel("{date}=日期  {counter}=序号  {name}=原名  {ext}=扩展名  {text}=自定义文本");
        tokenHint.setFont(ThemeUtil.FONT_TINY);
        tokenHint.setForeground(ThemeUtil.TEXT_TERTIARY);
        gbc.gridx = 1; gbc.gridy = row; gbc.gridwidth = 2;
        panel.add(tokenHint, gbc);
        row++;

        // v2.5: 预设模板快捷按钮
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        JPanel presetPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        presetPanel.setOpaque(false);

        JButton presetDateSeq = new JButton("日期+序号");
        presetDateSeq.setFont(ThemeUtil.FONT_SMALL);
        presetDateSeq.setFocusPainted(false);
        presetDateSeq.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        presetDateSeq.setToolTipText("模板: {date}_{counter:3} → 20260722_001.jpg");
        presetDateSeq.addActionListener(e -> {
            patternField.setText("{date}_{counter:3}");
            customTextField.setText("");
            refreshPreview();
        });

        JButton presetCustomSeq = new JButton("自定义文本+序号");
        presetCustomSeq.setFont(ThemeUtil.FONT_SMALL);
        presetCustomSeq.setFocusPainted(false);
        presetCustomSeq.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        presetCustomSeq.setToolTipText("模板: {text}_{counter:3} — 请在「自定义文本」中填入内容");
        presetCustomSeq.addActionListener(e -> {
            patternField.setText("{text}_{counter:3}");
            refreshPreview();
        });

        JButton presetNameSeq = new JButton("原名+序号");
        presetNameSeq.setFont(ThemeUtil.FONT_SMALL);
        presetNameSeq.setFocusPainted(false);
        presetNameSeq.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        presetNameSeq.setToolTipText("模板: {name}_{counter:3} → photo_001.jpg");
        presetNameSeq.addActionListener(e -> {
            patternField.setText("{name}_{counter:3}");
            customTextField.setText("");
            refreshPreview();
        });

        JButton presetKeepName = new JButton("保留原名");
        presetKeepName.setFont(ThemeUtil.FONT_SMALL);
        presetKeepName.setFocusPainted(false);
        presetKeepName.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        presetKeepName.setToolTipText("模板: {name} → 保持原文件名不变");
        presetKeepName.addActionListener(e -> {
            patternField.setText("{name}");
            customTextField.setText("");
            refreshPreview();
        });

        presetPanel.add(presetDateSeq);
        presetPanel.add(presetCustomSeq);
        presetPanel.add(presetNameSeq);
        presetPanel.add(presetKeepName);
        panel.add(presetPanel, gbc);
        row++;

        return panel;
    }

    private JPanel createPreviewPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);

        JLabel title = new JLabel("预览");
        title.setFont(ThemeUtil.FONT_TITLE.deriveFont(13f));
        title.setForeground(ThemeUtil.TEXT_PRIMARY);
        panel.add(title, BorderLayout.NORTH);

        String[] columns = {"序号", "原文件名", "新文件名", "状态"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        previewTable = new JTable(tableModel);
        previewTable.setFont(ThemeUtil.FONT_SMALL);
        previewTable.setRowHeight(26);
        previewTable.getColumnModel().getColumn(0).setPreferredWidth(45);
        previewTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        previewTable.getColumnModel().getColumn(2).setPreferredWidth(200);
        previewTable.getColumnModel().getColumn(3).setPreferredWidth(80);

        // 状态列颜色渲染
        previewTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                Component c = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);
                if ("OK".equals(value)) {
                    c.setForeground(ThemeUtil.SUCCESS);
                } else {
                    c.setForeground(ThemeUtil.ERROR);
                }
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(previewTable);
        scrollPane.setPreferredSize(new Dimension(640, 240));
        scrollPane.setBorder(BorderFactory.createLineBorder(ThemeUtil.BORDER, 1));
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        statusLabel = new JLabel("共 " + fileList.size() + " 个文件");
        statusLabel.setFont(ThemeUtil.FONT_SMALL);
        statusLabel.setForeground(ThemeUtil.TEXT_SECONDARY);
        panel.add(statusLabel, BorderLayout.WEST);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnPanel.setOpaque(false);

        JButton cancelBtn = new JButton("取消");
        cancelBtn.setFont(ThemeUtil.FONT_BODY);
        cancelBtn.addActionListener(e -> dispose());
        btnPanel.add(cancelBtn);

        applyButton = new JButton("应用重命名");
        applyButton.setFont(ThemeUtil.FONT_TITLE);
        applyButton.setForeground(Color.WHITE);
        applyButton.setBackground(ThemeUtil.PRIMARY);
        applyButton.setFocusPainted(false);
        applyButton.setBorder(BorderFactory.createEmptyBorder(6, 20, 6, 20));
        applyButton.addActionListener(e -> executeRename());
        btnPanel.add(applyButton);

        panel.add(btnPanel, BorderLayout.EAST);
        return panel;
    }

    private void refreshPreview() {
        // 同步 UI → rule
        rule.setPattern(patternField.getText().trim());
        rule.setCustomText(customTextField.getText().trim());
        rule.setCounterStart((int) counterStartSpinner.getValue());
        rule.setCounterDigits((int) counterDigitsSpinner.getValue());
        String dateFmt = (String) datePatternCombo.getSelectedItem();
        if (!"自动检测".equals(dateFmt)) {
            rule.setDatePattern(dateFmt);
        }

        // 预览
        lastPreviewEntries = renameService.preview(fileList, rule);

        // 更新表格
        tableModel.setRowCount(0);
        int conflictCount = 0;
        for (int i = 0; i < lastPreviewEntries.size(); i++) {
            RenameService.RenameEntry entry = lastPreviewEntries.get(i);
            tableModel.addRow(new Object[]{
                    i + 1,
                    entry.oldName,
                    entry.newName,
                    entry.isOk() ? "OK" : entry.message
            });
            if (!entry.isOk()) conflictCount++;
        }

        // 状态标签
        if (conflictCount > 0) {
            statusLabel.setText("共 " + fileList.size() + " 个文件 — " + conflictCount + " 个冲突");
            statusLabel.setForeground(ThemeUtil.ERROR);
        } else {
            statusLabel.setText("共 " + fileList.size() + " 个文件 — 全部就绪");
            statusLabel.setForeground(ThemeUtil.SUCCESS);
        }

        // 禁用应用按钮（如有冲突）
        applyButton.setEnabled(conflictCount == 0 && !fileList.isEmpty());
    }

    private void executeRename() {
        if (lastPreviewEntries == null) return;

        int choice = JOptionPane.showConfirmDialog(this,
                "确定要重命名 " + fileList.size() + " 个文件吗？\n此操作不可撤销。",
                "确认重命名", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;

        int count = renameService.execute(fileList, rule, lastPreviewEntries);
        dispose();

        if (onApply != null) {
            onApply.accept(fileList);
        }

        ToastNotification.success("已重命名 " + count + " 个文件");
    }

    private static void addLabel(JPanel panel, GridBagConstraints gbc, String text, int row) {
        JLabel label = new JLabel(text);
        label.setFont(ThemeUtil.FONT_SMALL);
        label.setForeground(ThemeUtil.TEXT_PRIMARY);
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        panel.add(label, gbc);
    }

    /**
     * 简化 DocumentListener（只关注 insert/remove/change 中的任意一种变化）。
     */
    private static class SimpleDocumentListener implements DocumentListener {
        private final Runnable callback;
        SimpleDocumentListener(Runnable callback) { this.callback = callback; }
        @Override public void insertUpdate(DocumentEvent e) { callback.run(); }
        @Override public void removeUpdate(DocumentEvent e) { callback.run(); }
        @Override public void changedUpdate(DocumentEvent e) { callback.run(); }
    }
}
