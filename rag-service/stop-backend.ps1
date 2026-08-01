$conns = Get-NetTCPConnection -LocalPort 18473 -State Listen -ErrorAction SilentlyContinue
foreach ($c in $conns) {
    Write-Output ("Stopping PID " + $c.OwningProcess)
    Stop-Process -Id $c.OwningProcess -Force
}
Start-Sleep -Seconds 2
$left = Get-NetTCPConnection -LocalPort 18473 -State Listen -ErrorAction SilentlyContinue
if ($left) { Write-Output 'PORT STILL IN USE' } else { Write-Output 'PORT FREE' }
