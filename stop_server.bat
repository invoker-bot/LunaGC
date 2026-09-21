@echo off
rem ---------------------------------------------------------------------------
rem  LunaGC 7.0.0 -- double-click launcher for `task serve:stop`.
rem
rem  Thin wrapper around tools/serve.ps1 -Mode stop, which is what the Taskfile
rem  runs. That version finds the java process owning port 8088 and kills it,
rem  and it also understands the -PskipHandbook/-LUNAGC_HTTP_PORT knobs so it
rem  keeps working if the ports move. The old netstat+taskkill body is kept
rem  commented out below.
rem
rem  Only the java process is killed. MongoDB is left running so player data is
rem  not disturbed.
rem ---------------------------------------------------------------------------
cd /d "%~dp0"

call task serve:stop
echo Done.
timeout /t 3 >nul

rem ---------------------------------------------------------------------------
rem  Previous inline implementation, kept for reference. It scanned netstat for
rem  the 8088 listener and taskkilled each owning PID.
rem
rem  for /f "tokens=5" %%a in (
rem    'netstat -ano -p tcp ^| findstr ":8088 .*LISTENING"'
rem  ) do (
rem    echo Stopping LunaGC java process PID %%a
rem    taskkill /F /PID %%a
rem  )
rem  echo Done.
rem  timeout /t 3 >nul
rem ---------------------------------------------------------------------------
