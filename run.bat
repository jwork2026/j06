@echo off
setlocal
cd /d "%~dp0"
call compile.bat
if errorlevel 1 exit /b 1
if not defined GAME_PARALLEL set "GAME_PARALLEL=true"
set "MAIN=com.gameengine.example.Game"
if "%1"=="client" set "MAIN=com.gameengine.example.ClientLauncher"
if "%1"=="replay" set "MAIN=com.gameengine.example.ReplayLauncher"
if "%1"=="benchmark" set "MAIN=com.gameengine.example.ParallelBenchmark"
java -Dgame.parallel=%GAME_PARALLEL% -cp "build/classes;lib/lwjgl/*" %MAIN% %2 %3
