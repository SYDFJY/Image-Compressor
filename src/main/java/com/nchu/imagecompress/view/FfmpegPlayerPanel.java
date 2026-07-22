package com.nchu.imagecompress.view;

import com.nchu.imagecompress.util.FfmpegRenderUtil;
import com.nchu.imagecompress.util.LogUtil;
import com.nchu.imagecompress.util.ThemeUtil;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;

/**
 * 基于 ffmpeg 管道帧渲染的视频播放面板 — 无需 VLC，纯 Java + ffmpeg 实现。
 *
 * <h3>工作原理</h3>
 * <ol>
 *   <li>通过 {@link FfmpegRenderUtil#startFrameStream(File, int)} 启动 ffmpeg 子进程，
 *       输出 raw RGB24 帧到 stdout</li>
 *   <li>后台线程持续从 stdout 读取帧 → 转换为 {@link BufferedImage}</li>
 *   <li>{@link javax.swing.Timer} 按目标帧率定时取最新帧 → {@code repaint()}</li>
 *   <li>{@link #paintComponent(Graphics)} 将当前帧拉伸绘制到面板</li>
 * </ol>
 *
 * <h3>限制</h3>
 * <ul>
 *   <li>无音频输出（ffmpeg 管道仅传输视频帧）</li>
 *   <li>不支持逐帧精确拖拽进度条（Seek 需重启 ffmpeg 进程）</li>
 * </ul>
 *
 * @author NCHU-Student
 * @version 1.1.0
 * @since 2026-07-13
 */
public class FfmpegPlayerPanel extends JPanel {

    // ==================== 状态常量 ====================

    private enum State {
        /** 空状态 — 未加载视频 */
        EMPTY,
        /** 加载中 — ffmpeg 启动中，等待首帧 */
        LOADING,
        /** 播放中 — Timer 驱动帧刷新 */
        PLAYING,
        /** 已暂停 — ffmpeg 仍在输出，Timer 暂停 */
        PAUSED,
        /** 已停止 — ffmpeg 已终止 */
        STOPPED,
        /** 错误状态 */
        ERROR
    }

    // ==================== UI 组件 ====================

    /** 当前帧图像（volatile — 后台线程写入，EDT 读取） */
    private volatile BufferedImage currentFrame;

    /** 播放/暂停按钮 */
    private JButton playPauseBtn;

    /** 时长标签（当前 / 总时长） */
    private JLabel durationLabel;

    /** 进度滑块（只读 — ffmpeg 管道不支持 seek） */
    private JSlider seekSlider;

    /** 音量滑块（禁用 — ffmpeg 管道无音频输出） */
    private JSlider volumeSlider;

    private JPanel controlBar;

    // ==================== 后台线程 / Timer ====================

    /** ffmpeg 子进程 */
    private Process ffmpegProcess;

    /** 帧读取线程 */
    private Thread readerThread;

    /** 帧渲染 Timer（EDT） */
    private Timer renderTimer;

    /** 进度同步 Timer（每 200ms 更新进度条和时长） */
    private Timer progressTimer;

    /** 帧间隔（毫秒），由目标帧率决定 */
    private int frameIntervalMs;

    // ==================== 时长追踪 ====================

    /** 视频总时长（毫秒），由调用方传入 */
    private long totalDurationMs = 0;

    /** 播放开始时的 System.currentTimeMillis()（用于计算已播放时间） */
    private long playStartTime = 0;

    /** 暂停时已累计的播放时间（毫秒） */
    private long pausedElapsed = 0;

    // ==================== 状态 ====================

    private volatile State state = State.EMPTY;
    private File currentVideo;
    private int videoWidth;
    private int videoHeight;
    private int displayWidth;
    private int displayHeight;
    private int frameSize;

    /** 目标预览宽度（像素） */
    private static final int TARGET_WIDTH = 640;

