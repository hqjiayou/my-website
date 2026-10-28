#Requires -Version 5.1
<#
    pwsh ./scripts/local/run-seed-admin-hqjiayou-super.ps1

.EXAMPLE
  .\scripts\local\run-seed-admin-hqjiayou-super.ps1 -UseHostPsql
#>
[CmdletBinding()]
param(
    [string] $Container = 'local-dev-postgres-1',
    [switch] $UseHostPsql
)

$ErrorActionPreference = 'Stop'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ServicesRoot = Resolve-Path (Join-Path $ScriptDir '..\..')
$SqlFile = Join-Path $ScriptDir 'seed-admin-hqjiayou-super.sql'
$EnvFile = Join-Path $ServicesRoot '.env'

if (-not (Test-Path -LiteralPath $SqlFile)) {
    throw "SQL 文件不存在: $SqlFile"
}

function Read-DotEnvValue {
    param([string] $Key, [string] $Default)
    if (-not (Test-Path -LiteralPath $EnvFile)) {
        return $Default
    }
    foreach ($line in Get-Content -LiteralPath $EnvFile -Encoding utf8) {
        if ($line -match "^\s*#") { continue }
        if ($line -match "^\s*$([regex]::Escape($Key))\s*=\s*(.+)\s*$") {
            return $Matches[1].Trim().Trim('"').Trim("'")
        }
    }
    return $Default
}

$DbName = Read-DotEnvValue -Key 'POSTGRES_DB' -Default 'my_website_dev'
$DbUser = Read-DotEnvValue -Key 'POSTGRES_USER' -Default 'hqjiayou'
$DbPassword = Read-DotEnvValue -Key 'POSTGRES_PASSWORD' -Default 'hqjiayou'

Write-Host "==> seed admin: username=hqjiayou role=super_admin database=$DbName user=$DbUser"

if ($UseHostPsql) {
    $psql = Get-Command psql -ErrorAction SilentlyContinue
    if (-not $psql) {
        throw '未找到 psql，请安装 PostgreSQL 客户端或使用默认 Docker 方式（去掉 -UseHostPsql）。'
    }
    $env:PGPASSWORD = $DbPassword
    try {
        & psql -h localhost -p 5432 -U $DbUser -d $DbName -v ON_ERROR_STOP=1 -f $SqlFile
    } finally {
        Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    }
} else {
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $docker) {
        throw '未找到 docker，请安装 Docker 或使用 -UseHostPsql。'
    }
    $running = docker ps --format '{{.Names}}' 2>$null | Where-Object { $_ -eq $Container }
    if (-not $running) {
        throw "容器 '$Container' 未运行。请先启动 local-stack，或指定 -Container，或使用 -UseHostPsql。"
    }
    Get-Content -LiteralPath $SqlFile -Raw -Encoding utf8 | docker exec -i $Container psql -U $DbUser -d $DbName -v ON_ERROR_STOP=1 -f -
}

if ($LASTEXITCODE -ne 0) {
    throw "seed 执行失败，退出码: $LASTEXITCODE"
}

Write-Host '==> 完成。可使用 hqjiayou / 123456 登录管理端（仅本地）。'
