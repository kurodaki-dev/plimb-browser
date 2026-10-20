@echo off
REM ============================================================
REM  Plimb Browser
REM  Distribution Firefox portable : aucune installation,
REM  toutes les donnees restent dans le dossier "profile".
REM ============================================================
setlocal
set "HERE=%~dp0"
start "" "%HERE%firefox.exe" -profile "%HERE%profile" -no-remote
