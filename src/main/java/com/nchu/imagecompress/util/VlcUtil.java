package com.nchu.imagecompress.util;

import com.sun.jna.NativeLibrary;
import uk.co.caprica.vlcj.discovery.NativeDiscovery;

import java.io.File;

/**
 * VLC 环境检测工具类 — 检测 VLC 原生库是否可用、获取版本信息。
 *
 * <p>VLCJ 通过 JNA 桥接 VLC 的 C/C++ 原生库（libvlc），因此需要系统上安装
 * VLC 媒体播放器或将 VLC 运行时捆绑到应用中。</p>
 *
 * <h3>搜索路径</h3>
 * <ol>
 *   <li>系统属性 {@code jna.library.path}</li>
 *   <li>系统 PATH 环境变量</li>
 *   <li>常见安装路径（Windows: C:\Program Files\VideoLAN\VLC）</li>
 * </ol>
 *
 * <p>所有方法为静态工具方法，结果会被缓存。对标 {@link VideoUtil} 的检测模式。</p>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-13
 */
public final class VlcUtil {

    private VlcUtil() { /* 工具类禁止实例化 */ }

    // ==================== 常量 ====================

    /** 缓存的 VLC 可用性检测结果 */
    private static Boolean vlcAvailable = null;

    /** 缓存的 VLC 版本字符串 */
    private static String vlcVersion = null;

    /** 通过系统属性 jna.library.path 自定义 VLC 原生库路径 */
    private static final String JNA_LIBRARY_PATH = System.getProperty("jna.library.path", "");

    /** 捆绑版 VLC 目录路径（自动探测结果缓存） */
    private static String bundledVlcDir = null;

    /** 捆绑版 VLC 插件目录路径（供 VideoPlayerPanel 传入 --plugin-path） */
    private static String bundledVlcPluginsPath = null;

    // ==================== 环境检测 ====================

    /**
     * 检测 VLC 原生库是否可用（结果会被缓存）。
     *
     * <p>使用 VLCJ 的 {@link NativeDiscovery} 自动搜索 libvlc。
     * 首次调用后不再重复检测。</p>
     *
     * @return true 表示 VLC 原生库可正常加载
     */
    public static boolean checkVlcAvailable() {
        if (vlcAvailable != null) {
            return vlcAvailable;
        }

        try {
            // 方式 1：使用 VLCJ NativeDiscovery 自动搜索
            NativeDiscovery discovery = new NativeDiscovery();
            vlcAvailable = discovery.discover();
            LogUtil.info("[VlcUtil] NativeDiscovery 结果: " + vlcAvailable);
        } catch (Throwable e) {
            // UnsatisfiedLinkError extends Error，不是 Exception，必须用 Throwable 兜底
            LogUtil.info("[VlcUtil] NativeDiscovery 异常: " + e.getMessage());
            vlcAvailable = false;
        }

        // 方式 1.5：始终探测捆绑版 VLC，获取插件路径供 MediaPlayerFactory 使用
        String bundledPath = findBundledVlc();
        if (bundledPath != null) {
            if (!vlcAvailable) {
                NativeLibrary.addSearchPath("libvlc", bundledPath);
                NativeLibrary.addSearchPath("libvlccore", bundledPath);
            }
            File pluginsDir = new File(bundledPath, "plugins");
            if (pluginsDir.isDirectory()) {
                bundledVlcPluginsPath = pluginsDir.getAbsolutePath();
                LogUtil.info("[VlcUtil] 捆绑版 VLC 插件目录: " + bundledVlcPluginsPath);
            }
        }

        // 方式 2：NativeDiscovery 失败时，手动尝试加载 libvlc
        if (!vlcAvailable) {
            vlcAvailable = tryManualLoad();
        }

        LogUtil.info("[VlcUtil] VLC 可用性: " + vlcAvailable);
        return vlcAvailable;
    }

