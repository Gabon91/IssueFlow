$csv = Import-Csv target\site\jacoco\jacoco.csv

$rows = $csv | ForEach-Object {
    $m = [int]$_.LINE_MISSED
    $c = [int]$_.LINE_COVERED
    $pct = if (($m + $c) -gt 0) { [math]::Round(100 * $c / ($m + $c), 1) } else { 0 }
    [pscustomobject]@{
        Package     = $_.PACKAGE
        Class       = $_.CLASS
        LineCovered = $c
        LineMissed  = $m
        Pct         = $pct
    }
}

# Identify "service-like" classes: anything ending in Service, or in a *service* sub-package.
$svc = $rows | Where-Object {
    $_.Class -like '*Service' -or
    $_.Class -like '*Aspect' -or
    $_.Package -like '*.service' -or
    $_.Package -like '*.csv' -or
    $_.Package -like '*.escalation' -or
    $_.Package -like '*.audit'
}

$svc | Sort-Object Pct | Format-Table Package, Class, LineCovered, LineMissed, Pct -AutoSize

$totalC = ($svc | Measure-Object LineCovered -Sum).Sum
$totalM = ($svc | Measure-Object LineMissed -Sum).Sum
$totalPct = if (($totalC + $totalM) -gt 0) { [math]::Round(100 * $totalC / ($totalC + $totalM), 1) } else { 0 }
Write-Host "Service-layer aggregate: covered=$totalC missed=$totalM pct=$totalPct%"

Write-Host ""
Write-Host "== All packages summary =="
$byPkg = $rows | Group-Object Package | ForEach-Object {
    $c = ($_.Group | Measure-Object LineCovered -Sum).Sum
    $m = ($_.Group | Measure-Object LineMissed -Sum).Sum
    $p = if (($c + $m) -gt 0) { [math]::Round(100 * $c / ($c + $m), 1) } else { 0 }
    [pscustomobject]@{ Package = $_.Name; Covered = $c; Missed = $m; Pct = $p }
}
$byPkg | Sort-Object Pct | Format-Table -AutoSize
