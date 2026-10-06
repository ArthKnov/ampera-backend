# Instala PostgreSQL 16 (binarios portateis, sem administrador) e Mosquitto (winget).

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
$pg = Join-Path $env:LOCALAPPDATA "ampera-postgres"

if (-not (Test-Path "C:\Program Files\mosquitto\mosquitto.exe")) {
    winget install --id EclipseFoundation.Mosquitto -e --silent --accept-source-agreements --accept-package-agreements
}

if (-not (Test-Path "$pg\pgsql\bin\pg_ctl.exe")) {
    New-Item -ItemType Directory -Force $pg | Out-Null
    $zip = Join-Path $env:TEMP "postgresql-16.zip"
    Invoke-WebRequest "https://get.enterprisedb.com/postgresql/postgresql-16.10-1-windows-x64-binaries.zip" -OutFile $zip -UseBasicParsing
    Expand-Archive $zip -DestinationPath $pg -Force
    Remove-Item $zip
}

if (-not (Test-Path "$pg\data\PG_VERSION")) {
    $senha = Join-Path $env:TEMP "ampera-pw.txt"
    Set-Content $senha "ampera" -NoNewline
    & "$pg\pgsql\bin\initdb.exe" -D "$pg\data" -U ampera --pwfile="$senha" -A scram-sha-256 -E UTF8 --locale=C
    Remove-Item $senha
    & "$pg\pgsql\bin\pg_ctl.exe" -D "$pg\data" -l "$pg\postgres.log" -o "-p 5432" start
    Start-Sleep 3
    $env:PGPASSWORD = "ampera"
    & "$pg\pgsql\bin\psql.exe" -h localhost -U ampera -d postgres -c "CREATE DATABASE ampera;"
}

Write-Host "Pronto. Use scripts\subir-local.ps1 para iniciar banco e broker."
