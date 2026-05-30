$ErrorActionPreference = "Stop"
$adminRoot = Resolve-Path "$PSScriptRoot\..\src\main\java\com\mywebsite\admin"
$testRoot = Resolve-Path "$PSScriptRoot\..\src\test\java\com\mywebsite\admin"
$rbacMain = Join-Path $adminRoot "rbac"

function Rewrite-Imports([string]$c) {
    $c = $c -replace 'import com\.mywebsite\.admin\.rbac\.AdminRbacDtos', 'import com.mywebsite.admin.dto.AdminRbacDtos'
    $c = $c -replace 'import com\.mywebsite\.admin\.rbac\.AdminRbacConstants', 'import com.mywebsite.admin.constant.AdminRbacConstants'
    $c = $c -replace 'import com\.mywebsite\.admin\.rbac\.AdminRbacMessageKeys', 'import com.mywebsite.admin.constant.AdminRbacMessageKeys'
    $c = $c -replace 'import com\.mywebsite\.admin\.rbac\.AdminSuperAdminRoleSupport', 'import com.mywebsite.admin.security.AdminSuperAdminRoleSupport'
    $c = $c -replace 'import com\.mywebsite\.admin\.rbac\.mapper\.', 'import com.mywebsite.admin.mapper.'
    $c = $c -replace 'import com\.mywebsite\.admin\.rbac\.service\.impl\.', 'import com.mywebsite.admin.service.impl.'
    $c = $c -replace 'import com\.mywebsite\.admin\.rbac\.service\.', 'import com.mywebsite.admin.service.'
    $c = $c -replace 'import com\.mywebsite\.admin\.rbac\.security\.', 'import com.mywebsite.admin.security.'
    $c = $c -replace 'import com\.mywebsite\.admin\.rbac\.controller\.', 'import com.mywebsite.admin.controller.'
    $c = $c -replace '\{@link com\.mywebsite\.admin\.rbac\.security\.AdminPermissionChecker\}', '{@link com.mywebsite.admin.security.AdminPermissionChecker}'
    return $c
}

function Move-MainFile($src, $destDir, $package) {
    $name = Split-Path $src -Leaf
    $dest = Join-Path $destDir $name
    New-Item -ItemType Directory -Force -Path $destDir | Out-Null
    $c = Get-Content $src -Raw -Encoding UTF8
    $c = $c -replace 'package com\.mywebsite\.admin\.rbac[^;]*;', "package $package;"
    $c = Rewrite-Imports $c
    if (Test-Path $dest) { Remove-Item $dest -Force }
    $utf8NoBom = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($dest, $c, $utf8NoBom)
}

# Main: controllers
Get-ChildItem (Join-Path $rbacMain "controller\*.java") -ErrorAction SilentlyContinue | ForEach-Object {
    Move-MainFile $_.FullName (Join-Path $adminRoot "controller") "com.mywebsite.admin.controller"
}

# Main: mappers
Get-ChildItem (Join-Path $rbacMain "mapper\*.java") -ErrorAction SilentlyContinue | ForEach-Object {
    Move-MainFile $_.FullName (Join-Path $adminRoot "mapper") "com.mywebsite.admin.mapper"
}

# Main: services
Get-ChildItem (Join-Path $rbacMain "service\*.java") -ErrorAction SilentlyContinue | ForEach-Object {
    Move-MainFile $_.FullName (Join-Path $adminRoot "service") "com.mywebsite.admin.service"
}

# Main: service impl
Get-ChildItem (Join-Path $rbacMain "service\impl\*.java") -ErrorAction SilentlyContinue | ForEach-Object {
    Move-MainFile $_.FullName (Join-Path $adminRoot "service\impl") "com.mywebsite.admin.service.impl"
}

# Main: security
Get-ChildItem (Join-Path $rbacMain "security\*.java") -ErrorAction SilentlyContinue | ForEach-Object {
    Move-MainFile $_.FullName (Join-Path $adminRoot "security") "com.mywebsite.admin.security"
}

