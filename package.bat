@echo off
REM RQ Tracker 打包腳本
REM 執行前請確認 JAVA_HOME 指向 JDK 21
REM MSI 格式需先安裝 WiX Toolset 3.x：https://wixtoolset.org/

setlocal

REM 自動偵測 Scoop 安裝的 JDK 21
if exist "%USERPROFILE%\scoop\apps\openjdk21\current\bin\java.exe" (
    set JAVA_HOME=%USERPROFILE%\scoop\apps\openjdk21\current
)

REM 自動偵測 Scoop 安裝的 Maven
if exist "%USERPROFILE%\scoop\apps\maven\current\bin\mvn.cmd" (
    set PATH=%USERPROFILE%\scoop\apps\maven\current\bin;%JAVA_HOME%\bin;%PATH%
)

echo [1/3] 編譯...
call mvn compile -q
if errorlevel 1 ( echo 編譯失敗 & exit /b 1 )

echo [2/3] jlink 建立 Runtime Image...
call mvn javafx:jlink
if errorlevel 1 ( echo jlink 失敗 & exit /b 1 )

echo [3/3] jpackage 打包...
call mvn exec:exec@jpackage
if errorlevel 1 ( echo jpackage 失敗 & exit /b 1 )

echo.
echo 打包完成！輸出目錄：target\installer\
echo.
if exist "target\installer\RQTracker-1.0.0.msi" (
    echo 安裝檔：target\installer\RQTracker-1.0.0.msi
) else if exist "target\installer\RQTracker\RQTracker.exe" (
    echo 可執行檔：target\installer\RQTracker\RQTracker.exe
)

endlocal
