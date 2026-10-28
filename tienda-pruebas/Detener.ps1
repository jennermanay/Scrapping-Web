$ErrorActionPreference='Stop'
$shopRoot=$PSScriptRoot
$projectRoot=Split-Path -Parent $shopRoot
$statePath=Join-Path $projectRoot '.local/tienda-process.json'
if(!(Test-Path -LiteralPath $statePath)){Write-Output 'No hay un proceso de tienda registrado.';exit 0}
$state=Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
$expectedServer=[IO.Path]::GetFullPath((Join-Path $shopRoot 'server.mjs'))
if($state.Server -ne $expectedServer){throw 'El registro no corresponde a esta tienda.'}
$running=Get-CimInstance Win32_Process -Filter "ProcessId=$([int]$state.Id)"
if($running){
 if($running.Name -notin @('node.exe','node') -or !$running.CommandLine.Contains($expectedServer)){throw 'El proceso registrado no corresponde al servidor de esta tienda.'}
 Stop-Process -Id ([int]$state.Id)
}
Remove-Item -LiteralPath $statePath
Write-Output 'Tienda detenida. Los productos se conservaron.'
