@echo off
cd /d "%~dp0"
start "" "%~dp0jre\bin\javaw.exe" -Dffmpeg.bin.path="%~dp0ffmpeg\bin" -Xmx512m -jar "%~dp0image-compressor-1.0.0.jar"
