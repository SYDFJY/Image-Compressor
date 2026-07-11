@echo off
chcp 65001 >nul
title NCHU Image Compressor

:: ============================================
::  NCHU Image Compressor — Windows 启动脚本
::  版本: 1.0.0 | 南昌航空大学 软件工程 暑期实训项目
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

:: 启动程序（最大堆内存 512MB）
echo 正在启动 NCHU Image Compressor...
start javaw -Xmx512m -jar "%~dp0image-compressor-1.0.0.jar"

exit /b 0
