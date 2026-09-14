# =============================================================
# Knowledge handbook -> normalized seed SQL / summary generator
# Usage: pwsh gen-knowledge-sql.ps1
# Reads: sheet-mapping.json (UTF-8), parse-xlsx.ps1
# Outputs: MiaoWang-backproject/sql/knowledge-seed.sql
#          MiaoWang-backproject/sql/summary.md
# NOTE: This script is intentionally ASCII-only.
#       All CJK strings come from sheet-mapping.json (UTF-8 data).
# =============================================================
$ErrorActionPreference = 'Stop'

$workDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$parser  = Join-Path $workDir 'parse-xlsx.ps1'
$cfgPath = Join-Path $workDir 'sheet-mapping.json'
$outSql  = [System.IO.Path]::GetFullPath((Join-Path $workDir '..\MiaoWang-backproject\sql\knowledge-seed.sql'))
$outMd   = [System.IO.Path]::GetFullPath((Join-Path $workDir '..\MiaoWang-backproject\sql\summary.md'))

$cfg = Get-Content $cfgPath -Encoding UTF8 -Raw | ConvertFrom-Json

function Read-Sheets([string]$txtPath) {
    $sheets = @()
    $cur = $null
    foreach ($line in Get-Content $txtPath -Encoding UTF8) {
        if ($line -like '===== Sheet*') {
            if ($cur) { $sheets += , $cur }
            $name = ($line -replace '^===== Sheet \d+ : ', '') -replace ' =====$', ''
            $cur = @{ Name = $name; Rows = @() }
            continue
        }
        if ($line -like 'Row *: *') {
            $rest = ($line -replace '^Row \d+: ', '')
            $cells = @{}
            foreach ($part in ($rest -split ' \| ')) {
                $i = $part.IndexOf('=')
                if ($i -gt 0) { $cells[$part.Substring(0, $i)] = $part.Substring($i + 1) }
            }
            $cur.Rows += , $cells
        }
    }
    if ($cur) { $sheets += , $cur }
    return $sheets
}

function Escape-Sql([string]$s) {
    if ($null -eq $s) { return 'NULL' }
    $s = $s.Replace("'", "''")
    if ($s.Trim() -eq '') { return 'NULL' }
    return "'$s'"
}

function Merge-Remark {
    param([string[]]$Parts)
    $out = @()
    foreach ($p in $Parts) { if ($p -and $p.Trim()) { $out += $p.Trim() } }
    if ($out.Count -eq 0) { return $null }
    return ($out -join $cfg.remarkJoin)
}

$fragments = @()
$stat = @()

foreach ($fileCfg in $cfg.files) {
    $fileKey = [string]$fileCfg.key
    $tmpTxt = Join-Path $workDir ("tmp-" + $fileCfg.species + ".txt")
    & $parser -Path ([string]$fileCfg.path) -OutFile $tmpTxt | Out-Null

    foreach ($sheet in (Read-Sheets $tmpTxt)) {
        $sheetCfg = $fileCfg.sheets | Where-Object { $_.name -eq $sheet.Name }
        if (-not $sheetCfg) { continue }
        $count = 0
        $mode    = if ($sheetCfg.mode) { [string]$sheetCfg.mode } else { 'normal' }
        $curType = if ($sheetCfg.type)  { [string]$sheetCfg.type }  else { 'guide' }
        $cat = [string]$sheetCfg.category
        $tCol = [string]$sheetCfg.title
        $cCol = [string]$sheetCfg.content
        $rCols = @($sheetCfg.remark | Where-Object { $_ -ne $null })
        $skip = @($sheetCfg.skipTitles)
        $markers = $sheetCfg.sectionMarkers

        foreach ($row in $sheet.Rows) {
            $filled = @($row.GetEnumerator() | Where-Object { $_.Value -and $_.Value.Trim() }).Count

            if ($mode -eq 'dog-diet') {
                if ($filled -eq 1 -and $row['A']) {
                    $cellA = [string]$row['A']
                    $hit = $null
                    foreach ($m in $markers.PSObject.Properties) {
                        if ($cellA.StartsWith($m.Name)) { $hit = $m.Value; break }
                    }
                    if ($hit) { $curType = [string]$hit }
                    continue
                }
                if ($filled -lt 2) { continue }
            }
            elseif ($mode -eq 'cat-diet') {
                if ($filled -lt 2) { continue }
            }
            else {
                if ($filled -lt 2) { continue }
            }

            $title = [string]$row[$tCol]
            $content = [string]$row[$cCol]
            if (-not $title -or -not $content) { continue }
            if ($skip -contains $title) { continue }

            $remark = $null
            if ($rCols.Count -gt 0) {
                $vals = @()
                foreach ($rc in $rCols) { $vals += [string]$row[$rc] }
                $remark = Merge-Remark -Parts $vals
            }

            $tags = $null
            if ($mode -eq 'cat-diet') {
                $tagText = [string]$row['A']
                $curType = 'guide'
                foreach ($w in $cfg.warningKeywords) { if ($tagText.Contains([string]$w)) { $curType = 'warning'; break } }
                $o = $tagText.IndexOf([string]$cfg.tagOpen)
                $cc = $tagText.IndexOf([string]$cfg.tagClose)
                if ($o -ge 0 -and $cc -gt $o) { $tags = $tagText.Substring($o + 1, $cc - $o - 1).Trim() }
            }

            $fragments += , @{ species = [int]$fileCfg.species; cat = $cat; type = $curType; title = $title; content = $content; remark = $remark; tags = $tags; batch = [string]$fileCfg.batch }
            $count++
        }
        $stat += , @{ Species = [int]$fileCfg.species; Sheet = $sheet.Name; Rows = $count }
    }
    Remove-Item -Force $tmpTxt -ErrorAction SilentlyContinue
}

