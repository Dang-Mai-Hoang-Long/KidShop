@echo off
:loop
echo %time% >> keepalive.log
timeout /t 60 > nul
goto loop