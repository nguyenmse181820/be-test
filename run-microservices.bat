@echo off
setlocal enabledelayedexpansion

REM Create a temporary PowerShell script for the health check UI
echo $host.UI.RawUI.WindowTitle = 'Boeing Microservices Health Monitor' > "%TEMP%\health-check.ps1"
echo $host.UI.RawUI.BackgroundColor = 'Black' >> "%TEMP%\health-check.ps1"
echo $host.UI.RawUI.ForegroundColor = 'White' >> "%TEMP%\health-check.ps1"
echo cls >> "%TEMP%\health-check.ps1"
echo function Show-HealthStatus { >> "%TEMP%\health-check.ps1"
echo     param([string]$service, [string]$port, [string]$status) >> "%TEMP%\health-check.ps1"
echo     $color = if ($status -eq 'UP') { 'Green' } else { 'Red' } >> "%TEMP%\health-check.ps1"
echo     Write-Host ('{0,-20} {1,-10} [' -f $service, $port) -NoNewline >> "%TEMP%\health-check.ps1"
echo     Write-Host $status -ForegroundColor $color -NoNewline >> "%TEMP%\health-check.ps1"
echo     Write-Host ']' >> "%TEMP%\health-check.ps1"
echo } >> "%TEMP%\health-check.ps1"
echo function Check-ServiceHealth { >> "%TEMP%\health-check.ps1"
echo     param([string]$port) >> "%TEMP%\health-check.ps1"
echo     try { >> "%TEMP%\health-check.ps1"
echo         $response = Invoke-WebRequest -Uri "http://localhost:$port/actuator/health" -UseBasicParsing -TimeoutSec 1 >> "%TEMP%\health-check.ps1"
echo         return ($response.Content -match '"status":"UP"') >> "%TEMP%\health-check.ps1"
echo     } catch { return $false } >> "%TEMP%\health-check.ps1"
echo } >> "%TEMP%\health-check.ps1"
echo while ($true) { >> "%TEMP%\health-check.ps1"
echo     cls >> "%TEMP%\health-check.ps1"
echo     Write-Host '===============================================' -ForegroundColor Cyan >> "%TEMP%\health-check.ps1"
echo     Write-Host '        Boeing Microservices Health Monitor    ' -ForegroundColor Cyan >> "%TEMP%\health-check.ps1"
echo     Write-Host '===============================================' -ForegroundColor Cyan >> "%TEMP%\health-check.ps1"
echo     Write-Host '' >> "%TEMP%\health-check.ps1"
echo     $services = @( >> "%TEMP%\health-check.ps1"
echo         @{Name='Config Server'; Port='8888'}, >> "%TEMP%\health-check.ps1"
echo         @{Name='Discovery Service'; Port='8761'}, >> "%TEMP%\health-check.ps1"
echo         @{Name='Gateway'; Port='8080'}, >> "%TEMP%\health-check.ps1"
echo         @{Name='User Service'; Port='8081'}, >> "%TEMP%\health-check.ps1"
echo         @{Name='Flight Service'; Port='8082'}, >> "%TEMP%\health-check.ps1"
echo         @{Name='Aircraft Service'; Port='8083'}, >> "%TEMP%\health-check.ps1"
echo         @{Name='Booking Service'; Port='8084'}, >> "%TEMP%\health-check.ps1"
echo         @{Name='Check-in Service'; Port='8085'}, >> "%TEMP%\health-check.ps1"
echo         @{Name='Loyalty Service'; Port='8086'} >> "%TEMP%\health-check.ps1"
echo     ) >> "%TEMP%\health-check.ps1"
echo     foreach ($service in $services) { >> "%TEMP%\health-check.ps1"
echo         $status = if (Check-ServiceHealth $service.Port) { 'UP' } else { 'DOWN' } >> "%TEMP%\health-check.ps1"
echo         Show-HealthStatus $service.Name $service.Port $status >> "%TEMP%\health-check.ps1"
echo     } >> "%TEMP%\health-check.ps1"
echo     Write-Host '' >> "%TEMP%\health-check.ps1"
echo     Write-Host 'Press Ctrl+C to exit the health monitor' -ForegroundColor Yellow >> "%TEMP%\health-check.ps1"
echo     Start-Sleep -Seconds 2 >> "%TEMP%\health-check.ps1"
echo } >> "%TEMP%\health-check.ps1"

