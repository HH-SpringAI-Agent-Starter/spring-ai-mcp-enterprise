# Bump version 0.15.0-SNAPSHOT -> 0.16.0-SNAPSHOT across all pom.xml
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$poms = Get-ChildItem -Recurse -Filter "pom.xml" | Where-Object { $_.FullName -notmatch '\\target\\' }
$changed = @()
foreach ($p in $poms) {
    $bytes = [System.IO.File]::ReadAllBytes($p.FullName)
    $text = [System.Text.Encoding]::UTF8.GetString($bytes)
    $new = $text.Replace('<version>0.15.0-SNAPSHOT</version>', '<version>0.16.0-SNAPSHOT</version>')
    if ($new -ne $text) {
        $out = [System.Text.Encoding]::UTF8.GetBytes($new)
        [System.IO.File]::WriteAllBytes($p.FullName, $out)
        $changed += $p.FullName.Replace($root + '\', '')
    }
}
Write-Host "CHANGED: $($changed.Count)"
$changed | ForEach-Object { Write-Host "  $_" }
