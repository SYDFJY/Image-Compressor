@echo off
cd /d "%~dp0"
set "PATH=%~dp0vlc;%PATH%"
start "" "%~dp0jre\bin\javaw.exe" -Dffmpeg.bin.path="%~dp0ffmpeg\bin" -Djna.library.path="%~dp0vlc" -Djava.library.path="%~dp0vlc" -Xmx512m -jar "%~dp0image-compressor-1.0.0.jar"
