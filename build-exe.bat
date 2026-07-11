@echo off
chcp 65001 >nul
echo ========================================
echo   NCHU Image Compressor — EXE 构建脚本
echo ========================================
echo.
echo 编译原生 Windows 启动器 (需 GCC/MinGW)...
echo.

:: 检查 gcc
where gcc >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [错误] 未找到 gcc，请先安装 MinGW-w64
    echo 下载: https://winlibs.com/
    pause
    exit /b 1
)

:: 编译
gcc -mwindows -O2 -s ^
  -o target\image-compressor.exe ^
  src\main\native\launcher.c ^
  -ladvapi32

if %ERRORLEVEL% EQU 0 (
    echo.
    echo [成功] target\image-compressor.exe 已生成
    dir target\image-compressor.exe
) else (
    echo [失败] 编译出错
)

pause
