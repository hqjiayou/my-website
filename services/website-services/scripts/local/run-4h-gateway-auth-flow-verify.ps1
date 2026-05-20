# 阶段 4H：经 Gateway :8080 的 Admin 认证全链路 HTTP 验证。
# 前提：local-postgres / local-redis 已启动；Auth 8083、Admin 8081、Gateway 8080 已启动；
#       已执行 seed-admin-local-verify.sql；.env 与 RSA 密钥就绪（见 README-4G-jwks.md）。
#
# 用法（在 services/website-services 目录）：
#   .\scripts\local\run-4h-gateway-auth-flow-verify.ps1
#   .\scripts\local\run-4h-gateway-auth-flow-verify.ps1 -ObserveRedis
#   .\scripts\local\run-4h-gateway-auth-flow-verify.ps1 -RequestId "4h-manual-01"

param(
    [string] $GatewayBase = "http://127.0.0.1:8080",
    [string] $Username = "admin_local_verify",
    [string] $Password = "Local4c_Verify",
    [string] $RequestId = "",
    [switch] $ObserveRedis,
    [string] $RedisCli = "docker",
    [string] $RedisContainer = "local-redis"
)

$ErrorActionPreference = "Stop"
$tmp = Join-Path $env:TEMP "gw4h_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
New-Item -ItemType Directory -Force -Path $tmp | Out-Null

function Wait-Health([string]$base, [string]$label) {
    for ($i = 0; $i -lt 60; $i++) {
        try {
            $txt = curl.exe -sS "$base/actuator/health"
            if ($txt -match '"status"\s*:\s*"UP"') { return }
        } catch {}
        Start-Sleep -Seconds 2
    }
    throw "$label health check failed: $base"
}

function StripHttpCode([string]$raw) {
    return ($raw -replace "(?s)\s*HTTP_CODE:\d+\s*$", "").TrimEnd()
}

function Get-HttpCode([string]$raw) {
    if ($raw -match "HTTP_CODE:(\d+)\s*$") { return [int]$Matches[1] }
    return 0
}

function Header-Lines([string]$hdrFile, [string]$pattern) {
    if (-not (Test-Path $hdrFile)) { return @() }
    return Get-Content $hdrFile | Select-String -Pattern $pattern | ForEach-Object { $_.Line }
}

function Invoke-Gw {
    param(
        [string]$Method,
        [string]$Path,
        [string[]]$ExtraHeaders = @(),
        [string]$BodyFile = "",
        [string]$CookieJar = "",
        [string]$CookieJarWrite = "",
        [string]$OutBody = "",
        [string]$OutHdr = ""
    )
    $url = "$GatewayBase$Path"
    $args = @("-sS", "-w", "`nHTTP_CODE:%{http_code}")
    if ($OutHdr) { $args += @("-D", $OutHdr) }
    if ($OutBody) { $args += @("-o", $OutBody) } else { $args += @("-o", "NUL") }
    if ($CookieJar) { $args += @("-b", $CookieJar) }
    if ($CookieJarWrite) { $args += @("-c", $CookieJarWrite) }
    $args += @("-X", $Method, $url)
    foreach ($h in $ExtraHeaders) { $args += @("-H", $h) }
    if ($BodyFile) { $args += @("--data-binary", "@$BodyFile") }
    $raw = & curl.exe @args 2>&1 | Out-String
    return @{ Raw = $raw; Code = (Get-HttpCode $raw); Body = if ($OutBody -and (Test-Path $OutBody)) { Get-Content $OutBody -Raw } else { "" } }
}

function Resolve-RedisPassword {
    if ($env:REDIS_PASSWORD -and $env:REDIS_PASSWORD -notin @("", "CHANGE_ME")) {
        return $env:REDIS_PASSWORD.Trim()
    }
    $envFile = Join-Path $PSScriptRoot "..\..\.env"
    if (Test-Path $envFile) {
        foreach ($line in Get-Content $envFile) {
            if ($line -match '^\s*REDIS_PASSWORD\s*=\s*(.+)\s*$') {
                $v = $Matches[1].Trim()
                if ($v -and $v -notin @("", "CHANGE_ME")) { return $v }
            }
        }
    }
    return $null
}

