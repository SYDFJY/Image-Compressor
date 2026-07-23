/**
 * NCHU Compressor — Windows EXE 启动器 (v3 — 免安装版)
 *
 * 编译: gcc -mwindows -O2 -s -o image-compressor.exe launcher.c -ladvapi32
 *
 * Java 查找策略（按优先级）:
 *   0. 捆绑 JRE — 同目录 jre\bin\javaw.exe 或 standalone\jre\bin\javaw.exe
 *   1. PATH — 直接尝试运行 javaw.exe（CreateProcess 自动搜索 PATH）
 *   2. 注册表 — 查找已安装的 JRE/JDK（HKLM + HKCU）
 *   3. 常见路径 — C:\Program Files\Java\ 等
 *
 * 额外 JVM 参数（自动检测捆绑组件）:
 *   -Dffmpeg.bin.path=./ffmpeg/bin   （若检测到捆绑 FFmpeg）
 *   -Djna.library.path=./vlc          （若检测到捆绑 VLC）
 */

#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdio.h>
#include <string.h>

/* 获取 EXE 所在目录（末尾带反斜杠） */
static void getExeDir(char *buf, int size) {
    GetModuleFileNameA(NULL, buf, size);
    char *p = strrchr(buf, '\\');
    if (p) *(p + 1) = '\0';
}

/* 在同目录查找 image-compressor-*.jar */
static int findJar(const char *dir, char *out, int outSize) {
    WIN32_FIND_DATAA fd;
    char pattern[MAX_PATH];
    snprintf(pattern, sizeof(pattern), "%simage-compressor-*.jar", dir);
    HANDLE h = FindFirstFileA(pattern, &fd);
    if (h == INVALID_HANDLE_VALUE) return 0;
    snprintf(out, outSize, "%s%s", dir, fd.cFileName);
    FindClose(h);
    return 1;
}

/* 检测文件是否存在 */
static int fileExists(const char *path) {
    DWORD attr = GetFileAttributesA(path);
    return (attr != INVALID_FILE_ATTRIBUTES && !(attr & FILE_ATTRIBUTE_DIRECTORY));
}

/* 检测目录是否存在 */
static int dirExists(const char *path) {
    DWORD attr = GetFileAttributesA(path);
    return (attr != INVALID_FILE_ATTRIBUTES && (attr & FILE_ATTRIBUTE_DIRECTORY));
}

/* 构建额外 JVM 参数（FFmpeg + VLC 路径） */
static void buildExtraArgs(const char *exeDir, char *buf, int bufSize) {
    buf[0] = '\0';
    char path[MAX_PATH];
    int hasExtra = 0;

    /* 检测捆绑 FFmpeg（同目录 + standalone 子目录） */
    snprintf(path, sizeof(path), "%sffmpeg\\bin\\ffmpeg.exe", exeDir);
    if (!fileExists(path)) {
        snprintf(path, sizeof(path), "%sstandalone\\ffmpeg\\bin\\ffmpeg.exe", exeDir);
    }
    if (fileExists(path)) {
        /* 提取目录部分（去掉 ffmpeg.exe） */
        char *p = strrchr(path, '\\');
        if (p) *p = '\0';
        int len = (int)strlen(buf);
        snprintf(buf + len, bufSize - len, " -Dffmpeg.bin.path=\"%s\"", path);
        hasExtra = 1;
    }

    /* 检测捆绑 VLC（同目录 + standalone 子目录） */
    snprintf(path, sizeof(path), "%svlc\\libvlc.dll", exeDir);
    if (!fileExists(path)) {
        snprintf(path, sizeof(path), "%sstandalone\\vlc\\libvlc.dll", exeDir);
    }
    if (fileExists(path)) {
        /* 提取 VLC 目录 */
        char *p = strrchr(path, '\\');
        if (p) *p = '\0';
        int len = (int)strlen(buf);
        snprintf(buf + len, bufSize - len, " -Djna.library.path=\"%s\"", path);
        hasExtra = 1;
    }

    (void)hasExtra; /* suppress unused warning */
}

/* 尝试直接运行 javaw（CreateProcess 会搜索 PATH） */
static int tryLaunchFromPath(const char *jarPath, const char *extraArgs) {
    char cmdLine[MAX_PATH * 8];
    snprintf(cmdLine, sizeof(cmdLine), "javaw.exe%s -jar \"%s\"", extraArgs, jarPath);

    STARTUPINFOA si = { sizeof(si) };
    PROCESS_INFORMATION pi;
    si.dwFlags = STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;

    if (CreateProcessA(NULL, cmdLine, NULL, NULL, FALSE,
                       CREATE_NO_WINDOW, NULL, NULL, &si, &pi)) {
        CloseHandle(pi.hProcess);
        CloseHandle(pi.hThread);
        return 1;
    }
    return 0;
}

