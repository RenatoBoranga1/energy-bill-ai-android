@echo off
setlocal

set "PROJECT_DIR=%~dp0"
if "%PROJECT_DIR:~-1%"=="\" set "PROJECT_DIR=%PROJECT_DIR:~0,-1%"

set "ANDROID_USER_HOME=%PROJECT_DIR%\.android"
if not exist "%ANDROID_USER_HOME%" mkdir "%ANDROID_USER_HOME%"

if not defined JAVA_HOME (
  if exist "C:\Program Files\Android\Android Studio\jbr" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
    set "PATH=%JAVA_HOME%\bin;%PATH%"
  )
)

set "GRADLE_USER_HOME=%PROJECT_DIR%\.gradle-user"
set "GRADLE_OPTS=-Dorg.gradle.native=false -Dorg.gradle.vfs.watch=false"
set "GRADLE_JVMARGS=-Xmx4g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8"
set "WRAPPER_DIR=%GRADLE_USER_HOME%\wrapper\dists"
set "FALLBACK_WRAPPER_DIR=%USERPROFILE%\.gradle\wrapper\dists"
set "GRADLE_CMD=%WRAPPER_DIR%\gradle-8.2-bin\bbg7u40eoinfdyxsxr3z4i7ta\gradle-8.2\bin\gradle.bat"

if exist "%GRADLE_CMD%" goto :run

for /f "delims=" %%D in ('where /r "%WRAPPER_DIR%" gradle.bat 2^>nul') do (
  set "GRADLE_CMD=%%D"
  goto :run
)

set "GRADLE_CMD=%FALLBACK_WRAPPER_DIR%\gradle-8.2-bin\bbg7u40eoinfdyxsxr3z4i7ta\gradle-8.2\bin\gradle.bat"
if exist "%GRADLE_CMD%" goto :run

for /f "delims=" %%D in ('where /r "%FALLBACK_WRAPPER_DIR%" gradle.bat 2^>nul') do (
  set "GRADLE_CMD=%%D"
  goto :run
)

echo Gradle distribution not found in %WRAPPER_DIR% or %FALLBACK_WRAPPER_DIR%.
exit /b 1

:run
call "%GRADLE_CMD%" -Dorg.gradle.jvmargs="%GRADLE_JVMARGS%" -Dkotlin.daemon.jvm.options=-Xmx2g -Dorg.gradle.workers.max=2 --no-daemon %*
