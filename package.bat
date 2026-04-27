@echo off
REM RQ Tracker packaging script
REM Requires JDK 21.
REM MSI packaging requires WiX Toolset 3.x.

setlocal

REM Auto-detect Scoop JDK 21.
if exist "%USERPROFILE%\scoop\apps\openjdk21\current\bin\java.exe" set "JAVA_HOME=%USERPROFILE%\scoop\apps\openjdk21\current"

REM Auto-detect Scoop Maven.
if exist "%USERPROFILE%\scoop\apps\maven\current\bin\mvn.cmd" set "PATH=%USERPROFILE%\scoop\apps\maven\current\bin;%JAVA_HOME%\bin;%PATH%"

echo [1/3] Compile...
call mvn compile -q
if errorlevel 1 ( echo Compile failed & exit /b 1 )

echo [2/3] jlink runtime image...
call mvn javafx:jlink
if errorlevel 1 ( echo jlink failed & exit /b 1 )

echo [3/3] jpackage installer...
call mvn exec:exec@jpackage
if errorlevel 1 ( echo jpackage failed & exit /b 1 )

echo.
echo Package complete. Output directory: target\installer\
echo.
if exist "target\installer\RQTracker-1.1.12.msi" (
    echo Installer: target\installer\RQTracker-1.1.12.msi
) else if exist "target\installer\RQTracker\RQTracker.exe" (
    echo Executable: target\installer\RQTracker\RQTracker.exe
)

endlocal
