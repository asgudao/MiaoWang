param(
    [Parameter(Mandatory=$true)][string]$Path,
    [Parameter(Mandatory=$true)][string]$OutFile
)
Add-Type -AssemblyName System.IO.Compression.FileSystem

$tmp = Join-Path $env:TEMP ("xlsx-" + [guid]::NewGuid().ToString("N"))
[System.IO.Compression.ZipFile]::ExtractToDirectory($Path, $tmp)

$sb = New-Object System.Text.StringBuilder

# shared strings
$ssPath = Join-Path $tmp "xl/sharedStrings.xml"
$shared = @()
if (Test-Path $ssPath) {
    $doc = New-Object System.Xml.XmlDocument
    $doc.Load($ssPath)
    foreach ($si in $doc.SelectNodes("//*[local-name()='si']")) {
        $text = ""
        foreach ($t in $si.SelectNodes(".//*[local-name()='t']")) { $text += $t.InnerText }
        $shared += ,$text
    }
}

# workbook sheet names
$wbPath = Join-Path $tmp "xl/workbook.xml"
$sheetNames = @()
if (Test-Path $wbPath) {
    $doc = New-Object System.Xml.XmlDocument
    $doc.Load($wbPath)
    foreach ($s in $doc.SelectNodes("//*[local-name()='sheet']")) {
        $sheetNames += $s.GetAttribute("name")
    }
}

$wsDir = Join-Path $tmp "xl/worksheets"
$idx = 0
foreach ($f in (Get-ChildItem $wsDir -Filter "sheet*.xml" | Sort-Object { [int]([regex]::Match($_.Name,'\d+').Value) })) {
    $idx++
    $name = if ($idx -le $sheetNames.Count) { $sheetNames[$idx-1] } else { $f.BaseName }
    [void]$sb.AppendLine("===== Sheet $idx : $name =====")
    $doc = New-Object System.Xml.XmlDocument
    $doc.Load($f.FullName)
    foreach ($row in $doc.SelectNodes("//*[local-name()='row']")) {
        $cells = @()
        foreach ($c in $row.SelectNodes(".//*[local-name()='c']")) {
            $ref = $c.GetAttribute("r")
            $type = $c.GetAttribute("t")
            $col = ($ref -replace '[0-9]+','')
            $v = $c.SelectSingleNode("./*[local-name()='v']")
            $val = ""
            if ($type -eq "s" -and $v) { $val = $shared[[int]$v.InnerText] }
            elseif ($type -eq "inlineStr") { $is = $c.SelectSingleNode("./*[local-name()='is']"); if ($is) { $val = $is.InnerText } }
            elseif ($v) { $val = $v.InnerText }
            $cells += "$col=$val"
        }
        [void]$sb.AppendLine(("Row " + $row.GetAttribute("r") + ": " + ($cells -join " | ")))
    }
}

[System.IO.File]::WriteAllText($OutFile, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Remove-Item -Recurse -Force $tmp
Write-Output "done -> $OutFile"