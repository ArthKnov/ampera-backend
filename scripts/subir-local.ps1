# Sobe PostgreSQL e Mosquitto nesta maquina sem Docker.
# Com Docker disponivel, `docker compose up -d` faz o mesmo papel.

$ErrorActionPreference = "Stop"
$raiz = Split-Path -Parent $PSScriptRoot
$pg = Join-Path $env:LOCALAPPDATA "ampera-postgres"
$mosquitto = "C:\Program Files\mosquitto\mosquitto.exe"

function Escutando([int] $porta) {
    [bool](Get-NetTCPConnection -LocalPort $porta -State Listen -ErrorAction SilentlyContinue)
}

if (Escutando 5432) {
    Write-Host "PostgreSQL ja esta na porta 5432"
} elseif (Test-Path "$pg\pgsql\bin\pg_ctl.exe") {
    & "$pg\pgsql\bin\pg_ctl.exe" -D "$pg\data" -l "$pg\postgres.log" -o "-p 5432" start | Out-Null
    Write-Host "PostgreSQL iniciado"
} else {
    throw "PostgreSQL nao encontrado em $pg. Rode scripts\instalar-local.ps1"
}

if (Escutando 1883) {
    Write-Host "Broker MQTT ja esta na porta 1883"
} elseif (Test-Path $mosquitto) {
    Start-Process -FilePath $mosquitto -ArgumentList "-c", "`"$raiz\docker\mosquitto\mosquitto.conf`"" -WindowStyle Hidden
    Write-Host "Mosquitto iniciado na porta 1883"
} else {
    throw "Mosquitto nao encontrado. Rode scripts\instalar-local.ps1"
}