    /**
     * 手动尝试加载 VLC 原生库（NativeDiscovery 失败时的补充检测）。
     */
    private static boolean tryManualLoad() {
        try {
            // 尝试从 jna.library.path 加载
            if (!JNA_LIBRARY_PATH.isEmpty()) {
                File vlcDir = new File(JNA_LIBRARY_PATH);
                if (!vlcDir.isDirectory()) {
                    // 尝试作为绝对路径
                    vlcDir = new File(JNA_LIBRARY_PATH);
                }
                if (vlcDir.isDirectory()) {
                    String absPath = vlcDir.getAbsolutePath();
                    // Windows 上 DLL 名为 libvlc.dll，JNA 的 "vlc" → vlc.dll 找不到
                    // 必须用 "libvlc" 让 JNA 在 Windows 上查找 libvlc.dll
                    NativeLibrary.addSearchPath("libvlc", absPath);
                    NativeLibrary.addSearchPath("libvlccore", absPath);
                    LogUtil.info("[VlcUtil] 添加搜索路径: " + absPath);
                }
            }
            NativeLibrary.getInstance("libvlc");
            LogUtil.info("[VlcUtil] 手动加载 libvlc 成功");
            return true;
        } catch (Throwable e) {
            // UnsatisfiedLinkError extends Error, not Exception
            LogUtil.info("[VlcUtil] 手动加载 libvlc 失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 自动探测应用目录下的捆绑版 VLC（standalone/vlc/）。
     *
     * @return 捆绑版 VLC 目录的绝对路径，未找到时返回 null
     */
    private static String findBundledVlc() {
        try {
            String jarPath = VlcUtil.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI().getPath();
            File jarFile = new File(jarPath);
            File jarDir = jarFile.getParentFile();
            if (jarDir == null) return null;

            File vlcDir = new File(jarDir, "standalone/vlc");
            if (!vlcDir.isDirectory()) {
                // 场景1：JAR 运行 — dist/standalone/xxx.jar，vlc/ 是 JAR 的兄弟目录
                File siblingDir = new File(jarDir, "vlc");
                if (siblingDir.isDirectory()) {
                    vlcDir = siblingDir;
                } else {
                    // 场景2：IDE 运行 — target/classes/，回退到项目根目录找 dist/standalone/vlc/
                    File altDir = new File(jarDir.getParentFile(), "dist/standalone/vlc");
                    if (altDir.isDirectory()) vlcDir = altDir;
                    else return null;
                }
            }

            File libvlcDll = new File(vlcDir, "libvlc.dll");
            File libvlccoreDll = new File(vlcDir, "libvlccore.dll");
            if (libvlcDll.exists() && libvlccoreDll.exists()) {
                bundledVlcDir = vlcDir.getAbsolutePath();
                LogUtil.info("[VlcUtil] 发现捆绑版 VLC: " + bundledVlcDir);
                return bundledVlcDir;
            }
        } catch (Exception e) {
            LogUtil.info("[VlcUtil] 探测捆绑版 VLC 失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 获取捆绑版 VLC 插件目录路径。
     *
     * <p>供 VideoPlayerPanel 创建 MediaPlayerFactory 时传入 --plugin-path。</p>
     *
     * @return 插件目录绝对路径，未找到时返回 null
     */
    public static String getVlcPluginsPath() {
        if (bundledVlcPluginsPath == null) {
            checkVlcAvailable();
        }
        return bundledVlcPluginsPath;
    }

    /**
     * 获取 VLC 版本字符串。
     *
     * @return 版本字符串（如 "3.0.20 Vetinari"），不可用时返回 "不可用"
     */
    public static String getVlcVersion() {
        if (vlcVersion != null) {
            return vlcVersion;
        }

        if (!checkVlcAvailable()) {
            vlcVersion = "不可用";
            return vlcVersion;
        }

        try {
            // 通过 JNA 读取 libvlc 版本
            NativeLibrary lib = NativeLibrary.getInstance("libvlc");
            vlcVersion = lib.toString();
            LogUtil.info("[VlcUtil] VLC 版本: " + vlcVersion);
        } catch (Throwable e) {
            vlcVersion = "未知版本";
        }

        return vlcVersion;
    }

    /**
     * 强制重新检测 VLC 可用性（清除缓存）。
     */
    public static void resetVlcCheck() {
        vlcAvailable = null;
        vlcVersion = null;
        bundledVlcDir = null;
        bundledVlcPluginsPath = null;
    }
}
