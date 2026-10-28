param([int]$Puerto=8877)
$ErrorActionPreference='Stop'
if($Puerto -lt 1 -or $Puerto -gt 65535){throw 'Puerto inválido.'}
$shopRoot=$PSScriptRoot
$projectRoot=Split-Path -Parent $shopRoot
$serverPath=Join-Path $shopRoot 'server.mjs'
$address="http://127.0.0.1:$Puerto"
try {
 $existing=Invoke-WebRequest -Uri "$address/admin" -UseBasicParsing -TimeoutSec 2
 if($existing.Content -match 'Tienda de pruebas'){Write-Output "La tienda ya está disponible: $address";exit 0}
 throw 'El puerto está ocupado por otra aplicación. Usa otro con -Puerto.'
} catch {
 if($_.Exception.Message -match 'El puerto está ocupado'){throw}
}
$nodeCommand=Get-Command node.exe -ErrorAction SilentlyContinue
if(!$nodeCommand){throw 'Se requiere Node.js 18 o posterior para iniciar la tienda.'}
$logRoot=Join-Path $projectRoot '.local'
New-Item -ItemType Directory -Force -Path $logRoot | Out-Null
$previousPort=$env:TIENDA_PORT
try {
 $env:TIENDA_PORT=[string]$Puerto
 $process=Start-Process -FilePath $nodeCommand.Source -ArgumentList @(('"'+$serverPath+'"')) -WorkingDirectory $shopRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logRoot 'tienda-out.log') -RedirectStandardError (Join-Path $logRoot 'tienda-error.log') -PassThru
 @{Id=$process.Id;Port=$Puerto;Server=$serverPath} | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $logRoot 'tienda-process.json')
} finally {$env:TIENDA_PORT=$previousPort}
$ready=$false
for($attempt=0;$attempt -lt 20;$attempt++){
 try {$response=Invoke-WebRequest -Uri "$address/admin" -UseBasicParsing -TimeoutSec 1;if($response.Content -match 'Tienda de pruebas'){$ready=$true;break}}catch{}
 if($process.HasExited){break}
 Start-Sleep -Milliseconds 200
}
if(!$ready){throw 'No se pudo iniciar la tienda. Consulta .local/tienda-error.log.'}
Write-Output "Tienda: $address"
Write-Output "Administrar productos: $address/admin"
