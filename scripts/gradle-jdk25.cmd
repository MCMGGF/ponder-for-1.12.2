@echo off
setlocal

if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Java\zulu25.36.205-ca-jdk25.0.4.1-win_x64"
set "PATH=%JAVA_HOME%\bin;%PATH%"

call "%~dp0..\gradlew.bat" %*
exit /b %ERRORLEVEL%
