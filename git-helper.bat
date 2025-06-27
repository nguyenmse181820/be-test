@echo off
setlocal

echo ===================================
echo Boeing Backend Git Helper
echo ===================================

echo Available commands:
echo 1. Add all changes
echo 2. Commit changes
echo 3. Push to remote repository
echo 4. Pull from remote repository
echo 5. Check status
echo 6. Exit
echo.

:menu
set /p choice=Enter your choice (1-6): 

if "%choice%"=="1" (
    echo Adding all changes...
    git add .
    echo Done.
    echo.
    goto menu
)

if "%choice%"=="2" (
    set /p message=Enter commit message: 
    echo Committing changes...
    git commit -m "%message%"
    echo Done.
    echo.
    goto menu
)

if "%choice%"=="3" (
    echo Pushing to remote repository...
    git push
    echo Done.
    echo.
    goto menu
)

if "%choice%"=="4" (
    echo Pulling from remote repository...
    git pull
    echo Done.
    echo.
    goto menu
)

if "%choice%"=="5" (
    echo Current git status:
    git status
    echo.
    goto menu
)

if "%choice%"=="6" (
    echo Exiting...
    exit /b 0
)

echo Invalid choice. Please try again.
goto menu
