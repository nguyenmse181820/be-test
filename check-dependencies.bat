@echo off
setlocal enabledelayedexpansion

REM Get the service name from command line argument
set SERVICE_NAME=%1

REM Define required dependencies and their ports
set CONFIG_PORT=8888
set DISCOVERY_PORT=8761
set GATEWAY_PORT=8080

echo Checking dependencies for %SERVICE_NAME%...

REM Function to check if a service is up
:check_service
set /a attempts=0
:retry
set /a attempts+=1
timeout /t 2 /nobreak >nul
curl -s http://localhost:%1/actuator/health | findstr "UP" >nul
if errorlevel 1 (
    if !attempts! lss 30 (
        echo Waiting for service on port %1... (Attempt !attempts! of 30)
        goto retry
    ) else (
        echo Service on port %1 is not available after 60 seconds
        exit /b 1
    )
)
echo Service on port %1 is UP
exit /b 0

REM Check Config Server if not the config server itself
if not "%SERVICE_NAME%"=="config-server" (
    call :check_service %CONFIG_PORT%
    if errorlevel 1 exit /b 1
)

REM Check Discovery Service if not the discovery service itself
if not "%SERVICE_NAME%"=="discovery" (
    call :check_service %DISCOVERY_PORT%
    if errorlevel 1 exit /b 1
)

REM Check Gateway if not the gateway itself
if not "%SERVICE_NAME%"=="gateway" (
    call :check_service %GATEWAY_PORT%
    if errorlevel 1 exit /b 1
)

echo All dependencies are available for %SERVICE_NAME%
exit /b 0 