function Redis-Cmd([string]$redisArgs) {
    if (-not $ObserveRedis) { return }
    $pass = Resolve-RedisPassword
    Write-Host "  [redis] redis-cli $redisArgs"
    if ($RedisCli -eq "docker") {
        $cliArgs = @("exec", $RedisContainer, "redis-cli")
        if ($pass) { $cliArgs += @("-a", $pass, "--no-auth-warning") }
        $cliArgs += $redisArgs.Split(" ", [System.StringSplitOptions]::RemoveEmptyEntries)
        & docker @cliArgs 2>&1 | ForEach-Object { "    $_" }
    } else {
        $cliArgs = @()
        if ($pass) { $cliArgs += @("-a", $pass, "--no-auth-warning") }
        $cliArgs += $redisArgs.Split(" ", [System.StringSplitOptions]::RemoveEmptyEntries)
        & redis-cli @cliArgs 2>&1 | ForEach-Object { "    $_" }
    }
}

$ridHdr = @()
if ($RequestId) { $ridHdr = @("X-Request-Id: $RequestId") }

Write-Host "==> 4H Gateway flow verify: $GatewayBase"
Wait-Health $GatewayBase "Gateway"

$loginBody = Join-Path $tmp "login.json"
Set-Content -Path $loginBody -Value (@{ username = $Username; password = $Password } | ConvertTo-Json -Compress) -Encoding UTF8
$cj = Join-Path $tmp "session.cj"
$cjOrig = Join-Path $tmp "orig.cj"
$cjRot = Join-Path $tmp "rot.cj"
Remove-Item $cj, $cjOrig, $cjRot -ErrorAction SilentlyContinue

# --- 1 Login ---
Write-Host "`n=== [1] Gateway Login ==="
$hdrLogin = Join-Path $tmp "login.hdr"
$bodyLogin = Join-Path $tmp "login_out.json"
$login = Invoke-Gw -Method POST -Path "/api/auth/admin/login" `
    -ExtraHeaders (@("Accept-Language: ja-JP", "Content-Type: application/json") + $ridHdr) `
    -BodyFile $loginBody -CookieJarWrite $cj -OutBody $bodyLogin -OutHdr $hdrLogin
Write-Host "HTTP $($login.Code)"
if ($login.Body) { Write-Host $login.Body }
else { Write-Host (StripHttpCode $login.Raw) }
Header-Lines $hdrLogin "^(HTTP/|Set-Cookie:|X-Request-Id:)" | ForEach-Object { Write-Host $_ }
if ($login.Code -ne 200) {
    throw "Login failed (HTTP $($login.Code)). If message is login failed, run: scripts/local/seed-admin-local-verify.sql against your Postgres (see scripts/local/README-4C-seed.md)."
}
$loginJson = $login.Body | ConvertFrom-Json
if ($loginJson.message -ne "success") { throw "Login message expected success, got $($loginJson.message)" }
$accessToken = $loginJson.data.accessToken
Write-Host "accessToken prefix: $($accessToken.Substring(0, [Math]::Min(24, $accessToken.Length)))..."
Redis-Cmd "KEYS auth:admin:*"

# --- 2 Ping Bearer ---
Write-Host "`n=== [2] Gateway Admin Ping (Bearer) ==="
$pingOk = Invoke-Gw -Method GET -Path "/api/admin/ping" `
    -ExtraHeaders (@("Authorization: Bearer $accessToken", "Accept-Language: ja-JP") + $ridHdr)
Write-Host "HTTP $($pingOk.Code) $(StripHttpCode $pingOk.Raw)"
if ($pingOk.Code -ne 200) { throw "Ping with bearer failed" }

# --- 3 Refresh ---
Write-Host "`n=== [3] Gateway Refresh ==="
Copy-Item -Force $cj $cjOrig
$refresh = Invoke-Gw -Method POST -Path "/api/auth/admin/refresh" `
    -ExtraHeaders (@("Accept-Language: ja-JP") + $ridHdr) -CookieJar $cj -CookieJarWrite $cjRot
Write-Host "HTTP $($refresh.Code) $(StripHttpCode $refresh.Raw)"
if ($refresh.Code -ne 200) { throw "Refresh failed" }
$rotJson = (StripHttpCode $refresh.Raw) | ConvertFrom-Json
$accessToken2 = $rotJson.data.accessToken
Redis-Cmd "KEYS auth:admin:refresh-replay:*"
Redis-Cmd "KEYS auth:admin:refresh:*"

# --- 4 Replay ---
Write-Host "`n=== [4] Gateway Refresh Replay (old cookie) ==="
$replay = Invoke-Gw -Method POST -Path "/api/auth/admin/refresh" `
    -ExtraHeaders (@("Accept-Language: ja-JP") + $ridHdr) -CookieJar $cjOrig
Write-Host "HTTP $($replay.Code) $(StripHttpCode $replay.Raw)"
if ($replay.Code -ne 401) { Write-Warning "Expected HTTP 401 for replay, got $($replay.Code)" }

Write-Host "`n=== [4b] Refresh with rotated cookie after replay (expect fail) ==="
$afterReplay = Invoke-Gw -Method POST -Path "/api/auth/admin/refresh" `
    -ExtraHeaders (@("Accept-Language: ja-JP") + $ridHdr) -CookieJar $cjRot
Write-Host "HTTP $($afterReplay.Code) $(StripHttpCode $afterReplay.Raw)"

# --- Re-login for logout chain ---
Write-Host "`n=== [4c] Fresh login for logout ==="
Remove-Item $cj -ErrorAction SilentlyContinue
$login2 = Invoke-Gw -Method POST -Path "/api/auth/admin/login" `
    -ExtraHeaders (@("Accept-Language: ja-JP", "Content-Type: application/json") + $ridHdr) `
    -BodyFile $loginBody -CookieJarWrite $cj
