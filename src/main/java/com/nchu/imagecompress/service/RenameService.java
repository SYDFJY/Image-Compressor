package com.nchu.imagecompress.service;

import com.nchu.imagecompress.model.FileInfo;
import com.nchu.imagecompress.model.RenameRule;
import com.nchu.imagecompress.util.LogUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 批量重命名服务 — 预览生成 + 磁盘执行。
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-22
 */
public class RenameService {

    /**
     * 重命名条目（预览用）。
     */
    public static class RenameEntry {
        public final String oldName;
        public final String newName;
        public final Status status;
        public final String message;

        public enum Status { OK, CONFLICT, INVALID_CHARS, EMPTY }

        public RenameEntry(String oldName, String newName, Status status, String message) {
            this.oldName = oldName;
            this.newName = newName;
            this.status = status;
            this.message = message;
        }

        public boolean isOk() { return status == Status.OK; }
    }

    /**
     * 预览重命名结果（不修改磁盘文件）。
     *
     * @param files 待重命名文件列表
     * @param rule  重命名规则
     * @return 每个文件的重命名条目
     */
    public List<RenameEntry> preview(List<FileInfo> files, RenameRule rule) {
        List<RenameEntry> entries = new ArrayList<>();
        Set<String> newNameSet = new HashSet<>();

        // 第一遍：生成所有新名称
        String[] newNames = new String[files.size()];
        for (int i = 0; i < files.size(); i++) {
            newNames[i] = rule.apply(files.get(i), i + 1);
        }

        // 第二遍：检测冲突
        for (int i = 0; i < files.size(); i++) {
            FileInfo info = files.get(i);
            String oldName = info.getFileName();
            String newName = newNames[i];

            if (newName == null || newName.trim().isEmpty()) {
                entries.add(new RenameEntry(oldName, newName,
                        RenameEntry.Status.EMPTY, "新文件名为空"));
                continue;
            }

            if (!RenameRule.isValidFilename(newName)) {
                entries.add(new RenameEntry(oldName, newName,
                        RenameEntry.Status.INVALID_CHARS, "包含非法字符"));
                continue;
            }

            // 冲突检测：与其他文件的新名称重复
            if (newNameSet.contains(newName.toLowerCase())) {
                entries.add(new RenameEntry(oldName, newName,
                        RenameEntry.Status.CONFLICT, "与其他文件名冲突"));
                continue;
            }
            newNameSet.add(newName.toLowerCase());

            // 冲突检测：与现有文件名重复（且不是自身）
            boolean selfConflict = false;
            for (int j = 0; j < files.size(); j++) {
                if (i != j && files.get(j).getFileName().equalsIgnoreCase(newName)) {
                    selfConflict = true;
                    break;
                }
            }
            if (selfConflict) {
                entries.add(new RenameEntry(oldName, newName,
                        RenameEntry.Status.CONFLICT, "与列表中已有文件名冲突"));
                continue;
            }

            entries.add(new RenameEntry(oldName, newName,
                    RenameEntry.Status.OK, "OK"));
        }

        return entries;
    }

    /**
     * 执行磁盘重命名。
     *
     * @param files   待重命名文件列表
     * @param rule    重命名规则
     * @param entries 预览结果（用于重用已计算的名称）
     * @return 成功重命名的文件数
     */
    public int execute(List<FileInfo> files, RenameRule rule, List<RenameEntry> entries) {
        int successCount = 0;
        for (int i = 0; i < files.size(); i++) {
            RenameEntry entry = entries.get(i);
            if (!entry.isOk()) continue;

            FileInfo info = files.get(i);
            File oldFile = info.getSourceFile();
            if (oldFile == null || !oldFile.exists()) continue;

            File newFile = new File(oldFile.getParent(), entry.newName);

            // 安全检查：目标文件不应已存在（除非是自身）
            if (newFile.exists() && !newFile.equals(oldFile)) {
                LogUtil.info("[RenameService] 跳过冲突: " + entry.oldName + " → " + entry.newName);
                continue;
            }

            if (oldFile.renameTo(newFile)) {
                info.setSourceFile(newFile);
                info.setFileName(newFile.getName());
                successCount++;
                LogUtil.info("[RenameService] 重命名成功: " + entry.oldName + " → " + entry.newName);
            } else {
                LogUtil.info("[RenameService] 重命名失败: " + entry.oldName + " → " + entry.newName);
            }
        }
        return successCount;
    }
}
