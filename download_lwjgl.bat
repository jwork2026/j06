@echo off
setlocal
cd /d "%~dp0"
set "CLASSIFIER=windows"
if /I "%PROCESSOR_ARCHITECTURE%"=="ARM64" set "CLASSIFIER=windows-arm64"
if not exist lib\lwjgl mkdir lib\lwjgl
for %%m in (lwjgl lwjgl-glfw lwjgl-opengl) do (
  curl --fail --location --retry 2 "https://repo.maven.apache.org/maven2/org/lwjgl/%%m/3.3.6/%%m-3.3.6.jar" -o "lib/lwjgl/%%m-3.3.6.jar"
  if errorlevel 1 exit /b 1
  curl --fail --location --retry 2 "https://repo.maven.apache.org/maven2/org/lwjgl/%%m/3.3.6/%%m-3.3.6-natives-%CLASSIFIER%.jar" -o "lib/lwjgl/%%m-3.3.6-natives-%CLASSIFIER%.jar"
  if errorlevel 1 exit /b 1
)
