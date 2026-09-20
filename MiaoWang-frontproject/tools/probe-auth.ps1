# Verify the real auth flow through the GATEWAY (port 7000), the same path both frontends use.
#
# Why this exists: the frontend must send BOTH `username` and `token` headers, but phone login only
# returns `{ token }` and the gateway does NOT inject username (backlog #8). So the frontends derive
# the username from the JWT payload. This script prints that payload and exercises the whole loop so
# the derivation is checked against reality instead of hoped for.
#
# Usage:
#   $env:MIAOWANG_REDIS_PASSWORD = "123456"      # required, see Nacos MiaoWang-all-service.yaml
#   & .\probe-auth.ps1
#
# SIDE EFFECTS (dev DB only): POST /user creates a throwaway account (probe<HHmmss>) plus one
# auto-registered phone account per run. Phones are derived from the clock so the per-phone SMS
# limits are not hit. There is no delete-user API, so clean these rows up manually if it bothers you.

$env:MIAOWANG_USER_BASE = if ($env:MIAOWANG_USER_BASE) { $env:MIAOWANG_USER_BASE } else { 'http://127.0.0.1:7000/miaowang-user/mapleleaf/miaowang/v1/user' }
$env:MIAOWANG_REDIS_PASSWORD = if ($env:MIAOWANG_REDIS_PASSWORD) { $env:MIAOWANG_REDIS_PASSWORD } else { '123456' }

. "$PSScriptRoot\..\..\MiaoWang-backproject\dev-tools\lib.ps1"
Assert-MwConfig

$stamp = Get-Date -Format 'HHmmss'
$newUser = "probe$stamp"                 # 11 chars, matches [A-Za-z0-9_]{3,20}
$newPass = 'MiaoWang123'
$phoneReg = "137$stamp" + (Get-Date -Format 'ff')   # fresh phone for scene=register
$phoneDup = "138$stamp" + (Get-Date -Format 'ff')   # fresh phone for the duplicate-username case
$phoneLogin = "139$stamp" + (Get-Date -Format 'ff') # fresh phone for phone-login (auto register)

# Decode a JWT payload the same way the frontends do (explicit username keys first, never `sub`).
function Get-JwtUsername([string]$token) {
    $parts = $token.Split('.')
    $payload = $parts[1].Replace('-', '+').Replace('_', '/')
    switch ($payload.Length % 4) {
        2 { $payload += '==' }
        3 { $payload += '=' }
    }
    $json = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($payload))
    Write-Host "  payload = $json"
    $p = $json | ConvertFrom-Json
    foreach ($holder in @($p, $p.claims)) {
        if ($holder) {
            foreach ($k in @('username', 'userName', 'user_name', 'nickName')) {
                if ($holder.$k) { return $holder.$k }
            }
        }
    }
    return ''
}

Write-Host ""
Write-Host "===== 1. graphic captcha (anonymous) ====="
$cap = New-Captcha
Write-Host "  captchaKey = $($cap.key), plain(redis) = $($cap.plain)"
if (-not $cap.plain) { Write-Host "  !! Redis read failed - check MIAOWANG_REDIS_PASSWORD" -ForegroundColor Red }

Write-Host ""
Write-Host "===== 2. does the register page need auth for has-username? ====="
Write-Host "  anonymous          -> $(Http-Get "$script:MwBase/has-username?username=$newUser")"

Write-Host ""
Write-Host "===== 3. register via POST /user (scene=register sms) ====="
$sms = Send-Sms $phoneReg 'register'
Write-Host "  POST /user/sms     -> $($sms.send)"
Write-Host "  sms code (redis)   = $($sms.code)"
$regBody = @{
    username = $newUser; password = $newPass; realName = 'ProbeTest'
    phone = $phoneReg; mail = "$newUser@example.com"; ownerCallName = 'shiba'; smsCode = $sms.code
} | ConvertTo-Json -Compress
Write-Host "  POST /user         -> $(Http-Post "$script:MwBase" $regBody)"

Write-Host ""
Write-Host "===== 4. password login with the account just created ====="
$pwLogin = Http-Post "$script:MwBase/login" (@{ username = $newUser; password = $newPass } | ConvertTo-Json -Compress)
Write-Host "  POST /user/login   -> $pwLogin"
$pwToken = ($pwLogin | ConvertFrom-Json).data.token
$pwName = Get-JwtUsername $pwToken
Write-Host "  username from JWT  = '$pwName' (expected '$newUser')"
if ($pwName -ne $newUser) { Write-Host "  !! MISMATCH - the frontends would send a wrong username header" -ForegroundColor Red }

Write-Host ""
Write-Host "===== 5. duplicate username: which error code comes back? ====="
$sms2 = Send-Sms $phoneDup 'register'
$dupBody = @{
    username = $newUser; password = $newPass; realName = 'ProbeTest'
    phone = $phoneDup; mail = "$newUser@example.com"; ownerCallName = 'shiba'; smsCode = $sms2.code
} | ConvertTo-Json -Compress
Write-Host "  POST /user (same username, new phone) -> $(Http-Post "$script:MwBase" $dupBody)"

Write-Host ""
Write-Host "===== 6. phone login (auto-registers a new phone) ====="
$sms3 = Send-Sms $phoneLogin 'login'
$phLogin = Http-Post "$script:MwBase/login/phone" (@{ phone = $phoneLogin; smsCode = $sms3.code } | ConvertTo-Json -Compress)
Write-Host "  POST /user/login/phone -> $phLogin"
$phToken = ($phLogin | ConvertFrom-Json).data.token
$phName = Get-JwtUsername $phToken
Write-Host "  username from JWT  = '$phName'"

Write-Host ""
Write-Host "===== 7. authenticated calls (username + token headers) ====="
$h = @{ username = $pwName; token = $pwToken }
Write-Host "  GET /user/$pwName              -> $(Http-Get "$script:MwBase/$pwName" $h)"
Write-Host "  GET /user/has-username (auth)  -> $(Http-Get "$script:MwBase/has-username?username=$newUser" $h)"
Write-Host "  GET /user/check-login (no hdr) -> $(Http-Get "$script:MwBase/check-login?username=$pwName&token=$pwToken")"
Write-Host "  GET /user/check-login (hdr)    -> $(Http-Get "$script:MwBase/check-login?username=$pwName&token=$pwToken" $h)"
Write-Host "  DELETE /user/logout   (hdr)    -> $(Invoke-MwHttp 'Delete' "$script:MwBase/logout?username=$pwName&token=$pwToken" $null $h)"
Write-Host "  GET /user/check-login after logout -> $(Http-Get "$script:MwBase/check-login?username=$pwName&token=$pwToken" $h)"

Write-Host ""
Write-Host "===== 8. what happens WITHOUT a username header (gateway only checks the token) ====="
Write-Host "  GET /user/$pwName with token only -> $(Http-Get "$script:MwBase/$pwName" @{ token = $phToken })"