# Root rbac files
if (Test-Path (Join-Path $rbacMain "AdminRbacDtos.java")) {
    Move-MainFile (Join-Path $rbacMain "AdminRbacDtos.java") (Join-Path $adminRoot "dto") "com.mywebsite.admin.dto"
}
if (Test-Path (Join-Path $rbacMain "AdminRbacConstants.java")) {
    Move-MainFile (Join-Path $rbacMain "AdminRbacConstants.java") (Join-Path $adminRoot "constant") "com.mywebsite.admin.constant"
}
if (Test-Path (Join-Path $rbacMain "AdminRbacMessageKeys.java")) {
    Move-MainFile (Join-Path $rbacMain "AdminRbacMessageKeys.java") (Join-Path $adminRoot "constant") "com.mywebsite.admin.constant"
}
if (Test-Path (Join-Path $rbacMain "AdminSuperAdminRoleSupport.java")) {
    Move-MainFile (Join-Path $rbacMain "AdminSuperAdminRoleSupport.java") (Join-Path $adminRoot "security") "com.mywebsite.admin.security"
}

# Tests under rbac/
$rbacTest = Join-Path $testRoot "rbac"
if (Test-Path $rbacTest) {
    Get-ChildItem (Join-Path $rbacTest "controller\*.java") -ErrorAction SilentlyContinue | ForEach-Object {
        $c = Get-Content $_.FullName -Raw -Encoding UTF8
        $c = $c -replace 'package com\.mywebsite\.admin\.rbac\.controller;', 'package com.mywebsite.admin.controller;'
        $c = Rewrite-Imports $c
        $dest = Join-Path (Join-Path $testRoot "controller") $_.Name
        New-Item -ItemType Directory -Force -Path (Join-Path $testRoot "controller") | Out-Null
        $utf8NoBom = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($dest, $c, $utf8NoBom)
    }
    Get-ChildItem (Join-Path $rbacTest "security\*.java") -ErrorAction SilentlyContinue | ForEach-Object {
        $c = Get-Content $_.FullName -Raw -Encoding UTF8
        $c = $c -replace 'package com\.mywebsite\.admin\.rbac\.security;', 'package com.mywebsite.admin.security;'
        $c = Rewrite-Imports $c
        $dest = Join-Path (Join-Path $testRoot "security") $_.Name
        New-Item -ItemType Directory -Force -Path (Join-Path $testRoot "security") | Out-Null
        $utf8NoBom = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($dest, $c, $utf8NoBom)
    }
    Get-ChildItem (Join-Path $rbacTest "service\*.java") -ErrorAction SilentlyContinue | ForEach-Object {
        $c = Get-Content $_.FullName -Raw -Encoding UTF8
        $c = $c -replace 'package com\.mywebsite\.admin\.rbac\.service;', 'package com.mywebsite.admin.service;'
        $c = Rewrite-Imports $c
        $dest = Join-Path (Join-Path $testRoot "service") $_.Name
        New-Item -ItemType Directory -Force -Path (Join-Path $testRoot "service") | Out-Null
        $utf8NoBom = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($dest, $c, $utf8NoBom)
    }
}

# Fix remaining main/test files outside rbac
$roots = @(
    (Join-Path $adminRoot "..") ,
    $testRoot
)
foreach ($root in @($adminRoot, $testRoot)) {
    Get-ChildItem $root -Recurse -Filter *.java -ErrorAction SilentlyContinue | Where-Object { $_.FullName -notmatch '\\rbac\\' } | ForEach-Object {
        $c = Get-Content $_.FullName -Raw -Encoding UTF8
        $n = Rewrite-Imports $c
        if ($n -ne $c) { Set-Content -Path $_.FullName -Value $n -Encoding UTF8 -NoNewline }
    }
}

# Remove rbac trees
if (Test-Path $rbacMain) { Remove-Item $rbacMain -Recurse -Force }
if (Test-Path $rbacTest) { Remove-Item $rbacTest -Recurse -Force }

Write-Host "Migration complete."
