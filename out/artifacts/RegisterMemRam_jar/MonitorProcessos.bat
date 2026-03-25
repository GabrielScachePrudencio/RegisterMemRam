@echo off
cd /d "%~dp0"

set JAVAFX_LIB="C:\Users\Flavio\Downloads\openjfx-17.0.18_windows-x64_bin-sdk\javafx-sdk-17.0.18\lib"

java --module-path %JAVAFX_LIB% --add-modules javafx.controls,javafx.fxml -jar RegisterMemRam.jar

pause
