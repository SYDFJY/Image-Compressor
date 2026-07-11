/**
 * NCHU Image Compressor — Windows EXE 启动器 (v2)
 *
 * 编译: gcc -mwindows -O2 -s -o image-compressor.exe launcher.c -ladvapi32
 *
 * Java 查找策略（按优先级）:
 *   1. PATH — 直接尝试运行 javaw.exe（CreateProcess 自动搜索 PATH）
 *   2. 注册表 — 查找已安装的 JRE/JDK（HKLM + HKCU）
 *   3. 常见路径 — C:\Program Files\Java\ 等
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

/* 尝试直接运行 javaw（CreateProcess 会搜索 PATH） */
static int tryLaunchFromPath(const char *jarPath) {
    char cmdLine[MAX_PATH * 4];
    snprintf(cmdLine, sizeof(cmdLine), "javaw.exe -jar \"%s\"", jarPath);

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
                    // 读取 CurrentVersion
                    char ver[64] = {0};
                    DWORD verSize = sizeof(ver);
                    if (RegQueryValueExA(hKey, "CurrentVersion", NULL, &type,
                                        (BYTE *)ver, &verSize) == ERROR_SUCCESS && ver[0]) {
                        // 打开版本子键
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
static int launchFromHome(const char *javaHome, const char *jarPath) {
    char javaw[MAX_PATH];
    snprintf(javaw, sizeof(javaw), "%s\\bin\\javaw.exe", javaHome);

    char cmdLine[MAX_PATH * 4];
    snprintf(cmdLine, sizeof(cmdLine), "\"%s\" -jar \"%s\"", javaw, jarPath);

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
static int tryCommonPaths(const char *jarPath) {
    const char *paths[] = {
        "C:\\Program Files\\Java\\jre1.8",
        "C:\\Program Files (x86)\\Java\\jre1.8",
        "C:\\Program Files\\Java\\jdk1.8",
        "C:\\Program Files (x86)\\Java\\jdk1.8",
        NULL
    };
    WIN32_FIND_DATAA fd;
    const char *globs[] = {
        "C:\\Program Files\\Java\\jre*",
        "C:\\Program Files (x86)\\Java\\jre*",
        "C:\\Program Files\\Java\\jdk*",
        "C:\\Program Files (x86)\\Java\\jdk*",
        NULL
    };
    // 先试通配符匹配
    for (int i = 0; globs[i]; i++) {
        HANDLE h = FindFirstFileA(globs[i], &fd);
        if (h != INVALID_HANDLE_VALUE) {
            do {
                if (fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY && fd.cFileName[0] != '.') {
                    char home[MAX_PATH];
                    int len = (int)strlen(globs[i]) - 4; // remove "jre*" or "jdk*"
                    snprintf(home, sizeof(home), "%.*s%s", len, globs[i], fd.cFileName);
                    if (launchFromHome(home, jarPath)) {
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
    MessageBoxA(NULL, msg, "NCHU Image Compressor", MB_OK | MB_ICONERROR);
}

int WINAPI WinMain(HINSTANCE hInst, HINSTANCE hPrev, LPSTR cmdLine, int nShow) {
    char exeDir[MAX_PATH];
    char jarPath[MAX_PATH];
    char javaHome[MAX_PATH];

    // 1. 查找 JAR
    getExeDir(exeDir, sizeof(exeDir));
    if (!findJar(exeDir, jarPath, sizeof(jarPath))) {
        showError("找不到 image-compressor-*.jar\n\n请确保 EXE 与 JAR 文件放在同一目录下。");
        return 1;
    }

    // 2. 策略1：直接尝试 PATH 中的 javaw（最快，覆盖大多数情况）
    if (tryLaunchFromPath(jarPath)) return 0;

    // 3. 策略2：注册表查找
    if (tryRegistryJavaHome(javaHome, sizeof(javaHome))) {
        if (launchFromHome(javaHome, jarPath)) return 0;
    }

    // 4. 策略3：常见安装路径
    if (tryCommonPaths(jarPath)) return 0;

    // 5. 全部失败
    showError("未检测到 Java 运行环境\n\n"
              "请先安装 Java 8 或更高版本：\n"
              "https://www.java.com/download/\n\n"
              "安装完成后重新运行本程序。");
    return 1;
}
