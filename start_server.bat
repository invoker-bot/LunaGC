@echo off
rem ---------------------------------------------------------------------------
rem  LunaGC 7.1.0 -- double-click launcher for `task serve`.
rem
rem  This is now a thin wrapper: the real logic lives in tools/serve.ps1, which
rem  is what `task serve` runs. Keeping one implementation means the docker,
rem  jar-name and port handling here cannot drift out of sync with the Taskfile.
rem
rem  `task serve` starts the luna-mongo container if it is stopped, launches the
rem  server detached in its own console window, and waits for the dispatch port
rem  to come up before reporting success. If you want the equivalent of the old
rem  inline behaviour, it is preserved commented out below.
rem
rem  Requires: `task` on PATH (winget: Task.Task) and the luna-mongo container to
rem  exist. Create it once with:
rem    docker run -d --name luna-mongo -p 27017:27017 mongo
rem ---------------------------------------------------------------------------
cd /d "%~dp0"

call task serve
echo.
echo ------------------------------------------------------------------
echo task serve finished. The server runs in its own console window and
echo survives this one closing. Logs: start_stdout.log / start_stderr.log
echo Status: task serve:status    Stop: task serve:stop
echo ------------------------------------------------------------------
pause

rem ---------------------------------------------------------------------------
rem  Previous inline implementation, kept for reference. It started the
rem  container and java directly in this window, so the java output was
rem  visible here -- but it could not detect a missing container, and it
rem  duplicated what tools/serve.ps1 already does.
rem
rem  echo Starting MongoDB container (if stopped)...
rem  docker start luna-mongo >nul 2>&1
rem
rem  echo Starting LunaGC (HTTP dispatch on 0.0.0.0:8088, game server on 127.0.0.1:22101)...
rem  echo Log files: start_stdout.log / start_stderr.log  (overwritten each launch)
rem  echo.
rem  java -jar LunaGC-7.1.0.jar > start_stdout.log 2> start_stderr.log
rem  echo.
rem  echo LunaGC exited with code %ERRORLEVEL% -- see start_stderr.log
rem  pause
rem ---------------------------------------------------------------------------
