$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'
function Hdr($t) { Write-Host ""; Write-Host ("=== " + $t + " ===") -ForegroundColor Cyan }
function Ok($m)  { Write-Host ("  PASS " + $m) -ForegroundColor Green }
function Fail($m){ Write-Host ("  FAIL " + $m) -ForegroundColor Red }
function StatusOf($err) { try { $err.Exception.Response.StatusCode.value__ } catch { 0 } }

# ---------- 1. Auth ----------
Hdr '1. Auth: admin login'
$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' `
    -Body (@{ username='admin'; password='admin12345' } | ConvertTo-Json -Compress)
$adminH = @{ Authorization = "Bearer $($login.accessToken)" }
$adminTok = $login.accessToken
if ($adminTok) { Ok 'admin token issued' } else { Fail 'no token'; exit 1 }

# ---------- 2. Developer ----------
Hdr '2. Users: create developer + login'
$devUser = "smoke_dev_$(Get-Random -Min 1000 -Max 9999)"
$dev = Invoke-RestMethod -Method Post -Uri "$base/users" -Headers $adminH -ContentType 'application/json' `
    -Body (@{ username=$devUser; email="$devUser@e.com"; fullName='Smoke Dev'; role='DEVELOPER'; password='password1' } | ConvertTo-Json -Compress)
Ok "developer id=$($dev.id) username=$devUser"
$devLogin = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' `
    -Body (@{ username=$devUser; password='password1' } | ConvertTo-Json -Compress)
$devH = @{ Authorization = "Bearer $($devLogin.accessToken)" }
$devTok = $devLogin.accessToken
Ok 'developer can login'

# ---------- 3. Projects + Tickets ----------
Hdr '3. Projects + Tickets'
$proj = Invoke-RestMethod -Method Post -Uri "$base/projects" -Headers $adminH -ContentType 'application/json' `
    -Body (@{ name="Smoke $(Get-Random)"; description='smoke run'; ownerId=1 } | ConvertTo-Json -Compress)
Ok "project id=$($proj.id) name=$($proj.name)"
$t1 = Invoke-RestMethod -Method Post -Uri "$base/tickets" -Headers $adminH -ContentType 'application/json' `
    -Body (@{ projectId=$proj.id; title='Blocker'; description='b'; type='BUG'; priority='LOW'; status='TODO'; assigneeId=$dev.id } | ConvertTo-Json -Compress)
$t2 = Invoke-RestMethod -Method Post -Uri "$base/tickets" -Headers $adminH -ContentType 'application/json' `
    -Body (@{ projectId=$proj.id; title='Blocked'; description='b'; type='FEATURE'; priority='MEDIUM'; status='TODO' } | ConvertTo-Json -Compress)
Ok "tickets created: blocker=$($t1.id), blocked=$($t2.id)"

# ---------- 4. Dependencies + DONE guard ----------
Hdr '4. Dependencies + DONE guard'
Invoke-RestMethod -Method Post -Uri "$base/tickets/$($t2.id)/dependencies" -Headers $adminH -ContentType 'application/json' `
    -Body (@{ blockedBy=$t1.id } | ConvertTo-Json -Compress) | Out-Null
