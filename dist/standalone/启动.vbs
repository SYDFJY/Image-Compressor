Option Explicit

Dim objShell, objFSO, strDir, strJre, strJar, strFfmpeg, strVlc
Dim strCmd, objEnv, q

Set objShell = CreateObject("WScript.Shell")
Set objFSO   = CreateObject("Scripting.FileSystemObject")

strDir    = objFSO.GetParentFolderName(WScript.ScriptFullName)
strJre    = strDir & "\jre\bin\javaw.exe"
strJar    = strDir & "\image-compressor-1.0.0.jar"
strFfmpeg = strDir & "\ffmpeg\bin"
strVlc    = strDir & "\vlc"

If Not objFSO.FileExists(strJar) Then
    Call MsgBox("Missing: " & strJar, 48, "NCHU Compressor")
    WScript.Quit 1
End If

If Not objFSO.FileExists(strJre) Then
    Call MsgBox("Missing: " & strJre, 48, "NCHU Compressor")
    WScript.Quit 1
End If

Set objEnv = objShell.Environment("Process")
objEnv("PATH") = strVlc & ";" & objEnv("PATH")

q = Chr(34)
strCmd = q & strJre   & q & _
         " -Dffmpeg.bin.path="   & q & strFfmpeg & q & _
         " -Djna.library.path="  & q & strVlc    & q & _
         " -Djava.library.path=" & q & strVlc    & q & _
         " -Xmx512m -jar "       & q & strJar    & q

objShell.CurrentDirectory = strDir
objShell.Run strCmd, 1, False

Set objShell = Nothing
Set objFSO   = Nothing
