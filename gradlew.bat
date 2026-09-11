@rem
@rem UploadGo build launcher (Windows).
@rem
@rem Uses the checked-in gradle-wrapper.jar when present; otherwise downloads
@rem the Gradle distribution named in gradle\wrapper\gradle-wrapper.properties.
@rem

@if "%DEBUG%"=="" @echo off
setlocal EnableDelayedExpansion

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_HOME=%DIRNAME%

set WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
set PROPERTIES_FILE=%APP_HOME%gradle\wrapper\gradle-wrapper.properties

rem ---------------------------------------------------------------------------
rem Path 1: standard wrapper jar is present.
rem ---------------------------------------------------------------------------
if exist "%WRAPPER_JAR%" goto runWrapper

rem ---------------------------------------------------------------------------
rem Path 2: download and run the Gradle distribution directly via PowerShell.
rem ---------------------------------------------------------------------------
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$props = Get-Content '%PROPERTIES_FILE%' | Where-Object { $_ -match '^distributionUrl=' } | Select-Object -First 1;" ^
  "$url = ($props -replace '^distributionUrl=', '') -replace '\\\\','';" ^
  "$version = [System.IO.Path]::GetFileName($url) -replace '^gradle-(.*)-(bin|all)\.zip$','$1';" ^
  "$ghome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' };" ^
  "$distDir = Join-Path $ghome ('wrapper\dists\uploadgo-bootstrap\' + $version);" ^
  "$bin = Join-Path $distDir ('gradle-' + $version + '\bin\gradle.bat');" ^
  "if (-not (Test-Path $bin)) {" ^
  "  New-Item -ItemType Directory -Force -Path $distDir | Out-Null;" ^
  "  $zip = Join-Path $distDir 'gradle.zip';" ^
  "  Write-Host ('gradlew: downloading Gradle ' + $version);" ^
  "  [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12;" ^
  "  Invoke-WebRequest -Uri $url -OutFile $zip;" ^
  "  Expand-Archive -Path $zip -DestinationPath $distDir -Force;" ^
  "};" ^
  "& $bin @args"
exit /b %ERRORLEVEL%

:runWrapper
set JAVA_EXE=java.exe
if defined JAVA_HOME set JAVA_EXE=%JAVA_HOME%\bin\java.exe
"%JAVA_EXE%" -Dorg.gradle.appname=gradlew -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%