Ok "edge $($t2.id) blocked-by $($t1.id) created"
try {
    Invoke-RestMethod -Method Patch -Uri "$base/tickets/$($t2.id)" -Headers $adminH -ContentType 'application/json' `
        -Body (@{ status='DONE' } | ConvertTo-Json -Compress) | Out-Null
    Fail 'DONE while blocked allowed'
} catch { $c = StatusOf $_; if ($c -eq 422) { Ok "DONE-while-blocked rejected (HTTP $c)" } else { Fail "expected 422 got $c" } }

# ---------- 5. Workload ----------
Hdr '5. Workload reporting'
$wl = Invoke-RestMethod -Uri "$base/projects/$($proj.id)/workload" -Headers $adminH
$devRow = $wl | Where-Object { $_.userId -eq $dev.id }
if ($devRow.openTicketCount -ge 1) { Ok "dev has $($devRow.openTicketCount) open ticket(s)" } else { Fail 'workload off' }

# ---------- 6. Comments + Mentions ----------
Hdr '6. Comments + Mentions'
$c = Invoke-RestMethod -Method Post -Uri "$base/tickets/$($t1.id)/comments" -Headers $adminH -ContentType 'application/json' `
    -Body (@{ authorId=1; content="ping @$devUser please" } | ConvertTo-Json -Compress)
Ok "comment $($c.id) created with mention"
$inbox = Invoke-RestMethod -Uri "$base/users/$($dev.id)/mentions" -Headers $devH
$mentionCount = if ($inbox.content) { $inbox.content.Count } else { @($inbox).Count }
if ($mentionCount -ge 1) { Ok "dev inbox has $mentionCount mention(s)" } else { Fail 'no mention surfaced' }

# ---------- 7. Attachments ----------
Hdr '7. Attachment upload + download + MIME guard'
$txtPath = [IO.Path]::Combine([IO.Path]::GetTempPath(), "smoke-$(Get-Random).txt")
'hello smoke' | Set-Content -Path $txtPath -NoNewline -Encoding ascii
$attJson = curl.exe -s -H "Authorization: Bearer $adminTok" -F "file=@$txtPath;type=text/plain" "$base/tickets/$($t1.id)/attachments"
$att = $attJson | ConvertFrom-Json
Ok "uploaded id=$($att.id) name=$($att.filename) type=$($att.contentType)"
$dl = curl.exe -s -o NUL -w "%{http_code}" -H "Authorization: Bearer $adminTok" "$base/tickets/$($t1.id)/attachments/$($att.id)/download"
if ($dl -eq '200') { Ok 'download 200' } else { Fail "download $dl" }
$badPath = [IO.Path]::Combine([IO.Path]::GetTempPath(), "smoke-evil-$(Get-Random).exe")
'MZ' | Set-Content -Path $badPath -NoNewline -Encoding ascii
$badCode = curl.exe -s -o NUL -w "%{http_code}" -H "Authorization: Bearer $adminTok" -F "file=@$badPath;type=application/x-msdownload" "$base/tickets/$($t1.id)/attachments"
if ($badCode -eq '415') { Ok "MIME whitelist rejected exe (HTTP $badCode)" } else { Fail "expected 415 got $badCode" }
Remove-Item $txtPath,$badPath -Force -ErrorAction SilentlyContinue

# ---------- 8. CSV export + import ----------
Hdr '8. CSV export + re-import creates new IDs'
$csvFile = [IO.Path]::Combine([IO.Path]::GetTempPath(), "smoke-export-$(Get-Random).csv")
curl.exe -s -H "Authorization: Bearer $adminTok" -o $csvFile "$base/tickets/export?projectId=$($proj.id)"
$rowCount = (Get-Content -Path $csvFile).Count - 1
Ok "exported $rowCount data row(s)"
$tickBefore = (Invoke-RestMethod -Uri "$base/tickets?projectId=$($proj.id)" -Headers $adminH).Count
$report = curl.exe -s -H "Authorization: Bearer $adminTok" -F "file=@$csvFile;type=text/csv" -F "projectId=$($proj.id)" "$base/tickets/import" | ConvertFrom-Json
Ok "import created=$($report.created) failed=$($report.failed)"
$tickAfter = Invoke-RestMethod -Uri "$base/tickets?projectId=$($proj.id)" -Headers $adminH
if ($tickAfter.Count -eq ($tickBefore + $rowCount)) { Ok "tickets in project now=$($tickAfter.Count) (re-import created new rows, ids not preserved)" } else { Fail "expected $($tickBefore+$rowCount), got $($tickAfter.Count)" }
Remove-Item $csvFile -Force -ErrorAction SilentlyContinue

# ---------- 9. Audit log ----------
Hdr '9. Audit log filter + RBAC'
$audit = Invoke-RestMethod -Uri "$base/audit-logs?entityType=TICKET&entityId=$($t1.id)" -Headers $adminH
$auditCount = @($audit).Count
Ok "audit rows for ticket $($t1.id): $auditCount"
try { Invoke-RestMethod -Uri "$base/audit-logs" -Headers $devH | Out-Null; Fail 'developer reached audit-logs' }
catch { $c = StatusOf $_; if ($c -eq 403) { Ok "developer blocked (HTTP $c)" } else { Fail "expected 403 got $c" } }

# ---------- 10. User soft-delete safeguards ----------
Hdr '10. User safeguards'
try { Invoke-RestMethod -Method Delete -Uri "$base/users/1" -Headers $adminH | Out-Null; Fail 'self-delete allowed' }
catch { $c = StatusOf $_; if ($c -eq 422) { Ok "self-delete blocked (HTTP $c)" } else { Fail "expected 422 got $c" } }
try { Invoke-RestMethod -Method Delete -Uri "$base/users/$($dev.id)" -Headers $adminH | Out-Null; Fail 'delete-with-open-tickets allowed' }
catch { $c = StatusOf $_; if ($c -eq 409) { Ok "delete blocked while assigned (HTTP $c)" } else { Fail "expected 409 got $c" } }

# Free the dev by soft-deleting every ticket still assigned to them (t1 + any CSV-imported copies)
$assigned = Invoke-RestMethod -Uri "$base/tickets?projectId=$($proj.id)" -Headers $adminH `
    | Where-Object { $_.assigneeId -eq $dev.id }
foreach ($tk in $assigned) {
    Invoke-RestMethod -Method Delete -Uri "$base/tickets/$($tk.id)" -Headers $adminH | Out-Null
}
Invoke-RestMethod -Method Delete -Uri "$base/users/$($dev.id)" -Headers $adminH | Out-Null
Ok "dev soft-deleted after freeing $($assigned.Count) ticket(s)"
$deletedList = Invoke-RestMethod -Uri "$base/users/deleted" -Headers $adminH
if (@($deletedList) | Where-Object { $_.id -eq $dev.id }) { Ok 'shows up in /users/deleted' } else { Fail 'missing from deleted list' }
try { Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body (@{ username=$devUser; password='password1' } | ConvertTo-Json -Compress) | Out-Null; Fail 'soft-deleted user could log in' }
catch { $c = StatusOf $_; if ($c -eq 401) { Ok "soft-deleted user denied login (HTTP $c)" } else { Fail "expected 401 got $c" } }
Invoke-RestMethod -Method Post -Uri "$base/users/$($dev.id)/restore" -Headers $adminH | Out-Null
Ok 'dev restored'
$reLogin = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body (@{ username=$devUser; password='password1' } | ConvertTo-Json -Compress)
if ($reLogin.accessToken) { Ok 'restored dev can log in again' } else { Fail 'restored dev still locked out' }

Write-Host ""
Write-Host "All checks complete." -ForegroundColor Green
