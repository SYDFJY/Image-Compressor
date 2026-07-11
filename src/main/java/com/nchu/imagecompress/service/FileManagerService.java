package com.nchu.imagecompress.service;

import com.nchu.imagecompress.model.ImageFileInfo;
import com.nchu.imagecompress.util.FileUtil;
import com.nchu.imagecompress.util.ImageUtil;
import com.nchu.imagecompress.util.LogUtil;

import javax.swing.ImageIcon;
import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 文件管理服务。
 *
 * <p>负责图片文件的导入、过滤、去重和缩略图管理。
 * 支持从单文件、多文件、文件夹导入，自动过滤不支持的格式。</p>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-08
 */
public class FileManagerService {

    /** 单次导入最大文件数（防止界面卡顿） */
    private static final int MAX_FILES_PER_IMPORT = 500;

    // ==================== 文件导入 ====================

    /**
     * 从文件列表导入图片。
     * <p>自动过滤非图片格式和损坏文件，去重，生成 ImageFileInfo 列表。</p>
     *
     * @param files 用户选择的文件数组
     * @return 有效的图片文件信息列表
     */
    public List<ImageFileInfo> importFiles(File[] files) {
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }

        List<ImageFileInfo> result = new ArrayList<>();
        Set<String> seenPaths = new HashSet<>();
        int skippedCount = 0;
        int unsupportedCount = 0;

        for (File file : files) {
            // 数量上限
            if (result.size() >= MAX_FILES_PER_IMPORT) {
                LogUtil.warning("[FileManagerService] 达到单次导入上限 " + MAX_FILES_PER_IMPORT + "，跳过: " + file.getName());
                break;
            }

            // 去重（基于绝对路径）
            String absPath = file.getAbsolutePath();
            if (seenPaths.contains(absPath)) {
                continue;
            }
            seenPaths.add(absPath);

            // 格式检查
            if (!FileUtil.isImageFile(file)) {
                skippedCount++;
                continue;
            }

            if (FileUtil.isUnsupportedFormat(file)) {
                unsupportedCount++;
                // 不支持但可识别的格式也添加，标记为特殊状态
                ImageFileInfo info = new ImageFileInfo(file);
                info.setErrorMessage("暂不支持的文件格式");
                result.add(info);
                continue;
            }

            // 正常导入
            ImageFileInfo info = createImageFileInfo(file);
            result.add(info);
        }

        if (skippedCount > 0) {
            LogUtil.info("[FileManagerService] 已跳过 " + skippedCount + " 个非图片文件");
        }
        if (unsupportedCount > 0) {
            LogUtil.info("[FileManagerService] 已标记 " + unsupportedCount + " 个不支持格式的文件");
        }

        return result;
    }

    /**
     * 从文件夹导入图片。
     *
     * @param folder    文件夹
     * @param recursive 是否递归扫描子文件夹
     * @return 图片文件信息列表
     */
    public List<ImageFileInfo> importFolder(File folder, boolean recursive) {
        if (folder == null || !folder.isDirectory()) {
            return Collections.emptyList();
        }

        List<File> imageFiles = new ArrayList<>();
        scanFolder(folder, recursive, imageFiles);

        return importFiles(imageFiles.toArray(new File[0]));
    }

    /**
     * 递归或非递归扫描文件夹中的图片文件。
     */
    private void scanFolder(File folder, boolean recursive, List<File> collector) {
        File[] files = folder.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                if (file.isHidden()) return false;
                if (file.isDirectory()) return true;
                return FileUtil.isImageFile(file);
            }
        });

        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                if (recursive) {
                    scanFolder(file, true, collector);
                }
            } else {
                collector.add(file);
                // 数量上限保护
                if (collector.size() >= MAX_FILES_PER_IMPORT) {
                    return;
                }
            }
        }
    }

    // ==================== 文件信息构建 ====================

    /**
     * 根据文件创建完整的 ImageFileInfo（含尺寸和缩略图）。
     *
     * @param file 图片文件
     * @return 文件信息对象
     */
    public ImageFileInfo createImageFileInfo(File file) {
        ImageFileInfo info = new ImageFileInfo(file);

        // 读取尺寸
        int[] dims = ImageUtil.readDimensions(file);
        info.setWidth(dims[0]);
        info.setHeight(dims[1]);

        return info;
    }

    /**
     * 为文件信息生成缩略图（耗时操作，适合后台线程）。
     *
     * @param info 文件信息对象
     * @return 缩略图 ImageIcon，失败返回 null
     */
    public ImageIcon generateThumbnail(ImageFileInfo info) {
        if (info == null || info.getSourceFile() == null) {
            return null;
        }
        return ImageUtil.createThumbnailIcon(info.getSourceFile());
    }

    // ==================== 文件去重 ====================

    /**
     * 批量去重：移除列表中路径重复的文件。
     *
     * @param existingList 已有文件列表
     * @param newFiles     待添加的新文件
     * @return 去重后的新文件列表
     */
    public List<ImageFileInfo> deduplicate(List<ImageFileInfo> existingList, List<ImageFileInfo> newFiles) {
        Set<String> existingPaths = new HashSet<>();
        for (ImageFileInfo info : existingList) {
            if (info.getSourceFile() != null) {
                existingPaths.add(info.getSourceFile().getAbsolutePath());
            }
        }

        List<ImageFileInfo> result = new ArrayList<>();
        for (ImageFileInfo info : newFiles) {
            if (info.getSourceFile() == null) continue;
            String path = info.getSourceFile().getAbsolutePath();
            if (!existingPaths.contains(path)) {
                result.add(info);
                existingPaths.add(path);
            }
        }
        return result;
    }
}
