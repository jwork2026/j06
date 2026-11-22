@echo off
setlocal
cd /d "%~dp0"
if not exist lib\lwjgl\lwjgl-3.3.6.jar (
  echo Run download_lwjgl.bat first.
  exit /b 1
)
if not exist build\classes mkdir build\classes
(for /r src\main\java %%f in (*.java) do @echo "%%f") > build\sources.txt
javac --release 11 -encoding UTF-8 -cp "lib/lwjgl/*" -d build/classes @build/sources.txt
exit /b %errorlevel%