    public FfmpegPlayerPanel() {
        setLayout(new BorderLayout(0, 0));
        setBackground(Color.BLACK);
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        // 控制栏
        controlBar = createControlBar();
        controlBar.setVisible(false);
        add(controlBar, BorderLayout.SOUTH);

        // 进度同步 Timer（每 200ms 更新进度条和时长标签）
        progressTimer = new Timer(200, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateProgress();
            }
        });

        LogUtil.info("[FfmpegPlayerPanel] 初始化完成");
    }

    // ==================== 控制栏构建 ====================

    private JPanel createControlBar() {
        JPanel bar = new JPanel(new BorderLayout(ThemeUtil.SPACE_SM, 0));
        bar.setBackground(new Color(30, 30, 30, 230));
        bar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        // --- 左侧：播放/暂停 + 时长 ---
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftPanel.setOpaque(false);

        playPauseBtn = new JButton("▶");
        playPauseBtn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
        playPauseBtn.setFocusPainted(false);
        playPauseBtn.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        playPauseBtn.setContentAreaFilled(false);
        playPauseBtn.setForeground(Color.WHITE);
        playPauseBtn.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        playPauseBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                togglePlayPause();
            }
        });
        leftPanel.add(playPauseBtn);

        durationLabel = new JLabel("00:00 / 00:00");
        durationLabel.setFont(ThemeUtil.FONT_TINY);
        durationLabel.setForeground(new Color(200, 200, 200));
        leftPanel.add(durationLabel);

        bar.add(leftPanel, BorderLayout.WEST);

        // --- 中间：进度滑块（只读 — ffmpeg 管道不支持 seek） ---
        seekSlider = new JSlider(0, 1000, 0);
        seekSlider.setOpaque(false);
        seekSlider.setPreferredSize(new Dimension(200, 20));
        seekSlider.setEnabled(false);
        seekSlider.setToolTipText("ffmpeg 降级模式不支持拖拽进度");
        bar.add(seekSlider, BorderLayout.CENTER);

        // --- 右侧：音量（禁用 — ffmpeg 管道无音频输出） ---
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightPanel.setOpaque(false);

        JButton volumeBtn = new JButton("🔇");
        volumeBtn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
        volumeBtn.setFocusPainted(false);
        volumeBtn.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        volumeBtn.setContentAreaFilled(false);
        volumeBtn.setForeground(Color.GRAY);
        volumeBtn.setEnabled(false);
        rightPanel.add(volumeBtn);

        volumeSlider = new JSlider(0, 100, 0);
        volumeSlider.setOpaque(false);
        volumeSlider.setPreferredSize(new Dimension(80, 20));
        volumeSlider.setEnabled(false);
        volumeSlider.setToolTipText("ffmpeg 降级模式不支持音频输出");
        rightPanel.add(volumeSlider);

        bar.add(rightPanel, BorderLayout.EAST);

        return bar;
    }

    // ==================== 公共 API ====================

    /**
     * 加载并自动播放视频（ffmpeg 管道帧渲染）。
     *
     * @param videoFile       视频文件
     * @param origFps         原始帧率（用于显示，实际渲染使用固定预览帧率）
     * @param origW           原始宽度（像素）
     * @param origH           原始高度（像素）
     * @param totalDurationMs 视频总时长（毫秒），≤0 时进度条不更新
     */
    public void play(final File videoFile, double origFps, int origW, int origH,
                     long totalDurationMs) {
        if (videoFile == null || !videoFile.exists()) {
            LogUtil.info("[FfmpegPlayerPanel] 视频文件不存在: " + videoFile);
            return;
        }

        // 先停止当前播放
        stopInternal();

        this.currentVideo = videoFile;
        this.videoWidth = origW;
        this.videoHeight = origH;
        this.totalDurationMs = totalDurationMs;
        this.pausedElapsed = 0;

        // 计算预览尺寸
        Dimension size = FfmpegRenderUtil.calculatePreviewSize(origW, origH, TARGET_WIDTH);
        this.displayWidth = size.width;
        this.displayHeight = size.height;
        this.frameSize = displayWidth * displayHeight * 3;
        double effectiveFps = (origFps > 0) ? origFps : 24.0;
        this.frameIntervalMs = (int) (1000.0 / effectiveFps);

        // 切换到加载状态
        setState(State.LOADING);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                controlBar.setVisible(true);
                seekSlider.setValue(0);
                durationLabel.setText("加载中... / " + formatMs(totalDurationMs));
                playPauseBtn.setText("⏳");
                playPauseBtn.setEnabled(false);
                repaint();
            }
        });

        // 在后台启动 ffmpeg 和读取线程
        readerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ffmpegProcess = FfmpegRenderUtil.startFrameStream(
                            videoFile, TARGET_WIDTH, effectiveFps);
                    InputStream in = ffmpegProcess.getInputStream();

                    // 启动渲染 Timer
                    renderTimer = new Timer(frameIntervalMs, new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            repaint();
                        }
                    });
                    renderTimer.start();

                    // 持续读取帧
                    while (!Thread.currentThread().isInterrupted()) {
                        byte[] frameData = FfmpegRenderUtil.readFrame(
                                in, displayWidth, displayHeight);
                        if (frameData == null) {
                            break;
                        }

                        BufferedImage img = FfmpegRenderUtil.rgbToImage(
                                frameData, displayWidth, displayHeight);
                        currentFrame = img;

                        // 首帧到达 → 切换到播放状态
                        if (state == State.LOADING) {
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    if (state == State.LOADING) {
                                        setState(State.PLAYING);
                                        playPauseBtn.setText("⏸");
                                        playPauseBtn.setEnabled(true);
                                        playStartTime = System.currentTimeMillis();
                                        progressTimer.start();
                                        updateProgress();
                                        repaint();
                                    }
                                }
                            });
                        }
                    }

                    // 检查 ffmpeg 是否异常退出
                    try {
                        int exitCode = ffmpegProcess.waitFor();
                        if (exitCode != 0 && state != State.STOPPED) {
                            LogUtil.info("[FfmpegPlayerPanel] ffmpeg 异常退出, code=" + exitCode);
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    setState(State.ERROR);
                                    durationLabel.setText("播放出错");
                                    playPauseBtn.setText("⚠");
                                    playPauseBtn.setEnabled(false);
                                }
                            });
                        } else if (state == State.PLAYING || state == State.PAUSED) {
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    if (renderTimer != null) {
                                        renderTimer.stop();
                                    }
                                    progressTimer.stop();
                                    setState(State.STOPPED);
                                    playPauseBtn.setText("重试");
                                    seekSlider.setValue(1000);
                                    durationLabel.setText(formatMs(totalDurationMs)
                                            + " / " + formatMs(totalDurationMs));
                                }
                            });
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                } catch (Exception e) {
                    LogUtil.error("[FfmpegPlayerPanel] 播放失败: " + e.getMessage());
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            setState(State.ERROR);
                            durationLabel.setText("播放失败");
                            playPauseBtn.setText("⚠");
                            playPauseBtn.setEnabled(false);
                        }
                    });
                }
            }
        }, "Ffmpeg-Reader-Thread");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    /**
     * 停止播放并释放 ffmpeg 资源。
     */
    public void stop() {
        stopInternal();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                setState(State.EMPTY);
                currentFrame = null;
                controlBar.setVisible(false);
                playPauseBtn.setText("▶");
                seekSlider.setValue(0);
                durationLabel.setText("00:00 / 00:00");
                repaint();
            }
        });
    }

    /**
     * 暂停渲染（Timer 暂停，ffmpeg 继续输出）。
     */
    public void pause() {
        if (state == State.PLAYING) {
            if (renderTimer != null) {
                renderTimer.stop();
            }
            if (progressTimer != null) {
                progressTimer.stop();
            }
            pausedElapsed += System.currentTimeMillis() - playStartTime;
            setState(State.PAUSED);
            playPauseBtn.setText("▶");
        }
    }

    /**
     * 恢复渲染。
     */
    public void resume() {
        if (state == State.PAUSED) {
            if (renderTimer != null) {
                renderTimer.start();
            }
            playStartTime = System.currentTimeMillis();
            if (progressTimer != null) {
                progressTimer.start();
            }
            setState(State.PLAYING);
            playPauseBtn.setText("⏸");
        }
    }

    /**
     * 切换播放/暂停。
     */
    public void togglePlayPause() {
        if (state == State.PLAYING) {
            pause();
        } else if (state == State.PAUSED) {
            resume();
        } else if (state == State.STOPPED && currentVideo != null) {
            play(currentVideo, 0, videoWidth, videoHeight, totalDurationMs);
        }
    }

    /**
     * 释放所有资源（应用退出时调用）。
     */
    public void release() {
        stopInternal();
        currentFrame = null;
        LogUtil.info("[FfmpegPlayerPanel] 资源已释放");
    }

    /** 是否正在播放。 */
    public boolean isPlaying() {
        return state == State.PLAYING;
    }

    /** 是否处于可播放状态。 */
    public boolean isReady() {
        return state == State.PLAYING || state == State.PAUSED || state == State.STOPPED;
    }

    // ==================== 渲染 ====================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();

        BufferedImage frame = currentFrame;
        if (frame != null) {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);

            int panelW = getWidth();
            int panelH = getHeight();
            double scale = Math.min(
                    (double) panelW / frame.getWidth(),
                    (double) panelH / frame.getHeight());
            int drawW = (int) (frame.getWidth() * scale);
            int drawH = (int) (frame.getHeight() * scale);
            int x = (panelW - drawW) / 2;
            int y = (panelH - drawH) / 2;

            g2d.drawImage(frame, x, y, drawW, drawH, this);
        } else if (state == State.LOADING) {
            g2d.setColor(new Color(200, 200, 200));
            g2d.setFont(ThemeUtil.FONT_BODY);
            String text = "正在加载视频...";
            int textW = g2d.getFontMetrics().stringWidth(text);
            g2d.drawString(text, (getWidth() - textW) / 2, getHeight() / 2);
        }

        g2d.dispose();
    }

    // ==================== 内部方法 ====================

    /**
     * 停止播放（内部，不更新 UI 状态）。
     */
    private void stopInternal() {
        if (renderTimer != null) {
            renderTimer.stop();
            renderTimer = null;
        }
        if (progressTimer != null) {
            progressTimer.stop();
        }

        if (readerThread != null && readerThread.isAlive()) {
            readerThread.interrupt();
            try {
                readerThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            readerThread = null;
        }

        if (ffmpegProcess != null) {
            ffmpegProcess.destroy();
            try {
                ffmpegProcess.waitFor(1, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (ffmpegProcess.isAlive()) {
                ffmpegProcess.destroyForcibly();
            }
            ffmpegProcess = null;
        }

        currentFrame = null;
    }

    private void setState(State newState) {
        State old = this.state;
        this.state = newState;
        if (old != newState) {
            LogUtil.info("[FfmpegPlayerPanel] 状态: " + old + " → " + newState);
        }
    }

    // ==================== 进度更新 ====================

    /**
     * 更新进度条和时长标签（由 progressTimer 每 200ms 调用一次）。
     */
    private void updateProgress() {
        if (state != State.PLAYING) return;

        long elapsed = pausedElapsed + (System.currentTimeMillis() - playStartTime);

        if (totalDurationMs > 0) {
            int sliderVal = (int) (elapsed * 1000L / totalDurationMs);
            seekSlider.setValue(Math.min(sliderVal, 1000));
            durationLabel.setText(formatMs(elapsed) + " / " + formatMs(totalDurationMs));

            if (elapsed >= totalDurationMs) {
                progressTimer.stop();
                seekSlider.setValue(1000);
                durationLabel.setText(formatMs(totalDurationMs) + " / " + formatMs(totalDurationMs));
                if (renderTimer != null) {
                    renderTimer.stop();
                }
                setState(State.STOPPED);
                playPauseBtn.setText("重试");
            }
        } else {
            durationLabel.setText(formatMs(elapsed) + " / --:--");
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 格式化毫秒为时间字符串（HH:MM:SS 或 MM:SS）。
     */
    static String formatMs(long totalMs) {
        if (totalMs <= 0) return "00:00";
        long totalSec = totalMs / 1000;
        int h = (int) (totalSec / 3600);
        int m = (int) ((totalSec % 3600) / 60);
        int s = (int) (totalSec % 60);
        if (h > 0) {
            return String.format("%d:%02d:%02d", h, m, s);
        }
        return String.format("%02d:%02d", m, s);
    }
}
