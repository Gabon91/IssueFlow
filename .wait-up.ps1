$ErrorActionPreference = 'SilentlyContinue'
for ($i = 0; $i -lt 120; $i++) {
    try {
        $r = Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:8080/actuator/health' -TimeoutSec 2
        if ($r.StatusCode -eq 200) { Write-Host "UP after $i s"; exit 0 }
    } catch {}
    Start-Sleep 1
}
Write-Host 'TIMEOUT'
exit 1
