package com.nchu.imagecompress.util;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 统一日志工具类。
 *
 * <p>封装 {@link java.util.logging.Logger}，提供项目全局统一的日志输出。
 * 替代原始的 {@code System.out.println} / {@code System.err.println}，
 * 支持日志级别控制、格式化输出和后续扩展（如写入文件）。</p>
 *
 * <h3>使用方式</h3>
 * <pre>
 * LogUtil.info("应用启动完成");
 * LogUtil.warning("配置文件格式异常，已使用默认值");
 * LogUtil.error("压缩失败: " + e.getMessage());
 * </pre>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-08
 */
public final class LogUtil {

    /** 全局 Logger 实例 */
    private static final Logger LOGGER;

    static {
        // 使用简短名称，输出更简洁
        LOGGER = Logger.getLogger("NCHU.IC");

        // 配置控制台 Handler（使用项目一致的格式）
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        // 使用简洁格式：[LEVEL] message
        handler.setFormatter(new java.util.logging.Formatter() {
            @Override
            public String format(java.util.logging.LogRecord record) {
                return String.format("[%s] %s%n",
                        record.getLevel().getName(),
                        record.getMessage());
            }
        });
        LOGGER.addHandler(handler);

        // 不使用父 Logger 的 Handler，避免重复输出
        LOGGER.setUseParentHandlers(false);
        LOGGER.setLevel(Level.ALL);
    }

    private LogUtil() {
        // 工具类禁止实例化
    }

    /**
     * 输出信息级别日志。
     *
     * @param message 日志消息
     */
    public static void info(String message) {
        LOGGER.info(message);
    }

    /**
     * 输出警告级别日志。
     *
     * @param message 日志消息
     */
    public static void warning(String message) {
        LOGGER.warning(message);
    }

    /**
     * 输出错误级别日志。
     *
     * @param message 日志消息
     */
    public static void error(String message) {
        LOGGER.severe(message);
    }
}
