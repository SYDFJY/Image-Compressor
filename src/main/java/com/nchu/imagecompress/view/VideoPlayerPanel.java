package com.nchu.imagecompress.view;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.nchu.imagecompress.model.VideoFileInfo;
import com.nchu.imagecompress.util.LogUtil;
import com.nchu.imagecompress.util.ThemeUtil;
import com.nchu.imagecompress.util.VideoUtil;
import com.nchu.imagecompress.util.VlcUtil;
import uk.co.caprica.vlcj.component.EmbeddedMediaPlayerComponent;
import uk.co.caprica.vlcj.player.MediaPlayer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

/**
 * VLCJ 内嵌视频播放器面板 — 在 Swing 容器中直接播放视频。
 *
 * <p>内部使用 VLCJ {@link EmbeddedMediaPlayerComponent}（AWT Canvas）渲染视频帧。
 * 提供完整的播放控件：播放/暂停、进度拖拽、音量调节。</p>
 *
 * <h3>状态管理</h3>
 * <ul>
 *   <li><b>空状态</b>：未加载视频时显示引导图标</li>
 *   <li><b>就绪状态</b>：视频已加载，显示首帧 + 控件</li>
 *   <li><b>播放中</b>：Timer 驱动进度条更新</li>
 *   <li><b>VLC 不可用</b>：降级提示</li>
 * </ul>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-13
 */
public class VideoPlayerPanel extends JPanel {

    // ==================== 状态卡片 ====================

    private static final String CARD_EMPTY = "EMPTY";
    private static final String CARD_PLAYER = "PLAYER";
    private static final String CARD_FFMPEG = "FFMPEG";
    private static final String CARD_UNAVAILABLE = "UNAVAILABLE";

    private final CardLayout cardLayout;
    private final JPanel cardPanel;

    // ==================== VLCJ 组件 ====================

    private EmbeddedMediaPlayerComponent mediaPlayerComponent;
    private MediaPlayer mediaPlayer;

    // ==================== ffmpeg 降级组件 ====================

    private FfmpegPlayerPanel ffmpegPlayer;

    // ==================== 控件 ====================

    private JButton playPauseBtn;
    private JSlider seekSlider;
    private JButton volumeBtn;
    private JSlider volumeSlider;
    private JLabel timeLabel;
    private JPanel controlBar;

    // ==================== 状态 ====================

    private boolean isPlaying = false;
    private boolean isSeeking = false;
    private boolean isMuted = false;
    private int savedVolume = 100;
    private Timer syncTimer;
    private File currentVideo;
    private long totalDurationMs = 0;

    /** VLC 是否可用（面板级缓存） */
    private boolean vlcUsable;

    public VideoPlayerPanel() {
        setLayout(new BorderLayout(0, 0));
        setBackground(ThemeUtil.BG_CARD);
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        // 预检测 VLC 可用性
        vlcUsable = VlcUtil.checkVlcAvailable();

        // CardLayout 切换空状态 / 播放器 / 不可用
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.setOpaque(false);

        JPanel emptyPanel = createEmptyPanel();
        cardPanel.add(emptyPanel, CARD_EMPTY);

        if (vlcUsable) {
            JPanel playerPanel = createPlayerPanel();
            cardPanel.add(playerPanel, CARD_PLAYER);
        } else if (VideoUtil.checkFfmpegAvailable()) {
            // ffmpeg 降级：创建 ffmpeg 帧渲染面板 + 保留不可用兜底
            ffmpegPlayer = new FfmpegPlayerPanel();
            cardPanel.add(ffmpegPlayer, CARD_FFMPEG);
            JPanel unavailablePanel = createUnavailablePanel();
            cardPanel.add(unavailablePanel, CARD_UNAVAILABLE);
            LogUtil.info("[VideoPlayerPanel] 使用 ffmpeg 降级播放引擎");
        } else {
            JPanel unavailablePanel = createUnavailablePanel();
            cardPanel.add(unavailablePanel, CARD_UNAVAILABLE);
        }

        add(cardPanel, BorderLayout.CENTER);
        cardLayout.show(cardPanel, CARD_EMPTY);

        LogUtil.info("[VideoPlayerPanel] 初始化完成, VLC 可用: " + vlcUsable);
    }

    // ==================== 子面板构建 ====================

