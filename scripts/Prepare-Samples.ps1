param([string]$PostgresBin='C:/Program Files/PostgreSQL/18/bin')
$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'Read-Connection.ps1')
$connection=Read-Connection $taskRoot
$previousPassword=$env:PGPASSWORD
try{$env:PGPASSWORD=$connection.Password
    & (Join-Path $PostgresBin 'psql.exe') -h $connection.Host -p $connection.Port -U $connection.User -d $connection.Database -v ON_ERROR_STOP=1 -f (Join-Path $taskRoot 'database/schema_monitoreo_precios_final.sql') -f (Join-Path $taskRoot 'database/datos_prueba.sql')
    if($LASTEXITCODE -ne 0){throw 'Falló la preparación de ejemplos.'}
}finally{$env:PGPASSWORD=$previousPassword}
Write-Host 'Catálogos reales preparados. Crea tu administrador desde el primer inicio de la aplicación.'
