@echo off
setlocal

set "BASE_DIR=%~dp0"
set "MAVEN_VERSION=3.9.9"
set "WRAPPER_DIR=%BASE_DIR%.mvn\wrapper"
set "MAVEN_HOME=%WRAPPER_DIR%\apache-maven-%MAVEN_VERSION%"
set "MAVEN_BIN=%MAVEN_HOME%\bin\mvn.cmd"

where mvn >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  mvn %*
  exit /b %ERRORLEVEL%
)

if not exist "%MAVEN_BIN%" (
  if not exist "%WRAPPER_DIR%" mkdir "%WRAPPER_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$ErrorActionPreference='Stop';" ^
    "$version='%MAVEN_VERSION%';" ^
    "$dir='%WRAPPER_DIR%';" ^
    "$zip=Join-Path $dir ('apache-maven-' + $version + '-bin.zip');" ^
    "$url='https://archive.apache.org/dist/maven/maven-3/' + $version + '/binaries/apache-maven-' + $version + '-bin.zip';" ^
    "Invoke-WebRequest -Uri $url -OutFile $zip;" ^
    "Expand-Archive -Path $zip -DestinationPath $dir -Force;"
  if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%
)

"%MAVEN_BIN%" %*
exit /b %ERRORLEVEL%
