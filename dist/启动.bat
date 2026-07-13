@echo off
chcp 65001 >nul
title NCHU Compressor

:: ============================================
::  NCHU Compressor — Windows 启动脚本
::  版本: 1.0.0 | 图片/视频双模压缩工具
::  南昌航空大学 软件工程 暑期实训项目
:: ============================================

:: 查找 Java 运行环境
set JAVA_EXE=java
where java >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [错误] 未找到 Java 运行环境，请安装 JDK 1.8 或以上版本
    echo 下载地址: https://www.oracle.com/java/technologies/downloads/
    pause
    exit /b 1
)

:: 显示 Java 版本
echo 检测到 Java 环境:
java -version 2>&1 | findstr /i "version"
echo.

:: 检测 FFmpeg（优先使用捆绑版，其次系统 PATH）
set FFMPEG_OPTS=
if exist "%~dp0standalone\ffmpeg\bin\ffmpeg.exe" (
    echo 检测到捆绑版 FFmpeg（standalone\ffmpeg\bin\），视频压缩功能可用
    set FFMPEG_OPTS=-Dffmpeg.bin.path="%~dp0standalone\ffmpeg\bin"
    echo.
) else (
    where ffmpeg >nul 2>&1
    if %ERRORLEVEL% NEQ 0 (
        echo [提示] 未检测到 FFmpeg，视频压缩功能不可用
        echo   安装 FFmpeg 4.0+ 后可使用视频压缩: https://ffmpeg.org/download.html
        echo   或将免安装版解压后使用捆绑的 FFmpeg
        echo.
    ) else (
        echo 检测到系统 FFmpeg，视频压缩功能可用
        echo.
    )
)

:: 检测 VLC（优先使用捆绑版，其次系统 PATH）
set VLC_OPTS=
if exist "%~dp0standalone\vlc\libvlc.dll" (
    echo 检测到捆绑版 VLC（standalone\vlc\），内嵌播放功能可用
    set "PATH=%~dp0standalone\vlc;%PATH%"
    set VLC_PLUGIN_PATH=%~dp0standalone\vlc\plugins
    set VLC_OPTS=-Djna.library.path="%~dp0standalone\vlc"
    echo.
) else (
    echo [提示] 未检测到 VLC，内嵌视频播放功能不可用
    echo.
)

:: 启动程序（最大堆内存 512MB）
echo 正在启动 NCHU Compressor...
start javaw %FFMPEG_OPTS% %VLC_OPTS% -Xmx512m -jar "%~dp0image-compressor-1.0.0.jar"

exit /b 0
