param([string]$PostgresBin='C:/Program Files/PostgreSQL/18/bin')
$taskRoot=Split-Path -Parent $PSScriptRoot
& (Join-Path $PostgresBin 'pg_ctl.exe') -D (Join-Path $taskRoot '.local/postgres') -w stop -m fast
if($LASTEXITCODE -ne 0){throw 'No se pudo detener la base local.'}
