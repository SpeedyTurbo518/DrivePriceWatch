@echo off
setlocal
cd /d "%~dp0"
echo Building Drive Price Watch APK... this can take 3-10 minutes the first time.
echo RUNNING> build_status.txt

rem ---- Find Java (Android Studio ships its own) ----
set "JBR="
for %%P in ("%ProgramFiles%\Android\Android Studio\jbr" "%LOCALAPPDATA%\Programs\Android Studio\jbr" "%ProgramFiles%\Android\Android Studio1\jbr" "%ProgramFiles%\Android\Android Studio Preview\jbr") do (
  if not defined JBR if exist "%%~P\bin\java.exe" set "JBR=%%~P"
)
if not defined JBR if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JBR=%JAVA_HOME%"
if not defined JBR (
  for /d %%D in ("%USERPROFILE%\.jdks\*") do if exist "%%~D\bin\java.exe" set "JBR=%%~D"
)
if not defined JBR (
  echo FAILED: could not find Java / Android Studio>build_status.txt
  echo Could not find Android Studio's Java. & goto :end
)
set "JAVA_HOME=%JBR%"
echo Using Java: %JAVA_HOME%

rem ---- Find Android SDK ----
set "SDK="
if defined ANDROID_HOME if exist "%ANDROID_HOME%\platforms" set "SDK=%ANDROID_HOME%"
if not defined SDK if defined ANDROID_SDK_ROOT if exist "%ANDROID_SDK_ROOT%\platforms" set "SDK=%ANDROID_SDK_ROOT%"
if not defined SDK if exist "%LOCALAPPDATA%\Android\Sdk\platforms" set "SDK=%LOCALAPPDATA%\Android\Sdk"
if not defined SDK (
  echo FAILED: could not find the Android SDK>build_status.txt
  echo Could not find the Android SDK. & goto :end
)
echo Using SDK: %SDK%
> local.properties echo sdk.dir=%SDK:\=\\%

rem ---- Build ----
call gradlew.bat assembleDebug --no-daemon --console=plain > build_log.txt 2>&1
if errorlevel 1 (
  echo FAILED: gradle build failed, see build_log.txt>build_status.txt
  echo Build failed - see build_log.txt & goto :end
)
copy /y "app\build\outputs\apk\debug\app-debug.apk" "..\DrivePriceWatch.apk" >nul
if errorlevel 1 (
  echo FAILED: APK not found after build>build_status.txt & goto :end
)
echo OK>build_status.txt
echo.
echo Done: %~dp0..\DrivePriceWatch.apk
:end
endlocal
