param([int]$Port = 55439, [string]$PostgresBin = 'C:/Program Files/PostgreSQL/18/bin')
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$localDir = Join-Path $taskRoot '.local'
$dataDir = Join-Path $localDir 'postgres'
$configFile = Join-Path $localDir 'database.properties'
$ownerFile = Join-Path $localDir 'owner.password'
if (!(Test-Path -LiteralPath (Join-Path $PostgresBin 'initdb.exe'))) { throw 'No se encontró PostgreSQL. Indica -PostgresBin con su carpeta bin.' }
New-Item -ItemType Directory -Force -Path $localDir | Out-Null
if (!(Test-Path -LiteralPath (Join-Path $dataDir 'PG_VERSION'))) {
    if (Test-Path -LiteralPath $configFile) { throw 'Ya existe configuración local sin clúster. Revisa .local antes de continuar.' }
    function New-LocalPassword {
        $bytes = New-Object byte[] 24
        $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
        return [BitConverter]::ToString($bytes).Replace('-', '')
    }
    $ownerPassword = New-LocalPassword
    $appPassword = New-LocalPassword
    [IO.File]::WriteAllText($ownerFile, $ownerPassword)
    & (Join-Path $PostgresBin 'initdb.exe') -D $dataDir -U local_owner --encoding=UTF8 --locale=C --auth-host=scram-sha-256 --auth-local=scram-sha-256 "--pwfile=$ownerFile"
    if ($LASTEXITCODE -ne 0) { throw 'Falló initdb.' }
    [IO.File]::WriteAllText($configFile, "DB_URL=jdbc:postgresql://127.0.0.1:$Port/monitoreo_precios`nDB_USER=monitoreo`nDB_PASSWORD=$appPassword`nPG_PORT=$Port`n")
}
$values = ConvertFrom-StringData ([IO.File]::ReadAllText($configFile))
$Port = [int]$values.PG_PORT
& (Join-Path $PostgresBin 'pg_ctl.exe') -D $dataDir status
if ($LASTEXITCODE -ne 0) {
    & (Join-Path $PostgresBin 'pg_ctl.exe') -D $dataDir -l (Join-Path $localDir 'postgres.log') -o "-p $Port -h 127.0.0.1" -w start
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo iniciar PostgreSQL. Revisa .local/postgres.log y el puerto.' }
}
$previousPassword = $env:PGPASSWORD
try {
    $env:PGPASSWORD = [IO.File]::ReadAllText($ownerFile)
    $exists = & (Join-Path $PostgresBin 'psql.exe') -h 127.0.0.1 -p $Port -U local_owner -d postgres -Atc "SELECT count(*) FROM pg_roles WHERE rolname='monitoreo'"
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo conectar al clúster local.' }
    if ($exists -eq '0') {
        # Password is generated locally as hexadecimal, never interpolated from arbitrary input.
        $generatedPassword = $values.DB_PASSWORD
        & (Join-Path $PostgresBin 'psql.exe') -h 127.0.0.1 -p $Port -U local_owner -d postgres -v ON_ERROR_STOP=1 -c "CREATE ROLE monitoreo LOGIN PASSWORD '$generatedPassword'"
        if ($LASTEXITCODE -ne 0) { throw 'Falló la creación del usuario.' }
    }
    $databaseExists = & (Join-Path $PostgresBin 'psql.exe') -h 127.0.0.1 -p $Port -U local_owner -d postgres -Atc "SELECT count(*) FROM pg_database WHERE datname='monitoreo_precios'"
    if ($databaseExists -eq '0') {
        & (Join-Path $PostgresBin 'createdb.exe') -h 127.0.0.1 -p $Port -U local_owner -O monitoreo -E UTF8 monitoreo_precios
        if ($LASTEXITCODE -ne 0) { throw 'Falló la creación de la base.' }
    }
} finally { $env:PGPASSWORD = $previousPassword }
Write-Host "Base local lista en 127.0.0.1:$Port. Credenciales privadas en .local/database.properties."
