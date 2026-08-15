$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
chcp 65001 | Out-Null
cd D:\javaproject\spring-ai-mcp-enterprise

$token = '$env:GITHUB_TOKEN'
$repo = 'HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise'
$headers = @{ Authorization = "Bearer $token"; Accept = 'application/vnd.github+json' }

$baseCommitSha = '8c64d85ef13073f38c9d95ff95fcb5e5b0f73a43'
Write-Host "remote main commit: $baseCommitSha"
$baseCommit = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/git/commits/$baseCommitSha" -Headers $headers -TimeoutSec 30
$baseTree = $baseCommit.tree.sha
Write-Host "remote main tree: $baseTree"

$headSha = (git rev-parse HEAD).Trim()
Write-Host "local HEAD: $headSha"

$files = git diff --name-only "$baseCommitSha..HEAD" --diff-filter=ACMR 2>$null
if ($LASTEXITCODE -ne 0 -or -not $files) {
    Write-Host "remote base not in local repo, fallback to HEAD~1..HEAD"
    $files = git diff --name-only "HEAD~1..HEAD" --diff-filter=ACMR
}
$files = $files | Where-Object { $_ -ne '' }
Write-Host "files to upload: $($files.Count)"
if ($files.Count -eq 0) { throw 'No changed files found in commits' }

$items = @()
foreach ($f in $files) {
    $full = Join-Path (Get-Location) ($f.Replace('/','\'))
    if (-not (Test-Path $full)) { Write-Host "SKIP missing: $f"; continue }
    $raw = [System.IO.File]::ReadAllBytes($full)
    $b64 = [Convert]::ToBase64String($raw)
    $blobBody = @{ content = $b64; encoding = 'base64' } | ConvertTo-Json -Compress
    $blob = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/git/blobs" -Method Post -Headers $headers -Body $blobBody -ContentType 'application/json' -TimeoutSec 60
    $items += @{ path = $f; mode = '100644'; type = 'blob'; sha = $blob.sha }
    Write-Host "  blob: $f -> $($blob.sha.Substring(0,7))"
}

$treeBody = @{ base_tree = $baseTree; tree = $items } | ConvertTo-Json -Depth 10 -Compress
$tree = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/git/trees" -Method Post -Headers $headers -Body $treeBody -ContentType 'application/json' -TimeoutSec 60
Write-Host "new tree: $($tree.sha)"

$message = (git log -1 --format=%s).Trim()
Write-Host "commit message: $message"
$commitBody = @{
    message = $message
    tree = $tree.sha
    parents = @($baseCommitSha)
} | ConvertTo-Json -Depth 5 -Compress
$utf8Body = [System.Text.Encoding]::UTF8.GetBytes($commitBody)
$commit = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/git/commits" -Method Post -Headers $headers -Body $utf8Body -ContentType 'application/json; charset=utf-8' -TimeoutSec 60
Write-Host "new commit: $($commit.sha)"

$updateBody = @{ sha = $commit.sha; force = $false } | ConvertTo-Json -Compress
$utf8Update = [System.Text.Encoding]::UTF8.GetBytes($updateBody)
$updated = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/git/refs/heads/main" -Method Patch -Headers $headers -Body $utf8Update -ContentType 'application/json; charset=utf-8' -TimeoutSec 30
Write-Host "PUSH OK: main -> $($updated.object.sha)"
