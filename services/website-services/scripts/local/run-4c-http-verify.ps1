# 阶段 4C/4F 本地 HTTP 验证脚本。
# 4F 起：登录/刷新/登出打 $AuthBase（默认 http://127.0.0.1:8083），Ping/受保护业务打 $AdminBase。
# 使用前请已启动 auth（8083）与 admin（8081），或自行调整 -SkipStartJar 并在外部启动进程。

param(
    [string] $JarPath = "",
    [string] $AdminBase = "http://127.0.0.1:8081",
    [string] $AuthBase = "http://127.0.0.1:8083",
    [string] $GatewayBase = "http://127.0.0.1:8080",
    [switch] $SkipGateway,
    [switch] $SkipStartJar
)

$ErrorActionPreference = "Stop"

if (-not $JarPath) {
    $JarPath = Join-Path $PSScriptRoot "..\..\admin\target\admin-0.1.0-SNAPSHOT.jar" | Resolve-Path
}

function Wait-Health([string]$base) {
    for ($i = 0; $i -lt 90; $i++) {
        try {
            $txt = curl.exe -sS "$base/actuator/health"
            if ($txt -match '"status"\s*:\s*"UP"') {
                return
            }
        }
        catch {}
        Start-Sleep -Seconds 2
    }
    throw "Health check failed: $base"
}

$proc = $null
if (-not $SkipStartJar) {
    $env:SPRING_PROFILES_ACTIVE = "local"
    # local HTTP：需 Secure=false；亦可在外层 Shell 覆盖 SECURITY_REFRESH_COOKIE_SECURE
    if (-not $env:SECURITY_REFRESH_COOKIE_SECURE) {
        $env:SECURITY_REFRESH_COOKIE_SECURE = "false"
    }
    Write-Host "==> java -jar $JarPath"
    Write-Host "    (确保已设置 DB_URL/DB_USERNAME/DB_PASSWORD、REDIS_PASSWORD、SECURITY_JWT_SECRET 等)"
    $proc = Start-Process -FilePath "java" -ArgumentList @("-jar", $JarPath) -WorkingDirectory (Split-Path $JarPath) -PassThru -WindowStyle Hidden
}

