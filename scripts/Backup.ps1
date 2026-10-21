param([string]$OutputPath, [string]$PostgresBin='C:/Program Files/PostgreSQL/18/bin')
$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'Read-Connection.ps1')
$connection=Read-Connection $taskRoot
if(!$OutputPath){$OutputPath=Join-Path $taskRoot ('.local/backup-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'.dump')}
$previousPassword=$env:PGPASSWORD
try{$env:PGPASSWORD=$connection.Password
    & (Join-Path $PostgresBin 'pg_dump.exe') -h $connection.Host -p $connection.Port -U $connection.User -d $connection.Database -Fc -f $OutputPath
    if($LASTEXITCODE -ne 0){throw 'Falló el respaldo.'}
}finally{$env:PGPASSWORD=$previousPassword}
Write-Host "Respaldo guardado: $OutputPath"
