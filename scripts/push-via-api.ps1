# Push via GitHub Git Data API (fallback when git protocol blocked)
# Usage: $env:GITHUB_TOKEN='xxx'; powershell -File scripts\push-via-api.ps1
$ErrorActionPreference = 'Stop'
$token = $env:GITHUB_TOKEN
if (-not $token) { throw 'GITHUB_TOKEN env var required' }
$repo = 'HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise'
$headers = @{ Authorization = "Bearer $token"; Accept = 'application/vnd.github+json' }
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# Base = remote main ref; compute changed files vs HEAD from local git
$ref = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/git/refs/heads/main" -Headers $headers -TimeoutSec 30
$baseSha = $ref.object.sha
Write-Host "remote main: $baseSha"
$baseCommit = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/git/commits/$baseSha" -Headers $headers -TimeoutSec 30
$baseTree = $baseCommit.tree.sha
Write-Host "base tree: $baseTree"

# Local HEAD must be ahead of remote base
$headSha = (git rev-parse HEAD).Trim()
Write-Host "local HEAD: $headSha"
if ($baseSha -eq $headSha) { Write-Host 'Already up to date'; exit 0 }

# 2. Changed files between base and HEAD (only tracked changes in commits)
$files = git diff --name-only "$baseSha..HEAD" --diff-filter=ACMR
$files = $files | Where-Object { $_ -ne '' }
Write-Host "files to upload: $($files.Count)"
if ($files.Count -eq 0) { throw 'No changed files found in commits' }

# 3. Create blobs
$items = @()
foreach ($f in $files) {
    $full = Join-Path $root ($f.Replace('/','\'))
    if (-not (Test-Path $full)) { Write-Host "SKIP missing: $f"; continue }
    $raw = [System.IO.File]::ReadAllBytes($full)
    $b64 = [Convert]::ToBase64String($raw)
    $blobBody = @{ content = $b64; encoding = 'base64' } | ConvertTo-Json -Compress
    $blob = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/git/blobs" -Method Post -Headers $headers -Body $blobBody -ContentType 'application/json' -TimeoutSec 60
    $items += @{ path = $f; mode = '100644'; type = 'blob'; sha = $blob.sha }
    Write-Host "  blob: $f -> $($blob.sha.Substring(0,7))"
}

# 4. Create tree
$treeBody = @{ base_tree = $baseTree; tree = $items } | ConvertTo-Json -Depth 10 -Compress
$tree = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/git/trees" -Method Post -Headers $headers -Body $treeBody -ContentType 'application/json' -TimeoutSec 60
Write-Host "new tree: $($tree.sha)"

# 5. Create commit
$commitBody = @{
    message = (git log -1 --format=%s)
    tree = $tree.sha
    parents = @($baseSha)
} | ConvertTo-Json -Depth 5 -Compress
$commit = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/git/commits" -Method Post -Headers $headers -Body $commitBody -ContentType 'application/json' -TimeoutSec 60
Write-Host "new commit: $($commit.sha)"

# 6. Update ref
$updateBody = @{ sha = $commit.sha; force = $false } | ConvertTo-Json -Compress
$updated = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/git/refs/heads/main" -Method Patch -Headers $headers -Body $updateBody -ContentType 'application/json' -TimeoutSec 30
Write-Host "PUSH OK: main -> $($updated.object.sha)"
