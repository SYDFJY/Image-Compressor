' ============================================
'  NCHU Image Compressor — Windows 静默启动器
'  版本: 1.0.0 | 双击此文件即可无控制台启动
' ============================================

Set objShell = CreateObject("WScript.Shell")
Set objFSO = CreateObject("Scripting.FileSystemObject")

' 获取脚本所在目录
strDir = objFSO.GetParentFolderName(WScript.ScriptFullName)
strJar = strDir & "\image-compressor-1.0.0.jar"

' 检查 JAR 文件是否存在
If Not objFSO.FileExists(strJar) Then
    MsgBox "找不到程序文件: " & vbCrLf & strJar, 48, "NCHU Image Compressor — 错误"
    WScript.Quit 1
End If

' 静默启动（0=隐藏窗口, False=不等待）
objShell.Run "javaw -Xmx512m -jar """ & strJar & """", 0, False

Set objShell = Nothing
Set objFSO = Nothing
