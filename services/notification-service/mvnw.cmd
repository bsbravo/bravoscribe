@echo off
setlocal enableextensions

set "BASE_DIR=%~dp0"
:find_basedir
if exist "%BASE_DIR%.mvn\" goto :basedir_found
for %%d in ("%BASE_DIR%..") do set "BASE_DIR=%%~fd\"
goto :find_basedir
:basedir_found

set "WRAPPER_PROPS=%BASE_DIR%.mvn\wrapper\maven-wrapper.properties"
if not exist "%WRAPPER_PROPS%" (
  echo ERROR: %WRAPPER_PROPS% not found. >&2
  exit /b 1
)

for /f "usebackq tokens=2 delims==" %%a in (`findstr /b "distributionUrl=" "%WRAPPER_PROPS%"`) do set "DIST_URL=%%a"
for %%a in ("%DIST_URL%") do set "DIST_FILE=%%~nxa"
set "DIST_NAME=%DIST_FILE:.zip=%"
set "INSTALL_DIR=%USERPROFILE%\.m2\wrapper\dists\%DIST_NAME%"
set "TMP_FILE=%INSTALL_DIR%\download.zip"

set "MAVEN_HOME="
if exist "%INSTALL_DIR%\" (
  for /d %%d in ("%INSTALL_DIR%\apache-maven-*") do set "MAVEN_HOME=%%d"
)

if not defined MAVEN_HOME (
  echo Downloading Maven: %DIST_URL% >&2
  if not exist "%INSTALL_DIR%\" mkdir "%INSTALL_DIR%"
  where curl >nul 2>&1
  if not errorlevel 1 (
    curl -fsSL "%DIST_URL%" -o "%TMP_FILE%"
  ) else (
    powershell -Command "Invoke-WebRequest -Uri '%DIST_URL%' -OutFile '%TMP_FILE%'"
  )
  powershell -Command "Expand-Archive -Path '%TMP_FILE%' -DestinationPath '%INSTALL_DIR%' -Force"
  del "%TMP_FILE%"
  for /d %%d in ("%INSTALL_DIR%\apache-maven-*") do set "MAVEN_HOME=%%d"
)

"%MAVEN_HOME%\bin\mvn.cmd" %*
