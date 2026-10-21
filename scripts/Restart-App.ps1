$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$windows=Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object {
    $_.CommandLine -and $_.CommandLine.Contains($taskRoot) -and $_.CommandLine.Contains('pe.edu.monitoreo.App')
}
foreach($window in $windows){
    $running=Get-Process -Id $window.ProcessId -ErrorAction SilentlyContinue
    if($running -and $running.MainWindowHandle -ne 0){
        if(!$running.CloseMainWindow()){throw 'No se pudo cerrar la ventana. Cierra la aplicacion y ejecuta Iniciar.cmd.'}
        if(!$running.WaitForExit(8000)){throw 'La aplicacion sigue cerrando. No se iniciara otra instancia.'}
    }
}
$arguments=@('-NoProfile','-File',('"'+(Join-Path $PSScriptRoot 'Run.ps1')+'"'))
Start-Process -FilePath 'powershell.exe' -ArgumentList $arguments -WorkingDirectory $taskRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $taskRoot '.local/app-out.log') -RedirectStandardError (Join-Path $taskRoot '.local/app-error.log') -PassThru | Select-Object Id
