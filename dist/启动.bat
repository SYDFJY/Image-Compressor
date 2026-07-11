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

:: 检查 FFmpeg（可选）
where ffmpeg >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [提示] 未检测到 FFmpeg，视频压缩功能不可用
    echo   安装 FFmpeg 4.0+ 后可使用视频压缩: https://ffmpeg.org/download.html
    echo.
) else (
    echo 检测到 FFmpeg，视频压缩功能可用
    echo.
)

:: 启动程序（最大堆内存 512MB）
echo 正在启动 NCHU Compressor...
start javaw -Xmx512m -jar "%~dp0image-compressor-1.0.0.jar"

exit /b 0
