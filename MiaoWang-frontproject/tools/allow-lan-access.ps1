<#
  Allow the phone (same Wi-Fi) to reach the MiaoWang API gateway on TCP 7000.

  Why this is needed (measured on this machine, 2026-09-17):
    - api-gateway listens on 0.0.0.0:7000        -> the binding is fine, not a localhost-only issue
    - the gateway process is E:\jdk\bin\java.exe  -> this JDK path is what the firewall sees
    - the existing inbound "Java(TM) Platform SE binary" allow rules only cover
        E:\download\code\jdk\jdk21\jdk-21.0.12.1\bin\java.exe
        C:\program files\java\jdk-17\bin\java.exe
    - NEITHER matches E:\jdk\bin\java.exe, so Windows Firewall drops the phone's connection.

  What this script does:
    adds one inbound rule: TCP 7000, any profile, remote address = local subnet only
    (kept narrow on purpose: the phone is on the same subnet, the internet is not)

  Usage: right-click this file -> "Run with PowerShell", then accept the UAC prompt.
  ASCII only on purpose: Windows PowerShell 5.1 reads .ps1 as ANSI without a BOM.
#>

$ErrorActionPreference = 'Stop'
$Port = 7000
$RuleName = 'MiaoWang gateway 7000'

# ---------- self-elevate ----------
$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Host '[i] Elevation required - relaunching as administrator. Accept the UAC prompt.'
    Start-Process -FilePath 'powershell.exe' -Verb RunAs -ArgumentList @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $PSCommandPath
    )
    exit
}

Write-Host ''
Write-Host '=== 1. Who is listening on the gateway port ==='
$listen = netstat -ano | Select-String -Pattern (':' + $Port + '\s')
if ($listen) {
    $listen | Select-Object -First 3 | ForEach-Object { Write-Host ('   ' + $_.Line.Trim()) }
} else {
    Write-Host ('   [!] Nothing is listening on port ' + $Port + ' - start the api-gateway first.')
}

Write-Host ''
Write-Host '=== 2. Gateway executable (this is what the firewall matches on) ==='
$pids = netstat -ano |
    Select-String -Pattern (':' + $Port + '\s+.*LISTENING') |
    ForEach-Object { ($_.Line.Trim() -split '\s+')[-1] } |
    Select-Object -Unique
foreach ($p in $pids) {
    try {
        $proc = Get-Process -Id ([int]$p)
        Write-Host ('   PID ' + $p + '   ' + $proc.ProcessName + '   ' + $proc.Path)
    } catch {
        Write-Host ('   PID ' + $p + '   (cannot read path)')
    }
}

Write-Host ''
Write-Host '=== 3. Existing Java inbound allow rules (compare Program with section 2) ==='
netsh advfirewall firewall show rule name="Java(TM) Platform SE binary" dir=in verbose |
    Select-String -Pattern 'Program:|Profiles:|Protocol:|Action:' |
    ForEach-Object { Write-Host ('   ' + $_.Line.Trim()) }

Write-Host ''
Write-Host '=== 4. Adding the inbound rule (idempotent) ==='
netsh advfirewall firewall delete rule name="$RuleName" | Out-Null
netsh advfirewall firewall add rule name="$RuleName" dir=in action=allow protocol=TCP `
    localport=$Port remoteip=LocalSubnet profile=any enable=yes |
    ForEach-Object { Write-Host ('   ' + $_) }

Write-Host ''
Write-Host '=== 5. Verify ==='
netsh advfirewall firewall show rule name="$RuleName"

Write-Host ''
Write-Host '=== 6. Self-check URL - open it in the PHONE browser, it must return JSON ==='
$ip = $null
try {
    $ip = (Get-NetIPAddress -AddressFamily IPv4 -ErrorAction Stop |
        Where-Object { $_.InterfaceAlias -match 'WLAN|Wi-Fi|WiFi' -and $_.IPAddress -notlike '169.254.*' } |
        Select-Object -First 1).IPAddress
} catch { }
if (-not $ip) { $ip = '192.168.225.183' }
Write-Host ''
Write-Host ('   http://' + $ip + ':' + $Port + '/miaowang-user/mapleleaf/miaowang/v1/user/captcha')
Write-Host ''
Write-Host 'Then in MiaoWang-phone/utils/request.js make sure BASE uses this same IP.'
Write-Host 'If the phone browser still cannot open it:'
Write-Host '   - phone and PC must be on the SAME Wi-Fi; guest networks / AP isolation block device-to-device'
Write-Host '   - turn off VPN or proxy on the phone'
Write-Host '   - gateway must be running on the PC (it is a java process on port ' + $Port + ')'
Write-Host ''
Read-Host 'Press Enter to close'