try {
    Wait-Health $AuthBase
    Wait-Health $AdminBase
    $tmp = Join-Path $env:TEMP "admin4c"
    New-Item -ItemType Directory -Force -Path $tmp | Out-Null
    $cjLogin = Join-Path $tmp "login.cj"
    $cjOrig = Join-Path $tmp "login_orig.cj"
    $cjRot = Join-Path $tmp "after_rotate.cj"
    $cjReplay = Join-Path $tmp "replay_out.cj"

    function StripHttpCode([string]$raw) {
        return ($raw -replace "(?s)\s*HTTP_CODE:\d+\s*$", "").TrimEnd()
    }

    $wrong = '{"username":"admin_local_verify","password":"wrong"}'
    $missingUser = '{"username":"admin_local_verify_missing_xyz","password":"whatever"}'

    Write-Host "`n=== Wrong password ja-JP ==="
    $o = curl.exe -sS -w "`nHTTP_CODE:%{http_code}" -X POST "$AuthBase/api/admin/auth/login" -H "Accept-Language: ja-JP" -H "Content-Type: application/json" --data-binary $wrong
    Write-Host (StripHttpCode $o)

    Write-Host "`n=== Wrong password zh-CN ==="
    $o = curl.exe -sS -w "`nHTTP_CODE:%{http_code}" -X POST "$AuthBase/api/admin/auth/login" -H "Accept-Language: zh-CN" -H "Content-Type: application/json" --data-binary $wrong
    Write-Host (StripHttpCode $o)

    Write-Host "`n=== Wrong password en-US ==="
    $o = curl.exe -sS -w "`nHTTP_CODE:%{http_code}" -X POST "$AuthBase/api/admin/auth/login" -H "Accept-Language: en-US" -H "Content-Type: application/json" --data-binary $wrong
    Write-Host (StripHttpCode $o)

    Write-Host "`n=== Unknown username ja-JP ==="
    $o = curl.exe -sS -w "`nHTTP_CODE:%{http_code}" -X POST "$AuthBase/api/admin/auth/login" -H "Accept-Language: ja-JP" -H "Content-Type: application/json" --data-binary $missingUser
    Write-Host (StripHttpCode $o)

    Write-Host "`n=== Unknown username zh-CN ==="
    $o = curl.exe -sS -w "`nHTTP_CODE:%{http_code}" -X POST "$AuthBase/api/admin/auth/login" -H "Accept-Language: zh-CN" -H "Content-Type: application/json" --data-binary $missingUser
    Write-Host (StripHttpCode $o)

    Write-Host "`n=== Unknown username en-US ==="
    $o = curl.exe -sS -w "`nHTTP_CODE:%{http_code}" -X POST "$AuthBase/api/admin/auth/login" -H "Accept-Language: en-US" -H "Content-Type: application/json" --data-binary $missingUser
    Write-Host (StripHttpCode $o)

    Write-Host "`n=== Validation username blank ja-JP ==="
    $o = curl.exe -sS -w "`nHTTP_CODE:%{http_code}" -X POST "$AuthBase/api/admin/auth/login" -H "Accept-Language: ja-JP" -H "Content-Type: application/json" --data-binary '{"username":"","password":"x"}'
    Write-Host (StripHttpCode $o)

    Write-Host "`n=== Validation password blank zh-CN ==="
    $o = curl.exe -sS -w "`nHTTP_CODE:%{http_code}" -X POST "$AuthBase/api/admin/auth/login" -H "Accept-Language: zh-CN" -H "Content-Type: application/json" --data-binary '{"username":"admin_local_verify","password":""}'
    Write-Host (StripHttpCode $o)

    Write-Host "`n=== Validation password too long en-US ==="
    $longPw = ("a" * 129)
    $longBody = '{"username":"admin_local_verify","password":"' + $longPw + '"}'
    $o = curl.exe -sS -w "`nHTTP_CODE:%{http_code}" -X POST "$AuthBase/api/admin/auth/login" -H "Accept-Language: en-US" -H "Content-Type: application/json" --data-binary $longBody
    Write-Host (StripHttpCode $o)

    Write-Host "`n=== Login success + Set-Cookie sample (-D headers) ==="
    Remove-Item $cjLogin -ErrorAction SilentlyContinue
    $hdrLogin = Join-Path $tmp "login_hdr.txt"
    $good = '{"username":"admin_local_verify","password":"Local4c_Verify"}'
    curl.exe -sS -D $hdrLogin -o (Join-Path $tmp "login_body.json") -w "HTTP_CODE:%{http_code}`n" -c $cjLogin -X POST "$AuthBase/api/admin/auth/login" -H "Accept-Language: ja-JP" -H "Content-Type: application/json" --data-binary $good | Out-Host
    Write-Host "--- Response headers (HTTP / Set-Cookie) ---"
    Get-Content $hdrLogin | Select-String -Pattern "^(HTTP/|Set-Cookie:)" | ForEach-Object { $_.Line }

    $loginBody = Get-Content (Join-Path $tmp "login_body.json") -Raw
    $accessToken = ($loginBody | ConvertFrom-Json).data.accessToken
    Write-Host "accessToken prefix: $($accessToken.Substring(0,24))..."

    Write-Host "`n=== Ping without Bearer (401 JSON i18n) ==="
    foreach ($loc in @("ja-JP", "zh-CN", "en-US", "ko-KR")) {
        $code = curl.exe -sS -o (Join-Path $tmp "ping_$loc.body") -w "%{http_code}" -H "Accept-Language: $loc" "$AdminBase/api/admin/ping"
        Write-Host "[$loc] HTTP $code $(Get-Content (Join-Path $tmp "ping_$loc.body") -Raw)"
    }

    Write-Host "`n=== Ping with Bearer (200) ==="
    curl.exe -sS -w " HTTP_CODE:%{http_code}`n" -H "Authorization: Bearer $accessToken" -H "Accept-Language: ja-JP" "$AdminBase/api/admin/ping"

    Write-Host "`n=== Tampered JWT ==="
    $parts = $accessToken.Split(".")
    $tampered = ($parts[0..1] -join ".") + ".bad"
    $ht = Join-Path $tmp "tam.hdr"
    curl.exe -sS -D $ht -o (Join-Path $tmp "tam.body") -w "HTTP_CODE:%{http_code}`n" -H "Authorization: Bearer $tampered" "$AdminBase/api/admin/ping" | Out-Host
    Write-Host "--- WWW-Authenticate ---"
    Get-Content $ht | Select-String "WWW-Authenticate"

    Write-Host "`n=== Refresh rotate ==="
    Copy-Item -Force $cjLogin $cjOrig
    Remove-Item $cjRot -ErrorAction SilentlyContinue
    curl.exe -sS -w " HTTP_CODE:%{http_code}`n" -b $cjLogin -c $cjRot -X POST "$AuthBase/api/admin/auth/refresh" -H "Accept-Language: ja-JP" | Out-Host

    Write-Host "`n=== Replay old refresh cookie ==="
    Remove-Item $cjReplay -ErrorAction SilentlyContinue
    curl.exe -sS -w " HTTP_CODE:%{http_code}`n" -b $cjOrig -c $cjReplay -X POST "$AuthBase/api/admin/auth/refresh" -H "Accept-Language: ja-JP"

    Write-Host "`n=== Refresh with rotated cookie AFTER replay (expect invalid / session revoked) ==="
    curl.exe -sS -w " HTTP_CODE:%{http_code}`n" -b $cjRot -c $cjRot -X POST "$AuthBase/api/admin/auth/refresh" -H "Accept-Language: ja-JP"

    Write-Host "`n=== Fresh login after replay purge ==="
    Remove-Item $cjLogin -ErrorAction SilentlyContinue
    curl.exe -sS -w " HTTP_CODE:%{http_code}`n" -c $cjLogin -X POST "$AuthBase/api/admin/auth/login" -H "Accept-Language: ja-JP" -H "Content-Type: application/json" --data-binary $good

    Write-Host "`n=== Logout ==="
    curl.exe -sS -w " HTTP_CODE:%{http_code}`n" -b $cjLogin -c $cjLogin -X POST "$AuthBase/api/admin/auth/logout" -H "Accept-Language: ja-JP"

    Write-Host "`n=== Refresh after logout (expect unauthorized business envelope) ==="
    curl.exe -sS -w " HTTP_CODE:%{http_code}`n" -b $cjLogin -c $cjLogin -X POST "$AuthBase/api/admin/auth/refresh" -H "Accept-Language: ja-JP"

    Write-Host "`n=== Logout idempotent without cookie ==="
    curl.exe -sS -w " HTTP_CODE:%{http_code}`n" -X POST "$AuthBase/api/admin/auth/logout" -H "Accept-Language: ja-JP"

    if (-not $SkipGateway) {
        Write-Host "`n=== Gateway login headers (may fail if gateway not running) ==="
        try {
            $gh = Join-Path $tmp "gw.hdr"
            curl.exe -sS -D $gh -o NUL -w "HTTP_CODE:%{http_code}`n" -c (Join-Path $tmp "gw.cj") -X POST "$GatewayBase/api/admin/auth/login" -H "Accept-Language: ja-JP" -H "Content-Type: application/json" --data-binary $good
            Get-Content $gh | Select-String -Pattern "^(HTTP/|Set-Cookie:)" | ForEach-Object { $_.Line }
        }
        catch {
            Write-Host "Gateway check skipped/error: $_"
        }
    }

    Write-Host "`n=== OpenAPI snippet (auth paths on Auth service) ==="
    curl.exe -sS "$AuthBase/v3/api-docs" | Select-String "/api/admin/auth" | Select-Object -First 12
}
finally {
    if ($proc -and -not $SkipStartJar) {
        Write-Host "`n==> Stop PID $($proc.Id)"
        Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 2
        Get-NetTCPConnection -LocalPort 8081 -ErrorAction SilentlyContinue | ForEach-Object {
            Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue
        }
    }
}
