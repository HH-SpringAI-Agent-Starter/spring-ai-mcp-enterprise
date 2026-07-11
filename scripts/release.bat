@echo off
REM ===========================================
REM MCP Enterprise — Maven Central 发布脚本 (Windows)
REM ===========================================
REM
REM 使用方法：
REM   scripts\release.bat           交互式发布
REM   scripts\release.bat patch     自动 patch 版本发布
REM   scripts\release.bat minor     自动 minor 版本发布
REM   scripts\release.bat 1.0.0     直接指定版本
REM
REM 前提条件：
REM   1. 设置环境变量（或直接修改下面脚本中的值）
REM      - SONATYPE_USERNAME
REM      - SONATYPE_PASSWORD
REM      - GPG_KEY_NAME
REM      - GPG_PASSPHRASE
REM   2. 注册 https://central.sonatype.com/
REM   3. 创建命名空间 com.mcp.enterprise
REM   4. 生成 GPG 密钥并上传 keyserver
REM ===========================================

@echo off
setlocal enabledelayedexpansion

echo ========================================
echo   MCP Enterprise - Maven Central 发布
echo ========================================
echo.

REM ---- 检查环境变量 ----
if "%SONATYPE_USERNAME%"=="" (
    echo [错误] 缺少环境变量: SONATYPE_USERNAME
    goto :need_env
)
if "%SONATYPE_PASSWORD%"=="" (
    echo [错误] 缺少环境变量: SONATYPE_PASSWORD
    goto :need_env
)
if "%GPG_KEY_NAME%"=="" (
    echo [错误] 缺少环境变量: GPG_KEY_NAME
    goto :need_env
)
if "%GPG_PASSPHRASE%"=="" (
    echo [错误] 缺少环境变量: GPG_PASSPHRASE
    goto :need_env
)
echo [OK] 环境变量检查通过

REM ---- 检查 Git 工作区 ----
for /f %%i in ('git status --porcelain') do set dirty=1
if defined dirty (
    echo [错误] 工作区有未提交的变更，请先提交或 stash
    git status --short
    exit /b 1
)
echo [OK] Git 工作区干净

REM ---- 获取当前版本 ----
for /f "tokens=2 delims=<>" %%v in ('findstr "<version>" pom.xml ^| findstr /v "<?xml" ^| findstr /v "<version>0.0"') do set VERSION=%%v
set VERSION=%VERSION:-SNAPSHOT=%
echo 当前版本: %VERSION%

REM ---- 解析版本号并递增 ----
for /f "tokens=1,2,3 delims=." %%a in ("%VERSION%") do (
    set MAJOR=%%a
    set MINOR=%%b
    set PATCH=%%c
)

if "%1"=="patch" (
    set /a PATCH+=1
    set NEW_VERSION=!MAJOR!.!MINOR!.!PATCH!
) else if "%1"=="minor" (
    set /a MINOR+=1
    set PATCH=0
    set NEW_VERSION=!MAJOR!.!MINOR!.!PATCH!
) else if "%1"=="major" (
    set /a MAJOR+=1
    set MINOR=0
    set PATCH=0
    set NEW_VERSION=!MAJOR!.!MINOR!.!PATCH!
) else if not "%1"=="" (
    set NEW_VERSION=%1
) else (
    set /a PATCH+=1
    set NEW_VERSION=!MAJOR!.!MINOR!.!PATCH!
    echo.
    echo 使用默认版本: !NEW_VERSION!
    echo 按回车继续或 Ctrl+C 取消
    pause >nul
)

set TAG=v%NEW_VERSION%
echo.
echo [发布] 版本: %NEW_VERSION% (标签: %TAG%)
echo.
timeout /t 3 >nul

echo.
echo [1/5] 更新版本号
call mvn versions:set -DnewVersion="%NEW_VERSION%" -q
call git add pom.xml */pom.xml
call git commit -m "Release v%NEW_VERSION%: 版本号更新"

echo.
echo [2/5] 创建 Git 标签
call git tag -a "v%NEW_VERSION%" -m "Release v%NEW_VERSION%"

echo.
echo [3/5] 编译并测试
call mvn clean test -q
if %ERRORLEVEL% neq 0 (
    echo [错误] 测试失败，终止发布
    exit /b 1
)

echo.
echo [4/5] 发布到 Maven Central
call mvn clean deploy -P release -DskipTests -Dgpg.keyname="%GPG_KEY_NAME%" -Dgpg.passphrase="%GPG_PASSPHRASE%"
if %ERRORLEVEL% neq 0 (
    echo [错误] Maven Central 发布失败
    exit /b 1
)

echo.
echo [5/5] 推送到 GitHub
call git push origin main --tags

REM ---- 切换到下一个 SNAPSHOT 版本 ----
set /a NEXT_PATCH=PATCH+1
set NEXT_VERSION=%MAJOR%.%MINOR%.%NEXT_PATCH%
echo.
echo [后续] 切换到 %NEXT_VERSION%-SNAPSHOT
call mvn versions:set -DnewVersion="%NEXT_VERSION%-SNAPSHOT" -q
call git add pom.xml */pom.xml
call git commit -m "Prepare for next development iteration: %NEXT_VERSION%-SNAPSHOT"
call git push origin main

echo.
echo ========================================
echo  发布完成: v%NEW_VERSION%
echo ========================================
echo   GitHub: https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise/releases/tag/v%NEW_VERSION%
echo   Maven Central: https://central.sonatype.com/artifact/com.mcp.enterprise/mcp-core/%NEW_VERSION%
echo ========================================

endlocal
exit /b 0

:need_env
echo.
echo 请设置以下环境变量:
echo   set SONATYPE_USERNAME=^<Sonatype 用户名^>
echo   set SONATYPE_PASSWORD=^<Sonatype 密码/Token^>
echo   set GPG_KEY_NAME=^<GPG 密钥名称^>
echo   set GPG_PASSPHRASE=^<GPG 密码^>
endlocal
exit /b 1
