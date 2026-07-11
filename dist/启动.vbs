' ============================================
'  NCHU Compressor — Windows 静默启动器
'  版本: 1.0.0 | 图片/视频双模压缩工具
'  双击此文件即可无控制台启动
'  优先使用捆绑版 FFmpeg，其次使用系统 PATH
' ============================================

Set objShell = CreateObject("WScript.Shell")
Set objFSO   = CreateObject("Scripting.FileSystemObject")

' ---- 获取脚本所在目录 ----
strDir = objFSO.GetParentFolderName(WScript.ScriptFullName)
strJar = strDir & "\image-compressor-1.0.0.jar"

' ---- 检查 JAR 文件 ----
If Not objFSO.FileExists(strJar) Then
    MsgBox "找不到程序文件:" & vbCrLf & strJar, 48, "NCHU Compressor — 错误"
    WScript.Quit 1
End If

' ---- 检测 FFmpeg（优先捆绑版）----
strFfmpegOpts = ""
strBundledFfmpeg = strDir & "\standalone\ffmpeg\bin\ffmpeg.exe"
If objFSO.FileExists(strBundledFfmpeg) Then
    strFfmpegPath = strDir & "\standalone\ffmpeg\bin"
    strFfmpegOpts = "-Dffmpeg.bin.path=""" & strFfmpegPath & """"
End If

' ---- 静默启动（0=隐藏窗口, False=不等待）----
objShell.Run "javaw " & strFfmpegOpts & " -Xmx512m -jar """ & strJar & """", 0, False

Set objShell = Nothing
Set objFSO   = Nothing
