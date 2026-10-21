$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$databaseDir=Join-Path $taskRoot 'database'
New-Item -ItemType Directory -Force -Path $databaseDir | Out-Null
$base=[IO.File]::ReadAllText((Join-Path $taskRoot 'src/main/resources/schema.sql'))
$migration=[IO.File]::ReadAllText((Join-Path $taskRoot 'src/main/resources/migration-v2.sql'))
$third=[IO.File]::ReadAllText((Join-Path $taskRoot 'src/main/resources/migration-v3.sql'))
[IO.File]::WriteAllText((Join-Path $databaseDir 'schema_monitoreo_precios_final.sql'),"-- Esquema consolidado; regenerar con scripts/Sync-Schema.ps1`nBEGIN;`n$base`n$migration`n$third`nCOMMIT;`n")
