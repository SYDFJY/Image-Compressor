package com.nchu.imagecompress.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.nchu.imagecompress.model.AppConfig;
import com.nchu.imagecompress.model.Theme;
import com.nchu.imagecompress.util.FileUtil;
import com.nchu.imagecompress.util.LogUtil;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 应用配置持久化服务。
 *
 * <p>负责 AppConfig 的 JSON 序列化/反序列化，配置文件存储于用户主目录下。
 * 提供 500ms 防抖写入机制，避免频繁磁盘 I/O。</p>
 *
 * <h3>配置文件位置</h3>
 * <pre>~/.NCHUImageCompressor/config.json</pre>
 *
 * <h3>线程安全</h3>
 * <p>所有公共方法均通过 synchronized 保护，确保多线程读写安全。</p>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-08
 */
public class ConfigService {

    /** 配置文件目录名 */
    private static final String CONFIG_DIR_NAME = "NCHUImageCompressor";

    /** 配置文件名 */
    private static final String CONFIG_FILE_NAME = "config.json";

    /** 防抖写入延迟（毫秒） */
    private static final long DEBOUNCE_DELAY_MS = 500;

    /** Gson 实例（美化输出） */
    private final Gson gson;

    /** 当前内存中的配置 */
    private AppConfig currentConfig;

    /** 配置文件对象 */
    private final File configFile;

    /** 防抖定时器 */
    private Timer debounceTimer;

    /** 是否有待写入的配置变更 */
    private boolean dirty = false;

    /**
     * 构造 ConfigService，自动确定配置文件路径。
     */
    public ConfigService() {
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(Theme.class, new TypeAdapter<Theme>() {
                    @Override
                    public void write(JsonWriter out, Theme value) throws java.io.IOException {
                        out.value(value.name());
                    }
                    @Override
                    public Theme read(JsonReader in) throws java.io.IOException {
                        return Theme.fromName(in.nextString());
                    }
                })
                .create();

        String homeDir = System.getProperty("user.home");
        File configDir = new File(homeDir, CONFIG_DIR_NAME);
        this.configFile = new File(configDir, CONFIG_FILE_NAME);

        // 确保配置目录存在
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
    }

    // ==================== 加载配置 ====================

    /**
     * 从 JSON 文件加载配置。
     * <p>如果文件不存在或内容损坏/格式错误，则返回默认配置。</p>
     *
     * @return 加载的 AppConfig（不为 null）
     */
    public synchronized AppConfig loadConfig() {
        if (!configFile.exists()) {
            // 配置文件不存在，返回默认配置并主动保存一份
            currentConfig = AppConfig.getDefault();
            saveConfigImmediately();
            return currentConfig;
        }

        try (FileReader reader = new FileReader(configFile)) {
            currentConfig = gson.fromJson(reader, AppConfig.class);
            if (currentConfig == null) {
                // JSON 内容为空或格式错误
                currentConfig = AppConfig.getDefault();
                saveConfigImmediately();
            }
        } catch (IOException | JsonSyntaxException e) {
            // 文件读取失败或 JSON 解析异常：使用默认配置，备份损坏文件
            LogUtil.warning("[ConfigService] 配置文件读取失败: " + e.getMessage());
            backupCorruptedConfig();
            currentConfig = AppConfig.getDefault();
            saveConfigImmediately();
        }

        return currentConfig;
    }

    // ==================== 保存配置 ====================

    /**
     * 保存配置（防抖写入）。
     * <p>调用后不会立即写入磁盘，而是启动 500ms 防抖定时器。
     * 如果在 500ms 内再次调用，定时器重新计时。最终只在最后一次调用的 500ms 后写入一次。</p>
     *
     * @param config 待保存的配置
     */
    public synchronized void saveConfig(AppConfig config) {
        if (config == null) {
            return;
        }
        this.currentConfig = config;
        this.dirty = true;

        // 取消之前的定时器
        cancelDebounceTimer();

        // 启动新的防抖定时器
        debounceTimer = new Timer("config-save-debounce", true);
        debounceTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                flushConfig();
            }
        }, DEBOUNCE_DELAY_MS);
    }

    /**
     * 立即将当前配置写入磁盘（跳过防抖）。
     * <p>用于应用退出前保存，确保数据不丢失。</p>
     */
    public synchronized void saveConfigImmediately() {
        cancelDebounceTimer();
        if (currentConfig == null) {
            currentConfig = AppConfig.getDefault();
        }
        writeToDisk(currentConfig);
        dirty = false;
    }

    /**
     * 强制刷盘：如果有待写入的变更，立即写入。
     */
    private synchronized void flushConfig() {
        if (dirty && currentConfig != null) {
            writeToDisk(currentConfig);
            dirty = false;
        }
    }

    /**
     * 实际执行磁盘写入操作。
     */
    private void writeToDisk(AppConfig config) {
        try (FileWriter writer = new FileWriter(configFile)) {
            gson.toJson(config, writer);
        } catch (IOException e) {
            LogUtil.error("[ConfigService] 配置写入失败: " + e.getMessage());
        }
    }

    // ==================== 当前配置获取 ====================

    /**
     * 获取当前在内存中的配置（可能尚未持久化）。
     *
     * @return 当前配置（不为 null）
     */
    public synchronized AppConfig getCurrentConfig() {
        if (currentConfig == null) {
            currentConfig = loadConfig();
        }
        return currentConfig;
    }

    // ==================== 便捷方法 ====================

    /**
     * 获取当前主题。
     */
    public Theme getCurrentTheme() {
        return getCurrentConfig().getTheme();
    }

    /**
     * 获取默认输出目录。
     * <p>优先返回上次使用的输出目录，如果为空则返回默认目录。</p>
     *
     * @return 输出目录路径
     */
    public String getDefaultOutputDir() {
        AppConfig config = getCurrentConfig();
        String lastPath = config.getLastOutputPath();
        if (lastPath != null && !lastPath.isEmpty()) {
            File dir = new File(lastPath);
            if (dir.exists() && dir.isDirectory() && dir.canWrite()) {
                return lastPath;
            }
        }
        return FileUtil.getDefaultOutputDir();
    }

    /**
     * 获取上次保存的质量值。
     */
    public int getLastQuality() {
        return getCurrentConfig().getLastQuality();
    }

    /**
     * 获取上次保存的输出格式。
     */
    public String getLastOutputFormat() {
        return getCurrentConfig().getLastOutputFormat();
    }

    // ==================== 内部辅助 ====================

    /**
     * 取消防抖定时器。
     */
    private void cancelDebounceTimer() {
        if (debounceTimer != null) {
            debounceTimer.cancel();
            debounceTimer = null;
        }
    }

    /**
     * 备份损坏的配置文件（重命名为 .bak）。
     */
    private void backupCorruptedConfig() {
        if (!configFile.exists()) return;
        File backupFile = new File(configFile.getParentFile(), CONFIG_FILE_NAME + ".bak");
        try {
            // 删除旧备份
            if (backupFile.exists()) {
                backupFile.delete();
            }
            configFile.renameTo(backupFile);
            LogUtil.warning("[ConfigService] 损坏的配置文件已备份为: " + backupFile.getAbsolutePath());
        } catch (SecurityException e) {
            LogUtil.error("[ConfigService] 无法备份损坏的配置文件: " + e.getMessage());
        }
    }

    /**
     * 应用关闭时调用：强制刷盘 + 清理资源。
     */
    public void shutdown() {
        saveConfigImmediately();
        cancelDebounceTimer();
    }
}
