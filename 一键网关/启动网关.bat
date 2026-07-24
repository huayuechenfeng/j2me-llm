@echo off
chcp 65001 >nul
setlocal EnableExtensions DisableDelayedExpansion
title J2ME LLM Portable Gateway

set "BASE=%~dp0"
set "CONFIG=%BASE%gateway.conf"
set "NODE=%BASE%runtime\node.exe"
set "SERVER=%BASE%runtime\server.js"
set "SELFTEST=%BASE%runtime\self-test.js"

if /I "%~1"=="--self-test" goto self_test
if not exist "%CONFIG%" goto create_config
if not exist "%NODE%" goto broken_package
if not exist "%SERVER%" goto broken_package

rem Generate a phone-friendly 12-digit token. Old UUID tokens from the previous BAT are migrated too.
"%NODE%" -e "const fs=require('fs'),c=require('crypto'),p=process.argv[1];let s=fs.readFileSync(p,'utf8'),m=s.match(/^DEVICE_TOKEN=(.*)$/m);if(m){const v=m[1].trim();if(v==='AUTO'||/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(v)){let t='';for(let i=0;i<12;i++)t+=c.randomInt(0,10);s=s.replace(/^DEVICE_TOKEN=.*$/m,'DEVICE_TOKEN='+t);fs.writeFileSync(p,s,'utf8')}}" "%CONFIG%"
if errorlevel 1 goto token_error

set "HOST="
set "PORT="
set "UPSTREAM_URL="
set "UPSTREAM_MODELS_URL="
set "UPSTREAM_API_KEY="
set "UPSTREAM_MODEL="
set "DEVICE_TOKEN="
set "LOG_ERRORS="

for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%CONFIG%") do (
    if /I "%%A"=="HOST" set "HOST=%%B"
    if /I "%%A"=="PORT" set "PORT=%%B"
    if /I "%%A"=="UPSTREAM_URL" set "UPSTREAM_URL=%%B"
    if /I "%%A"=="UPSTREAM_MODELS_URL" set "UPSTREAM_MODELS_URL=%%B"
    if /I "%%A"=="UPSTREAM_API_KEY" set "UPSTREAM_API_KEY=%%B"
    if /I "%%A"=="UPSTREAM_MODEL" set "UPSTREAM_MODEL=%%B"
    if /I "%%A"=="DEVICE_TOKEN" set "DEVICE_TOKEN=%%B"
    if /I "%%A"=="LOG_ERRORS" set "LOG_ERRORS=%%B"
)

if not defined HOST set "HOST=0.0.0.0"
if not defined PORT set "PORT=8787"
if not defined LOG_ERRORS set "LOG_ERRORS=0"
if not defined UPSTREAM_URL goto invalid_config
if not defined UPSTREAM_API_KEY goto invalid_config
if not defined DEVICE_TOKEN goto token_error
findstr /B /I /C:"UPSTREAM_API_KEY=PASTE_YOUR_REAL_API_KEY_HERE" "%CONFIG%" >nul
if not errorlevel 1 goto invalid_config

cls
echo ============================================================
echo             J2ME LLM Portable Gateway
echo ============================================================
echo.
echo Gateway port: %PORT%
echo Phone API Key: %DEVICE_TOKEN%
echo.
set "PHONE_IP="
set "IP_FILE=%TEMP%\j2me-gateway-ip-%RANDOM%-%RANDOM%.txt"
"%NODE%" -e "const d=require('dgram'),s=d.createSocket('udp4');s.connect(53,'1.1.1.1',function(){console.log(s.address().address);s.close()});s.on('error',function(){process.exit(1)})" > "%IP_FILE%" 2>nul
if exist "%IP_FILE%" set /p PHONE_IP=<"%IP_FILE%"
if exist "%IP_FILE%" del /q "%IP_FILE%" >nul 2>&1
if not defined PHONE_IP set "PHONE_IP=YOUR-PC-LAN-IP"
echo Configure the phone with:
echo.
echo   Health: http://%PHONE_IP%:%PORT%/health
echo   Chat:   http://%PHONE_IP%:%PORT%/v1/chat/completions
echo   Models: http://%PHONE_IP%:%PORT%/v1/models
echo   API Key: %DEVICE_TOKEN%
echo.
echo Keep this window open while the phone is using the gateway.
echo If Windows asks about firewall access, allow Private networks only.
echo Close this window or press Ctrl+C to stop.
echo ============================================================
echo.

"%NODE%" "%SERVER%"
set "GATEWAY_EXIT=%ERRORLEVEL%"
echo.
echo Gateway stopped with exit code %GATEWAY_EXIT%.
pause
exit /b %GATEWAY_EXIT%

:create_config
(
    echo # J2ME LLM portable gateway configuration
    echo # Edit values after '='. Do not add quotes. Keep this file private.
    echo HOST=0.0.0.0
    echo PORT=8787
    echo UPSTREAM_URL=https://api.openai.com/v1/chat/completions
    echo UPSTREAM_MODELS_URL=https://api.openai.com/v1/models
    echo UPSTREAM_API_KEY=PASTE_YOUR_REAL_API_KEY_HERE
    echo UPSTREAM_MODEL=
    echo DEVICE_TOKEN=AUTO
    echo LOG_ERRORS=0
) > "%CONFIG%"
echo gateway.conf was created. Fill in the API settings, save it, and run this BAT again.
start "" notepad.exe "%CONFIG%"
pause
exit /b 2

:invalid_config
echo [ERROR] Open gateway.conf and set UPSTREAM_URL and UPSTREAM_API_KEY.
echo Do not add quotes around values.
start "" notepad.exe "%CONFIG%"
pause
exit /b 2

:broken_package
echo [ERROR] The portable runtime is missing or incomplete.
echo Extract the complete ZIP again. Do not move this BAT out of its folder.
pause
exit /b 3

:token_error
echo [ERROR] Could not generate or read the phone token.
echo Set DEVICE_TOKEN=AUTO in gateway.conf and try again.
pause
exit /b 4

:self_test
if not exist "%NODE%" goto broken_package
if not exist "%SELFTEST%" goto broken_package
"%NODE%" "%SELFTEST%"
exit /b %ERRORLEVEL%

