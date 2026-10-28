# Helper compartido por utilidades PostgreSQL. No imprime credenciales.
function Read-Connection {
    param([string]$TaskRoot)
    $path=if($env:MONITOREO_CONFIG){$env:MONITOREO_CONFIG}else{Join-Path $TaskRoot 'config/database.properties'}
    if(!(Test-Path -LiteralPath $path)){throw "Falta el archivo de conexion: $path"}
    $values=ConvertFrom-StringData ([IO.File]::ReadAllText($path))
    if(!$values.DB_URL.StartsWith('jdbc:postgresql://')){throw 'DB_URL debe comenzar con jdbc:postgresql://'}
    $uri=[Uri]$values.DB_URL.Substring(5)
    if(!$uri.Host -or !$uri.AbsolutePath.Trim('/')){throw 'Falta el servidor o la base de datos en DB_URL.'}
    $port=if($uri.Port -gt 0){$uri.Port}else{5432}
    return @{ Host=$uri.Host; Port=$port; Database=[Uri]::UnescapeDataString($uri.AbsolutePath.Trim('/')); User=$values.DB_USER; Password=$values.DB_PASSWORD; Url=$values.DB_URL }
}
