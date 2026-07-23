Option Explicit

Dim objShell, objFSO, strDir, strJar, strFfmpeg, strVlc
Dim strCmd, strFfmpegPath, strVlcPath

Set objShell = CreateObject("WScript.Shell")
Set objFSO   = CreateObject("Scripting.FileSystemObject")

strDir = objFSO.GetParentFolderName(WScript.ScriptFullName)
strJar = strDir & "\image-compressor-1.0.0.jar"

If Not objFSO.FileExists(strJar) Then
    Call MsgBox("Missing: " & strJar, 48, "NCHU Compressor")
    WScript.Quit 1
End If

' FFmpeg: prefer bundled (standalone/) else system PATH
strFfmpegPath = ""
If objFSO.FileExists(strDir & "\standalone\ffmpeg\bin\ffmpeg.exe") Then
    strFfmpegPath = strDir & "\standalone\ffmpeg\bin"
End If

' VLC: prefer bundled (standalone/) else system
strVlcPath = ""
If objFSO.FileExists(strDir & "\standalone\vlc\libvlc.dll") Then
    strVlcPath = strDir & "\standalone\vlc"
    objShell.Environment("Process")("PATH") = strVlcPath & ";" & objShell.Environment("Process")("PATH")
    objShell.Environment("Process")("VLC_PLUGIN_PATH") = strVlcPath & "\plugins"
End If

' Build command
Dim q : q = Chr(34)
strCmd = "javaw"
If strFfmpegPath <> "" Then
    strCmd = strCmd & " -Dffmpeg.bin.path=" & q & strFfmpegPath & q
End If
If strVlcPath <> "" Then
    strCmd = strCmd & " -Djna.library.path=" & q & strVlcPath & q & _
             " -Djava.library.path=" & q & strVlcPath & q
End If
strCmd = strCmd & " -Xmx512m -jar " & q & strJar & q

objShell.Run strCmd, 1, False

Set objShell = Nothing
Set objFSO   = Nothing
