param([Parameter(Mandatory=$true)][string]$BackupPath,[Parameter(Mandatory=$true)][string]$NewDatabase,[switch]$DatabaseAlreadyCreated,[string]$PostgresBin='C:/Program Files/PostgreSQL/18/bin')
$ErrorActionPreference='Stop'
if($NewDatabase -notmatch '^restauracion_[a-z0-9_]+$'){throw 'Usa una base nueva con nombre restauracion_ seguido de letras minúsculas o números.'}
$taskRoot=Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'Read-Connection.ps1')
$connection=Read-Connection $taskRoot
$previousPassword=$env:PGPASSWORD
try{$env:PGPASSWORD=$connection.Password
    if(!$DatabaseAlreadyCreated){
        & (Join-Path $PostgresBin 'createdb.exe') -h $connection.Host -p $connection.Port -U $connection.User $NewDatabase
        if($LASTEXITCODE -ne 0){throw 'No se pudo crear la base nueva. Prepara una base vacia con un administrador y utiliza -DatabaseAlreadyCreated. La base original se conserva.'}
    }
    & (Join-Path $PostgresBin 'pg_restore.exe') -h $connection.Host -p $connection.Port -U $connection.User -d $NewDatabase --no-owner --exit-on-error $BackupPath
    if($LASTEXITCODE -ne 0){throw 'Falló restauración en base nueva.'}
}finally{$env:PGPASSWORD=$previousPassword}
Write-Host "Restauración en $NewDatabase. La base original se conservó."
