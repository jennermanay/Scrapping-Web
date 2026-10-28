$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$databaseDir=Join-Path $taskRoot 'database'
New-Item -ItemType Directory -Force -Path $databaseDir | Out-Null
$base=[IO.File]::ReadAllText((Join-Path $taskRoot 'src/main/resources/schema.sql'))
$migration=[IO.File]::ReadAllText((Join-Path $taskRoot 'src/main/resources/migration-v2.sql'))
$third=[IO.File]::ReadAllText((Join-Path $taskRoot 'src/main/resources/migration-v3.sql'))
$fourth=[IO.File]::ReadAllText((Join-Path $taskRoot 'src/main/resources/migration-v4.sql'))
$fifth=[IO.File]::ReadAllText((Join-Path $taskRoot 'src/main/resources/migration-v5.sql'))
$sixth=[IO.File]::ReadAllText((Join-Path $taskRoot 'src/main/resources/migration-v6.sql'))
$seventh=[IO.File]::ReadAllText((Join-Path $taskRoot 'src/main/resources/migration-v7.sql'))
$eighth=[IO.File]::ReadAllText((Join-Path $taskRoot 'src/main/resources/migration-v8.sql'))
$ninth=[IO.File]::ReadAllText((Join-Path $taskRoot 'src/main/resources/migration-v9.sql'))
[IO.File]::WriteAllText((Join-Path $databaseDir 'schema_monitoreo_precios_final.sql'),"-- Esquema consolidado; regenerar con scripts/Sync-Schema.ps1`nBEGIN;`n$base`n$migration`n$third`n$fourth`n$fifth`n$sixth`n$seventh`n$eighth`n$ninth`nCOMMIT;`n")
