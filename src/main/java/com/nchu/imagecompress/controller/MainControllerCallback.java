package com.nchu.imagecompress.controller;

import java.io.File;
import java.util.List;

/**
 * 视图 → 控制器回调接口。
 *
 * <p>定义 View 层向 Controller 层报告用户操作的契约。
 * 所有 View 组件通过此接口通知 Controller 用户意图，
 * Controller 负责协调 Service 调用和 UI 更新。</p>
 *
 * <p><b>设计原则：</b>
 * <ul>
 *   <li>每个方法对应一个用户可触发的操作</li>
 *   <li>方法名以 {@code on} 开头，表示"响应用户的 XXX 操作"</li>
 *   <li>参数传递用户操作携带的数据（文件列表、索引、配置值等）</li>
 *   <li>不返回结果——Controller 通过更新 View 来反馈</li>
 * </ul>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-08
 */
public interface MainControllerCallback {

    // ==================== 文件操作 ====================

    /**
     * 用户点击"导入图片"。
     * <p>Controller 应弹出文件选择器，调用 FileManagerService 导入选中文件。</p>
     */
    void onImportFiles();

    /**
     * 用户点击"导入文件夹"。
     * <p>Controller 应弹出目录选择器，调用 FileManagerService 扫描文件夹。</p>
     */
    void onImportFolder();

    /**
     * 用户拖拽文件到窗口。
     *
     * @param files 拖入的文件列表
     */
    void onDragImport(List<File> files);

    /**
     * 用户从列表中移除某个文件。
     *
     * @param index 要移除的文件索引
     */
    void onRemoveFile(int index);

    /**
     * 用户点击"清空列表"。
     */
    void onClearAllFiles();

    // ==================== 预览操作 ====================

    /**
     * 用户在列表中选中了某个文件。
     *
     * @param index 选中文件的索引（-1 表示取消选中）
     */
    void onFileSelected(int index);

    // ==================== 压缩操作 ====================

    /**
     * 用户点击"开始压缩"。
     * <p>Controller 应读取 ParamPanel 参数 → 构建 CompressConfig →
     * 创建 CompressWorker → 执行批量压缩。</p>
     */
    void onStartCompress();

    /**
     * 用户点击"取消压缩"。
     * <p>Controller 应中断正在运行的 CompressWorker。</p>
     */
    void onCancelCompress();

    // ==================== 设置操作 ====================

    /**
     * 用户点击"切换主题"。
     */
    void onToggleTheme();

    /**
     * 用户拖动质量滑块。
     *
     * @param quality 新的质量值 (0-100)
     */
    void onQualityChanged(int quality);

    /**
     * 用户选择了输出目录。
     *
     * @param dir 选择的目录
     */
    void onOutputDirSelected(File dir);

    /**
     * 用户点击"设置"菜单。
     */
    void onOpenSettings();

    /**
     * 用户点击"关于"菜单。
     */
    void onOpenAbout();

    // ==================== 窗口操作 ====================

    /**
     * 用户关闭窗口。
     * <p>Controller 应保存窗口状态和配置，释放资源后退出。</p>
     */
    void onWindowClosing();

    /**
     * 用户点击"退出"菜单。
     */
    void onExit();

    // ==================== 模式切换（v2.0） ====================

    /**
     * 用户切换图片/视频压缩模式。
     *
     * @param videoMode true 表示切换到视频模式
     */
    void onModeToggle(boolean videoMode);
}