/* 从注册表读取 Java Home（32位+64位 + HKLM+HKCU） */
static int tryRegistryJavaHome(char *buf, int size) {
    HKEY hKey;
    DWORD type, cb;
    const char *keys[] = {
        "SOFTWARE\\JavaSoft\\Java Runtime Environment",
        "SOFTWARE\\JavaSoft\\Java Development Kit",
        "SOFTWARE\\JavaSoft\\JDK",
        NULL
    };
    HKEY roots[] = { HKEY_LOCAL_MACHINE, HKEY_CURRENT_USER };
    DWORD flags[] = { KEY_READ | KEY_WOW64_64KEY, KEY_READ | KEY_WOW64_32KEY };

    for (int r = 0; r < 2; r++) {
        for (int i = 0; keys[i]; i++) {
            for (int f = 0; f < 2; f++) {
                if (RegOpenKeyExA(roots[r], keys[i], 0, flags[f], &hKey) == ERROR_SUCCESS) {
                    /* 读取 CurrentVersion */
                    char ver[64] = {0};
                    DWORD verSize = sizeof(ver);
                    if (RegQueryValueExA(hKey, "CurrentVersion", NULL, &type,
                                        (BYTE *)ver, &verSize) == ERROR_SUCCESS && ver[0]) {
                        /* 打开版本子键 */
                        HKEY hVerKey;
                        if (RegOpenKeyExA(hKey, ver, 0, flags[f], &hVerKey) == ERROR_SUCCESS) {
                            cb = size;
                            if (RegQueryValueExA(hVerKey, "JavaHome", NULL, &type,
                                                (BYTE *)buf, &cb) == ERROR_SUCCESS && type == REG_SZ) {
                                RegCloseKey(hVerKey);
                                RegCloseKey(hKey);
                                return 1;
                            }
                            RegCloseKey(hVerKey);
                        }
                    }
                    RegCloseKey(hKey);
                }
            }
        }
    }
    return 0;
}

/* 使用 javaw.exe 从指定 JavaHome 启动 */
static int launchFromHome(const char *javaHome, const char *jarPath, const char *extraArgs) {
    char javaw[MAX_PATH];
    snprintf(javaw, sizeof(javaw), "%s\\bin\\javaw.exe", javaHome);

    char cmdLine[MAX_PATH * 8];
    snprintf(cmdLine, sizeof(cmdLine), "\"%s\"%s -jar \"%s\"", javaw, extraArgs, jarPath);

    STARTUPINFOA si = { sizeof(si) };
    PROCESS_INFORMATION pi;
    si.dwFlags = STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;

    if (CreateProcessA(NULL, cmdLine, NULL, NULL, FALSE,
                       CREATE_NO_WINDOW, NULL, NULL, &si, &pi)) {
        CloseHandle(pi.hProcess);
        CloseHandle(pi.hThread);
        return 1;
    }
    return 0;
}

/* 常见安装路径 */
static int tryCommonPaths(const char *jarPath, const char *extraArgs) {
    WIN32_FIND_DATAA fd;
    const char *globs[] = {
        "C:\\Program Files\\Java\\jre*",
        "C:\\Program Files (x86)\\Java\\jre*",
        "C:\\Program Files\\Java\\jdk*",
        "C:\\Program Files (x86)\\Java\\jdk*",
        NULL
    };
    for (int i = 0; globs[i]; i++) {
        HANDLE h = FindFirstFileA(globs[i], &fd);
        if (h != INVALID_HANDLE_VALUE) {
            do {
                if (fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY && fd.cFileName[0] != '.') {
                    char home[MAX_PATH];
                    int len = (int)strlen(globs[i]) - 4; /* remove "jre*" or "jdk*" */
                    snprintf(home, sizeof(home), "%.*s%s", len, globs[i], fd.cFileName);
                    if (launchFromHome(home, jarPath, extraArgs)) {
                        FindClose(h);
                        return 1;
                    }
                }
            } while (FindNextFileA(h, &fd));
            FindClose(h);
        }
    }
    return 0;
}

static void showError(const char *msg) {
    MessageBoxA(NULL, msg, "NCHU Compressor", MB_OK | MB_ICONERROR);
}

int WINAPI WinMain(HINSTANCE hInst, HINSTANCE hPrev, LPSTR cmdLine, int nShow) {
    char exeDir[MAX_PATH];
    char jarPath[MAX_PATH];
    char javaHome[MAX_PATH];
    char extraArgs[MAX_PATH * 4];

    /* 0. 准备工作：查找 JAR + 构建额外 JVM 参数 */
    getExeDir(exeDir, sizeof(exeDir));
    if (!findJar(exeDir, jarPath, sizeof(jarPath))) {
        showError("找不到 image-compressor-*.jar\n\n请确保 EXE 与 JAR 文件放在同一目录下。");
        return 1;
    }

    buildExtraArgs(exeDir, extraArgs, sizeof(extraArgs));

    /* 1. 策略0：捆绑 JRE（免安装版核心 — 优先于系统 Java）*/
    const char *bundledPaths[] = {
        "jre",              /* 同目录: dist/standalone/ 布局 */
        "standalone\\jre",  /* 子目录: dist/ 或项目根 布局 */
        NULL
    };
    for (int i = 0; bundledPaths[i]; i++) {
        char bundledHome[MAX_PATH];
        snprintf(bundledHome, sizeof(bundledHome), "%s%s", exeDir, bundledPaths[i]);
        char javawExe[MAX_PATH];
        snprintf(javawExe, sizeof(javawExe), "%s\\bin\\javaw.exe", bundledHome);
        if (fileExists(javawExe)) {
            if (launchFromHome(bundledHome, jarPath, extraArgs)) return 0;
        }
    }

    /* 2. 策略1：直接尝试 PATH 中的 javaw（最快，覆盖大多数情况） */
    if (tryLaunchFromPath(jarPath, extraArgs)) return 0;

    /* 3. 策略2：注册表查找 */
    if (tryRegistryJavaHome(javaHome, sizeof(javaHome))) {
        if (launchFromHome(javaHome, jarPath, extraArgs)) return 0;
    }

    /* 4. 策略3：常见安装路径 */
    if (tryCommonPaths(jarPath, extraArgs)) return 0;

    /* 5. 全部失败 */
    showError("未检测到 Java 运行环境\n\n"
              "请先安装 Java 8 或更高版本：\n"
              "https://www.java.com/download/\n\n"
              "安装完成后重新运行本程序。\n\n"
              "如果使用免安装版，请确保 jre 文件夹与程序在同一目录。");
    return 1;
}