echo ===================================
echo Boeing Backend Microservices Startup
echo ===================================

REM Start Docker containers for databases
echo Starting database containers...
docker-compose down -v
docker-compose --profile init up -d
if errorlevel 1 (
    echo Failed to start Docker containers. Make sure Docker Desktop is running.
    pause
    exit /b 1
)

echo Waiting for databases to initialize (10 seconds)...
timeout /t 10 /nobreak >nul

set BASE_DIR=%cd%\services

REM Define all services with their port numbers
set SERVICES=config-server:8888 discovery:8761 gateway:8080 user-service:8081 flight-service:8082 aircraft-service:8083 booking-service:8084 check-in-service:8085 loyalty-service:8086

REM Start services in the correct order (config server, discovery, gateway, then other services)
echo.
echo Starting Config Server...
rmdir /s /q "%BASE_DIR%\config-server\target" 2>nul
start /B cmd /C "cd %BASE_DIR%\config-server && mvn spring-boot:run"
echo Waiting for Config Server to start (port 8888)...
:wait_for_config
timeout /t 2 /nobreak >nul
netstat -an | findstr "8888" >nul
if errorlevel 1 goto wait_for_config
echo Config Server started successfully.

echo.
echo Starting Discovery Service...
rmdir /s /q "%BASE_DIR%\discovery\target" 2>nul
start /B cmd /C "cd %BASE_DIR%\discovery && mvn spring-boot:run"
echo Waiting for Discovery Service to start (port 8761)...
:wait_for_discovery
timeout /t 2 /nobreak >nul
netstat -an | findstr "8761" >nul
if errorlevel 1 goto wait_for_discovery
echo Discovery Service started successfully.

echo.
echo Starting Gateway...
rmdir /s /q "%BASE_DIR%\gateway\target" 2>nul
start /B cmd /C "cd %BASE_DIR%\gateway && mvn spring-boot:run"
echo Waiting for Gateway to start (port 8080)...
:wait_for_gateway
timeout /t 2 /nobreak >nul
netstat -an | findstr "8080" >nul
if errorlevel 1 goto wait_for_gateway
echo Gateway started successfully.

echo.
echo Starting other microservices...
for %%a in (%SERVICES%) do (
    for /f "tokens=1,2 delims=:" %%s in ("%%a") do (
        REM Skip the already started services
        if not "%%s"=="config-server" if not "%%s"=="discovery" if not "%%s"=="gateway" (
            echo Starting %%s on port %%t...
            rmdir /s /q "%BASE_DIR%\%%s\target" 2>nul
            start /B cmd /C "cd %BASE_DIR%\%%s && mvn spring-boot:run"
            timeout /t 2 /nobreak >nul
        )
    )
)

echo.
echo All microservices have been started!
echo.
echo Starting Health Monitor UI...
echo Press Ctrl+C in the Health Monitor window to stop all services
echo.

REM Start the health monitor in a new window
start powershell -NoExit -ExecutionPolicy Bypass -File "%TEMP%\health-check.ps1"

REM Wait for user to press a key to stop services
pause >nul

REM Stop all Java processes (microservices)
echo Stopping all microservices...
taskkill /F /IM java.exe /T > nul 2>&1

REM Stop Docker containers
echo Stopping database containers...
docker-compose down

REM Clean up temporary files
del "%TEMP%\health-check.ps1" > nul 2>&1

echo All services and containers stopped.