# ---------- seed SQL ----------
$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine('-- =============================================================')
[void]$sb.AppendLine('-- MiaoWang knowledge_fragment seed data')
[void]$sb.AppendLine('-- Generated from two handbooks (dog / cat) via sheet-mapping.json')
[void]$sb.AppendLine('-- Prerequisite: run knowledge-ddl.sql first')
[void]$sb.AppendLine('-- content_type: care_schedule=care routine (consumed by reminder module)')
[void]$sb.AppendLine('--               guide=knowledge  warning=safety warning  medical=health self-check')
[void]$sb.AppendLine('-- NOTE: medical rows need a disclaimer in the frontend; review before launch')
[void]$sb.AppendLine('-- =============================================================')
[void]$sb.AppendLine('SET NAMES utf8mb4;')
[void]$sb.AppendLine()

$chunk = 100
for ($i = 0; $i -lt $fragments.Count; $i += $chunk) {
    $end = [Math]::Min($i + $chunk, $fragments.Count)
    [void]$sb.AppendLine("-- rows $($i + 1)-$end")
    [void]$sb.AppendLine('INSERT INTO `knowledge_fragment` (`category_code`,`species`,`title`,`content`,`remark`,`content_type`,`tags`,`source`,`batch_no`,`status`,`create_time`,`update_time`,`del_flag`) VALUES')
    $rows = @()
    for ($j = $i; $j -lt $end; $j++) {
        $f = $fragments[$j]
        $rows += ("(" + (Escape-Sql $f.cat) + "," + $f.species + "," + (Escape-Sql $f.title) + "," + (Escape-Sql $f.content) + "," + (Escape-Sql $f.remark) + "," + (Escape-Sql $f.type) + "," + (Escape-Sql $f.tags) + ",'EXCEL'," + (Escape-Sql $f.batch) + ",2,NOW(),NOW(),0)")
    }
    [void]$sb.AppendLine(($rows -join ','))
    [void]$sb.AppendLine(';')
    [void]$sb.AppendLine()
}
[System.IO.File]::WriteAllText($outSql, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))

# ---------- summary markdown ----------
$md = New-Object System.Text.StringBuilder
[void]$md.AppendLine('# Knowledge base content summary')
[void]$md.AppendLine()
[void]$md.AppendLine('Normalized from the dog & cat handbooks. Total fragments: **' + $fragments.Count + '**.')
[void]$md.AppendLine()
$catNames = $cfg.categories
foreach ($sp in 1, 2) {
    $spObj = $cfg.speciesName.($sp.ToString())
    [void]$md.AppendLine("## species=$sp ($spObj)")
    [void]$md.AppendLine()
    foreach ($cat in 'diet', 'behavior', 'care', 'health') {
        $list = @($fragments | Where-Object { $_.species -eq $sp -and $_.cat -eq $cat })
        if ($list.Count -eq 0) { continue }
        $typeCounts = @{}
        foreach ($f in $list) { $k = if ($f.type) { [string]$f.type } else { '?' }; if ($typeCounts.ContainsKey($k)) { $typeCounts[$k]++ } else { $typeCounts[$k] = 1 } }
        $types = ($typeCounts.GetEnumerator() | Sort-Object Name | ForEach-Object { "$($_.Key)x$($_.Value)" }) -join ' / '
        [void]$md.AppendLine("### $($catNames.$cat) ($($list.Count) rows)  types: $($types -join ' / ')")
        [void]$md.AppendLine()
        foreach ($f in ($list | Select-Object -First 6)) {
            $t = $f.title; if ($t.Length -gt 26) { $t = $t.Substring(0, 26) + '...' }
            $c = $f.content; if ($c -and $c.Length -gt 34) { $c = $c.Substring(0, 34) + '...' }
            [void]$md.AppendLine("- ``" + $f.type + "`` **" + $t + "** - " + $c)
        }
        if ($list.Count -gt 6) { [void]$md.AppendLine("- ... and " + ($list.Count - 6) + " more rows (see seed SQL)") }
        [void]$md.AppendLine()
    }
}
[void]$md.AppendLine('## Source statistics')
[void]$md.AppendLine()
[void]$md.AppendLine('| species | sheet | rows |')
[void]$md.AppendLine('|---|---|---|')
foreach ($s in $stat) {
    [void]$md.AppendLine("| $($s.Species) | $($s.Sheet) | $($s.Rows) |")
}
[System.IO.File]::WriteAllText($outMd, $md.ToString(), (New-Object System.Text.UTF8Encoding($false)))

Write-Output ("fragments: " + $fragments.Count)
Write-Output ("sql: " + $outSql)
Write-Output ("md: " + $outMd)