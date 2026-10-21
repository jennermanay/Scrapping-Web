param([switch]$Live,[switch]$Ui,[switch]$LocalDatabase)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $taskRoot
if($LocalDatabase){& (Join-Path $PSScriptRoot 'Start-Database.ps1');if(!$?){throw 'No se pudo iniciar la base local opcional.'}}
. (Join-Path $PSScriptRoot 'Read-Connection.ps1')
$connection=Read-Connection $taskRoot
$env:INTEGRATION_DB_URL=$connection.Url
$env:INTEGRATION_DB_USER=$connection.User
$env:INTEGRATION_DB_PASSWORD=$connection.Password
$env:LIVE_TESTS=if($Live){'true'}else{'false'}
$env:UI_TESTS=if($Ui){'true'}else{'false'}
if(Test-Path -LiteralPath 'C:/Program Files/Java/jdk-21.0.12.1'){$env:JAVA_HOME='C:/Program Files/Java/jdk-21.0.12.1'}
$env:Path="$env:JAVA_HOME/bin;$env:Path"
& mvn.cmd -B -ntp test
if($LASTEXITCODE -ne 0){throw 'Fallaron las pruebas.'}
