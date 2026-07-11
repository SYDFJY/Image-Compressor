' ============================================
'  NCHU Compressor — 免安装版静默启动器
'  版本: 1.0.0 | 图片/视频双模压缩工具
'  内含: JRE + FFmpeg + 启动脚本
'  无需安装 Java，解压后双击即可运行
' ============================================

Set objShell = CreateObject("WScript.Shell")
Set objFSO   = CreateObject("Scripting.FileSystemObject")

' ---- 获取脚本所在目录 ----
strDir = objFSO.GetParentFolderName(WScript.ScriptFullName)
strJar = strDir & "\image-compressor-1.0.0.jar"
strJre = strDir & "\jre\bin\javaw.exe"
strFfmpeg = strDir & "\ffmpeg\bin"

' ---- 检查必需文件 ----
If Not objFSO.FileExists(strJar) Then
    MsgBox "找不到程序文件:" & vbCrLf & strJar, 48, "NCHU Compressor — 错误"
    WScript.Quit 1
End If

If Not objFSO.FileExists(strJre) Then
    MsgBox "找不到 Java 运行环境:" & vbCrLf & strJre & vbCrLf & vbCrLf _
        & "请确保 jre\bin\javaw.exe 存在。", 48, "NCHU Compressor — 错误"
    WScript.Quit 1
End If

' ---- 启动程序 ----
' -Dffmpeg.bin.path 指向捆绑的 FFmpeg 二进制目录
' -Xmx512m 限制最大堆内存 512MB
' 0 = 隐藏控制台窗口, False = 不等待进程结束
objShell.Run """" & strJre & """ -Dffmpeg.bin.path=""" & strFfmpeg _
    & """ -Xmx512m -jar """ & strJar & """", 0, False

Set objShell = Nothing
Set objFSO   = Nothing