    /**
     * 空状态面板 — 未选择视频时显示。
     */
    private JPanel createEmptyPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(40, 20, 40, 20));

        JPanel centerPanel = new JPanel(new BorderLayout(0, ThemeUtil.SPACE_LG));
        centerPanel.setOpaque(false);

        JLabel iconLabel = new JLabel(new FlatSVGIcon("icons/film.svg", 48, 48));
        centerPanel.add(iconLabel, BorderLayout.NORTH);

        JLabel guideLabel = new JLabel("选择视频文件播放", SwingConstants.CENTER);
        guideLabel.setFont(ThemeUtil.FONT_BODY);
        guideLabel.setForeground(ThemeUtil.TEXT_SECONDARY);
        centerPanel.add(guideLabel, BorderLayout.CENTER);

        panel.add(centerPanel, BorderLayout.CENTER);
        return panel;
    }

    /**
     * VLC 不可用时的降级面板。
     */
    private JPanel createUnavailablePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(40, 20, 40, 20));

        JPanel centerPanel = new JPanel(new BorderLayout(0, ThemeUtil.SPACE_MD));
        centerPanel.setOpaque(false);

        JLabel iconLabel = new JLabel("⚠", SwingConstants.CENTER);
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 36));
        centerPanel.add(iconLabel, BorderLayout.NORTH);

        JLabel msgLabel = new JLabel(
                "<html><center>未检测到 VLC 媒体播放器<br>"
                        + "<span style='font-size:11px;color:#999;'>请安装 VLC 以启用内嵌播放，"
                        + "或使用外部播放器</span></center></html>",
                SwingConstants.CENTER);
        msgLabel.setFont(ThemeUtil.FONT_SMALL);
        msgLabel.setForeground(ThemeUtil.TEXT_SECONDARY);
        centerPanel.add(msgLabel, BorderLayout.CENTER);

        panel.add(centerPanel, BorderLayout.CENTER);
        return panel;
    }

    /**
     * 播放器面板 — VLCJ 画布 + 控制栏。
     */
    private JPanel createPlayerPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setOpaque(false);
        panel.setBackground(Color.BLACK); // 视频区域黑色背景

        // --- VLCJ 画布（视频渲染区） ---
        try {
            // 探测捆绑版 VLC 插件路径，通过 --plugin-path 传给 libvlc
            // 否则 VLC 找不到 plugins/ 目录 → 音频输出模块缺失 → 无声 + 控件异常
            final String pluginsPath = VlcUtil.getVlcPluginsPath();
            if (pluginsPath != null) {
                mediaPlayerComponent = new EmbeddedMediaPlayerComponent() {
                    @Override
                    protected String[] onGetMediaPlayerFactoryArgs() {
                        return new String[] {
                                "--plugin-path=" + pluginsPath,
                                "--no-video-title-show"
                        };
                    }
                };
                LogUtil.info("[VideoPlayerPanel] 使用捆绑版 VLC 插件: " + pluginsPath);
            } else {
                mediaPlayerComponent = new EmbeddedMediaPlayerComponent();
            }
            mediaPlayer = mediaPlayerComponent.getMediaPlayer();
            panel.add(mediaPlayerComponent, BorderLayout.CENTER);
        } catch (Throwable e) {
            // UnsatisfiedLinkError extends Error，不是 Exception
            LogUtil.error("[VideoPlayerPanel] VLCJ 组件初始化失败: " + e.getMessage());
            vlcUsable = false;
            return createUnavailablePanel();
        }

        // --- 控制栏 ---
        controlBar = createControlBar();
        controlBar.setVisible(false); // 加载视频后才显示
        panel.add(controlBar, BorderLayout.SOUTH);

        // --- 同步 Timer（每 200ms 更新进度条） ---
        syncTimer = new Timer(200, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                syncPlayerState();
            }
        });

        return panel;
    }

    /**
     * 创建播放控制栏。
     */
    private JPanel createControlBar() {
        JPanel bar = new JPanel(new BorderLayout(ThemeUtil.SPACE_SM, 0));
        bar.setBackground(new Color(30, 30, 30, 230));
        bar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        // --- 左侧：播放/暂停 + 时间 ---
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

        timeLabel = new JLabel("00:00 / 00:00");
        timeLabel.setFont(ThemeUtil.FONT_TINY);
        timeLabel.setForeground(new Color(200, 200, 200));
        leftPanel.add(timeLabel);

        bar.add(leftPanel, BorderLayout.WEST);

        // --- 中间：进度滑块 ---
        seekSlider = new JSlider(0, 1000, 0);
        seekSlider.setOpaque(false);
        seekSlider.setPreferredSize(new Dimension(200, 20));
        seekSlider.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                isSeeking = true;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                isSeeking = false;
                // 拖拽松手 → 执行 seek
                if (mediaPlayer != null && totalDurationMs > 0) {
                    double fraction = seekSlider.getValue() / 1000.0;
                    long targetMs = (long) (fraction * totalDurationMs);
                    mediaPlayer.setTime(targetMs);
                }
            }
        });
        bar.add(seekSlider, BorderLayout.CENTER);

        // --- 右侧：音量 ---
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightPanel.setOpaque(false);

        volumeBtn = new JButton("🔊");
        volumeBtn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
        volumeBtn.setFocusPainted(false);
        volumeBtn.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        volumeBtn.setContentAreaFilled(false);
        volumeBtn.setForeground(Color.WHITE);
        volumeBtn.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        volumeBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                toggleMute();
            }
        });
        rightPanel.add(volumeBtn);

        volumeSlider = new JSlider(0, 200, 100);
        volumeSlider.setOpaque(false);
        volumeSlider.setPreferredSize(new Dimension(80, 20));
        volumeSlider.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (mediaPlayer != null) {
                    int vol = volumeSlider.getValue();
                    mediaPlayer.setVolume(vol);
                    savedVolume = vol;
                    isMuted = (vol == 0);
                    updateVolumeIcon();
                }
            }
        });
        rightPanel.add(volumeSlider);

        bar.add(rightPanel, BorderLayout.EAST);

        return bar;
    }

    // ==================== 公共 API ====================

    /**
     * 加载并自动播放视频文件。
     *
     * @param videoFile 视频文件
     */
    public void play(final File videoFile) {
        if (videoFile == null || !videoFile.exists()) {
            LogUtil.info("[VideoPlayerPanel] 视频文件不存在: " + videoFile);
            return;
        }

        if (!vlcUsable) {
            LogUtil.info("[VideoPlayerPanel] VLC 不可用，尝试降级引擎");
            this.currentVideo = videoFile;
            if (ffmpegPlayer != null) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        cardLayout.show(cardPanel, CARD_FFMPEG);
                    }
                });
                // play(File) 没有元数据，ffmpeg 内部自行处理
                ffmpegPlayer.play(videoFile, 0, 0, 0);
            } else {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        cardLayout.show(cardPanel, CARD_UNAVAILABLE);
                    }
                });
            }
            return;
        }

        // 相同视频已在播放 → 忽略
        if (currentVideo != null && currentVideo.equals(videoFile) && isPlaying) {
            return;
        }

        this.currentVideo = videoFile;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 停止当前播放
                    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                        mediaPlayer.stop();
                    }

                    // 播放新视频
                    final String mrl = videoFile.getAbsolutePath();
                    LogUtil.info("[VideoPlayerPanel] 开始播放: " + mrl);
                    mediaPlayer.playMedia(mrl);

                    // VLC 需要一点时间来解析媒体
                    Thread.sleep(500);

                    // 获取时长
                    totalDurationMs = mediaPlayer.getLength();

                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            cardLayout.show(cardPanel, CARD_PLAYER);
                            controlBar.setVisible(true);
                            isPlaying = true;
                            playPauseBtn.setText("⏸");
                            updateTimeLabel();
                            syncTimer.start();
                            LogUtil.info("[VideoPlayerPanel] 播放中, 时长: " + totalDurationMs + "ms");
                        }
                    });

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    LogUtil.error("[VideoPlayerPanel] 播放失败: " + e.getMessage());
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            cardLayout.show(cardPanel, CARD_EMPTY);
                        }
                    });
                }
            }
        }, "VLC-Play-Thread").start();
    }

    /**
     * 加载并自动播放视频文件（带元数据的重载版本）。
     *
     * <p>优先使用已有元数据（fps、分辨率），避免 ffmpeg 降级时重复探测。
     * VLC 可用时行为与 {@link #play(File)} 相同。</p>
     *
     * @param info 视频文件信息（含元数据）
     */
    public void play(final VideoFileInfo info) {
        if (info == null || info.getSourceFile() == null) return;

        final File videoFile = info.getSourceFile();

        // VLC 可用 → 走原有 VLCJ 路径
        if (vlcUsable) {
            play(videoFile);
            return;
        }

        // VLC 不可用 → 走 ffmpeg 降级或显示不可用
        if (videoFile == null || !videoFile.exists()) {
            LogUtil.info("[VideoPlayerPanel] 视频文件不存在: " + videoFile);
            return;
        }

        LogUtil.info("[VideoPlayerPanel] VLC 不可用，使用 ffmpeg 降级引擎");
        this.currentVideo = videoFile;

        if (ffmpegPlayer != null) {
            final double fps = info.getFps();
            final int w = info.getWidth();
            final int h = info.getHeight();
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    cardLayout.show(cardPanel, CARD_FFMPEG);
                }
            });
            ffmpegPlayer.play(videoFile, fps, w, h);
        } else {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    cardLayout.show(cardPanel, CARD_UNAVAILABLE);
                }
            });
        }
    }

    /**
     * 停止播放并清空画面。
     */
    public void stop() {
        // 停止 VLCJ
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (Exception ignored) { /* already stopped */ }
        }
        // 停止 ffmpeg 降级
        if (ffmpegPlayer != null) {
            ffmpegPlayer.stop();
        }
        isPlaying = false;
        if (syncTimer != null) {
            syncTimer.stop();
        }
        currentVideo = null;
        totalDurationMs = 0;

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                controlBar.setVisible(false);
                playPauseBtn.setText("▶");
                seekSlider.setValue(0);
                timeLabel.setText("00:00 / 00:00");
                cardLayout.show(cardPanel, CARD_EMPTY);
            }
        });
    }

    /**
     * 暂停播放。
     */
    public void pause() {
        if (mediaPlayer != null && isPlaying) {
            mediaPlayer.pause();
            isPlaying = false;
            playPauseBtn.setText("▶");
        }
    }

    /**
     * 继续播放。
     */
    public void resume() {
        if (mediaPlayer != null && !isPlaying && currentVideo != null) {
            mediaPlayer.play();
            isPlaying = true;
            playPauseBtn.setText("⏸");
        }
    }

    /**
     * 切换播放/暂停。
     */
    public void togglePlayPause() {
        if (isPlaying) {
            pause();
        } else {
            resume();
        }
    }

    /**
     * 释放 VLCJ 资源（应用退出时调用）。
     */
    public void release() {
        if (syncTimer != null) {
            syncTimer.stop();
        }
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (Exception ignored) { }
        }
        if (mediaPlayerComponent != null) {
            mediaPlayerComponent.release();
        }
        if (ffmpegPlayer != null) {
            ffmpegPlayer.release();
        }
        mediaPlayer = null;
        mediaPlayerComponent = null;
        LogUtil.info("[VideoPlayerPanel] 播放资源已释放");
    }

    // ==================== 内部状态同步 ====================

    /**
     * Timer 驱动的状态同步（每 200ms 执行一次）。
     * 在 EDT 中执行，更新进度条和时间标签。
     */
    private void syncPlayerState() {
        if (mediaPlayer == null || !isPlaying || isSeeking) {
            return;
        }

        try {
            long time = mediaPlayer.getTime();

            // 重新获取时长（VLC 可能需要一点时间才能报告准确时长）
            if (totalDurationMs <= 0) {
                totalDurationMs = mediaPlayer.getLength();
            }

            if (totalDurationMs > 0) {
                int sliderValue = (int) (time * 1000L / totalDurationMs);
                seekSlider.setValue(Math.min(sliderValue, 1000));
            }

            updateTimeLabel();

            // 播放结束检测
            if (totalDurationMs > 0 && time >= totalDurationMs - 500 && time > 0) {
                // 视频播放结束，停止 Timer
                syncTimer.stop();
                isPlaying = false;
                playPauseBtn.setText("▶");
                seekSlider.setValue(0);
                timeLabel.setText(formatTime(totalDurationMs) + " / " + formatTime(totalDurationMs));
            }
        } catch (Exception e) {
            // VLC 可能临时不可用，静默忽略
        }
    }

    /**
     * 更新时间标签（当前时间 / 总时长）。
     */
    private void updateTimeLabel() {
        if (mediaPlayer == null) return;
        long current = mediaPlayer.getTime();
        timeLabel.setText(formatTime(current) + " / " + formatTime(totalDurationMs));
    }

    /**
     * 切换静音。
     */
    private void toggleMute() {
        if (mediaPlayer == null) return;
        if (isMuted) {
            mediaPlayer.setVolume(savedVolume > 0 ? savedVolume : 100);
            volumeSlider.setValue(savedVolume > 0 ? savedVolume : 100);
            isMuted = false;
        } else {
            savedVolume = mediaPlayer.getVolume();
            mediaPlayer.mute();
            volumeSlider.setValue(0);
            isMuted = true;
        }
        updateVolumeIcon();
    }

    /**
     * 更新音量图标。
     */
    private void updateVolumeIcon() {
        int vol = volumeSlider.getValue();
        if (vol == 0 || isMuted) {
            volumeBtn.setText("🔇");
        } else if (vol < 70) {
            volumeBtn.setText("🔉");
        } else {
            volumeBtn.setText("🔊");
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 格式化毫秒为时间字符串（HH:MM:SS 或 MM:SS）。
     */
    static String formatTime(long totalMs) {
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

    // ==================== Getter ====================

    /** VLC 是否可用（供外部判断是否需要降级） */
    public boolean isVlcUsable() {
        return vlcUsable;
    }

    /** 获取当前播放的视频文件 */
    public File getCurrentVideo() {
        return currentVideo;
    }
}
