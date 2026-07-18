package com.nchu.imagecompress;

import com.nchu.imagecompress.controller.MainController;
import com.nchu.imagecompress.model.AppConfig;
import com.nchu.imagecompress.model.Theme;
import com.nchu.imagecompress.service.ConfigService;
import com.nchu.imagecompress.util.LogUtil;
import com.nchu.imagecompress.util.ThemeUtil;
import com.nchu.imagecompress.view.MainFrame;
import com.nchu.imagecompress.view.SplashScreen;

import javax.swing.SwingUtilities;

/**
 * 图片压缩工具 — 程序入口类。
 *
 * <p>启动流程：
 * <ol>
 *   <li>显示 SplashScreen 启动画面</li>
 *   <li>加载 FlatLaf 主题</li>
 *   <li>创建 MainFrame 主窗口</li>
 *   <li>创建 MainController 并初始化（加载配置、绑定事件）</li>
 *   <li>关闭启动画面</li>
 * </ol>
 *
 * <p>所有 Swing 操作在 EDT 中执行，确保线程安全。</p>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-08
 */
public class App {

    /** 应用版本号 */
    public static final String VERSION = "1.0.0";

    /** 应用名称 */
    public static final String APP_NAME = "NCHU Image Compressor";

    /**
     * 程序主入口。
     *
     * @param args 命令行参数（暂未使用）
     */
    public static void main(String[] args) {
        // 所有 Swing 界面操作必须在 EDT 中执行
        SwingUtilities.invokeLater(() -> {
            LogUtil.info(APP_NAME + " v" + VERSION + " 启动中...");

            SplashScreen splash = new SplashScreen();
            try {
                splash.setVisible(true);
                splash.updateProgress(10, "正在初始化...");

                // ② 加载主题（优先使用已保存的配置主题，否则用默认的蓝韵主题）
                splash.updateProgress(20, "正在加载主题...");
                Theme savedTheme = Theme.BLUE_RHYME;
                try {
                    AppConfig savedCfg = new ConfigService().loadConfig();
                    if (savedCfg != null && savedCfg.getTheme() != null) {
                        savedTheme = savedCfg.getTheme();
                    }
                } catch (Exception ignored) {
                    // 配置文件不存在或损坏，使用默认主题
                }
                ThemeUtil.applyTheme(savedTheme);
                LogUtil.info("[App] 主题已加载: " + savedTheme.getDisplayName());

                // ③ 创建主窗口
                splash.updateProgress(50, "正在构建界面...");
                MainFrame mainFrame = new MainFrame();

                // ④ 创建主控制器并初始化（加载配置、恢复参数、绑定事件）
                splash.updateProgress(70, "正在加载配置...");
                MainController controller = new MainController(mainFrame);
                controller.initialize();

                // ⑤ 显示窗口
                splash.updateProgress(90, "即将就绪...");
                mainFrame.setVisible(true);

                // ⑥ 关闭启动画面（用 Timer 替代 sleep，不阻塞 EDT）
                splash.updateProgress(100, "就绪");
                javax.swing.Timer timer = new javax.swing.Timer(300, e -> {
                    splash.dispose();
                    LogUtil.info("[App] 启动完成");
                });
                timer.setRepeats(false);
                timer.start();

            } catch (Exception ex) {
                // 初始化异常时也必须关闭启动画面
                LogUtil.error("[App] 启动失败: " + ex.getMessage());
                splash.dispose();
                throw new RuntimeException("应用启动失败", ex);
            }
        });
    }
}
