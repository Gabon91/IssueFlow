$csv = Import-Csv target\site\jacoco\jacoco.csv
$svc = $csv | Where-Object { $_.PACKAGE -like '*service*' }
$rows = $svc | ForEach-Object {
    $m = [int]$_.LINE_MISSED
    $c = [int]$_.LINE_COVERED
    $pct = if (($m + $c) -gt 0) { [math]::Round(100 * $c / ($m + $c), 1) } else { 0 }
    [pscustomobject]@{
        Class       = $_.CLASS
        LineCovered = $c
        LineMissed  = $m
        Pct         = $pct
    }
}
$rows | Sort-Object Pct | Format-Table -AutoSize

$totalC = ($svc | Measure-Object LINE_COVERED -Sum).Sum
$totalM = ($svc | Measure-Object LINE_MISSED -Sum).Sum
$totalPct = if (($totalC + $totalM) -gt 0) { [math]::Round(100 * $totalC / ($totalC + $totalM), 1) } else { 0 }
Write-Host "Service-layer total: covered=$totalC missed=$totalM pct=$totalPct%"
