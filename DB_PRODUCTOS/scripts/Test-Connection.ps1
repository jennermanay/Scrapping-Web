param([string]$PostgresBin='C:/Program Files/PostgreSQL/18/bin')
$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'Read-Connection.ps1')
$connection=Read-Connection $taskRoot
$previousPassword=$env:PGPASSWORD
try{$env:PGPASSWORD=$connection.Password
    & (Join-Path $PostgresBin 'psql.exe') -h $connection.Host -p $connection.Port -U $connection.User -d $connection.Database -v ON_ERROR_STOP=1 -c 'SELECT current_database() AS base, current_user AS usuario, version();'
    if($LASTEXITCODE -ne 0){throw 'No se pudo conectar a PostgreSQL. Revisa config/database.properties.'}
}finally{$env:PGPASSWORD=$previousPassword}