Write-Host "HTTP $($login2.Code)"

# --- 5 Logout ---
Write-Host "`n=== [5] Gateway Logout (with cookie) ==="
$hdrLo = Join-Path $tmp "logout.hdr"
$logout = Invoke-Gw -Method POST -Path "/api/auth/admin/logout" `
    -ExtraHeaders (@("Accept-Language: ja-JP") + $ridHdr) -CookieJar $cj -CookieJarWrite $cj -OutHdr $hdrLo
Write-Host "HTTP $($logout.Code) $(StripHttpCode $logout.Raw)"
Header-Lines $hdrLo "Set-Cookie:" | ForEach-Object { Write-Host $_ }
Redis-Cmd "KEYS auth:admin:session:*"

Write-Host "`n=== [5b] Logout idempotent (no cookie) ==="
$logout2 = Invoke-Gw -Method POST -Path "/api/auth/admin/logout" `
    -ExtraHeaders (@("Accept-Language: ja-JP") + $ridHdr)
Write-Host "HTTP $($logout2.Code) $(StripHttpCode $logout2.Raw)"

# --- 6 Errors ---
Write-Host "`n=== [6a] Wrong password via Gateway ==="
$bad = '{"username":"admin_local_verify","password":"wrong"}'
$badFile = Join-Path $tmp "bad.json"
Set-Content $badFile $bad -Encoding UTF8
$wrongLogin = Invoke-Gw -Method POST -Path "/api/auth/admin/login" `
    -ExtraHeaders (@("Accept-Language: ja-JP", "Content-Type: application/json") + $ridHdr) -BodyFile $badFile
Write-Host "HTTP $($wrongLogin.Code) $(StripHttpCode $wrongLogin.Raw)"

Write-Host "`n=== [6b] Ping without Bearer ==="
$hdr401 = Join-Path $tmp "ping401.hdr"
$body401 = Join-Path $tmp "ping401.json"
$noTok = Invoke-Gw -Method GET -Path "/api/admin/ping" `
    -ExtraHeaders (@("Accept-Language: ja-JP") + $ridHdr) -OutHdr $hdr401 -OutBody $body401
Write-Host "HTTP $($noTok.Code) $(Get-Content $body401 -Raw)"
Header-Lines $hdr401 "^(WWW-Authenticate:|X-Request-Id:)" | ForEach-Object { Write-Host $_ }

Write-Host "`n=== [6c] Invalid Bearer ==="
$hdrInv = Join-Path $tmp "inv.hdr"
$bodyInv = Join-Path $tmp "inv.json"
$inv = Invoke-Gw -Method GET -Path "/api/admin/ping" `
    -ExtraHeaders (@("Authorization: Bearer invalid-token", "Accept-Language: ja-JP") + $ridHdr) `
    -OutHdr $hdrInv -OutBody $bodyInv
Write-Host "HTTP $($inv.Code) $(Get-Content $bodyInv -Raw)"
Header-Lines $hdrInv "WWW-Authenticate" | ForEach-Object { Write-Host $_ }

Write-Host "`n=== [6d] Refresh without cookie ==="
$noCk = Invoke-Gw -Method POST -Path "/api/auth/admin/refresh" `
    -ExtraHeaders (@("Accept-Language: ja-JP") + $ridHdr)
Write-Host "HTTP $($noCk.Code) $(StripHttpCode $noCk.Raw)"

Write-Host "`n==> 4H script finished. Artifacts: $tmp"
Write-Host "Check Gateway/Auth/Admin logs for the same X-Request-Id when -RequestId was set."
