@echo off
start "J06 Server" cmd /c ""%~dp0run.bat" server"
timeout /t 3 >nul
call "%~dp0run.bat" client 127.0.0.1
