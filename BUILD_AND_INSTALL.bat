@echo off
setlocal
cd /d "%~dp0"
echo Building Drive Price Watch APK... this can take 3-10 minutes the first time.
echo RUNNING> build_status.txt

rem ---- Find Java 17-24 (Gradle 8.x can't run on Java 25 yet) ----
set "JBR="
for /d %%D in ("%CD%\jdk21\*" "%USERPROFILE%\.jdks\*" "%ProgramFiles%\Eclipse Adoptium\*" "%ProgramFiles%\Java\*" "%ProgramFiles%\Microsoft\jdk-*" "%ProgramFiles%\Zulu\*") do (
  if not defined JBR if exist "%%~D\bin\java.exe" if exist "%%~D\release" (
    findstr /r /c:"^JAVA_VERSION=.1[7-9]" /c:"^JAVA_VERSION=.2[0-4]" "%%~D\release" >nul 2>&1 && set "JBR=%%~D"
  )
)
for %%P in ("%ProgramFiles%\Android\Android Studio\jbr" "%LOCALAPPDATA%\Programs\Android Studio\jbr") do (
  if not defined JBR if exist "%%~P\release" (
    findstr /r /c:"^JAVA_VERSION=.1[7-9]" /c:"^JAVA_VERSION=.2[0-4]" "%%~P\release" >nul 2>&1 && set "JBR=%%~P"
  )
)
if not defined JBR (
  echo No Java 17-21 found. Downloading a portable Java 21 ^(Eclipse Temurin^) into this folder, about 190 MB...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -UseBasicParsing 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse' -OutFile 'jdk21.zip'; Expand-Archive -Force 'jdk21.zip' 'jdk21'; Remove-Item 'jdk21.zip'"
  for /d %%D in ("%CD%\jdk21\*") do if exist "%%~D\bin\java.exe" set "JBR=%%~D"
)
if not defined JBR (
  echo FAILED: no Java 17-21 available and download failed>build_status.txt
  echo Could not get Java 17-21. & goto :end
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
echo BUILT>build_status.txt
echo.
echo Built: %~dp0..\DrivePriceWatch.apk
echo.

rem ---- Install on the phone over USB ----
set "ADB=%SDK%\platform-tools\adb.exe"
if not exist "%ADB%" (
  echo BUILT, NOT INSTALLED: adb not found in the Android SDK platform-tools>build_status.txt
  echo adb not found - install "Android SDK Platform-Tools" in Android Studio's SDK Manager. & goto :end
)
"%ADB%" start-server >nul 2>&1
echo Waiting for your phone... unlock it and tap "Allow" on the USB debugging prompt if it appears.
"%ADB%" wait-for-usb-device 2>nul
"%ADB%" devices > adb_devices.txt 2>&1
"%ADB%" -d install -r "..\DrivePriceWatch.apk" > install_log.txt 2>&1
findstr /c:"Success" install_log.txt >nul
if errorlevel 1 (
  echo BUILT, INSTALL FAILED: see install_log.txt>build_status.txt
  echo Install failed - see install_log.txt & goto :end
)
"%ADB%" -d shell am start -n com.drivepricewatch/.MainActivity >nul 2>&1
echo OK: built and installed>build_status.txt
echo Installed and opened Drive Price Watch on your phone.
:end
echo.
pause
endlocal
