param([switch]$SkipDatabase)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $taskRoot
if(!(Test-Path -LiteralPath (Join-Path $taskRoot 'config/database.properties')) -and !$env:MONITOREO_CONFIG){throw 'Copia config/database.example.properties como config/database.properties y configura tu servidor PostgreSQL.'}
if(Test-Path -LiteralPath 'C:/Program Files/Java/jdk-21.0.12.1'){ $env:JAVA_HOME='C:/Program Files/Java/jdk-21.0.12.1' }
if(!$env:JAVA_HOME){throw 'Configura JAVA_HOME con un JDK 21 de 64 bits.'}
$env:Path="$env:JAVA_HOME/bin;$env:Path"
& mvn.cmd -B -ntp javafx:run
if($LASTEXITCODE -ne 0){throw 'Falló el inicio de JavaFX.'}
