<#
.SYNOPSIS
    Auto-tag MCP versions based on commit messages (V0.X pattern).
    Runs daily via cron to catch commits since last tag.
    Creates annotated tags both locally and via GitHub API.

.DESCRIPTION
    Scans commits since the latest tag. If any commit message contains
    a version marker (V0.X, V0.X.Y), creates an annotated tag.
    Uses GitHub API when git push is unavailable.

    Version format: v0.16, v0.16.1, etc.

.EXAMPLE
    .\scripts\tag-new-version.ps1
#>

$ErrorActionPreference = "Stop"
$repoDir = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
Set-Location $repoDir

Write-Host "=== MCP Auto-Tagger ==="
Write-Host "Repo: $repoDir"

# ── Config ──
$owner = "HH-SpringAI-Agent-Starter"
$repoName = "spring-ai-mcp-enterprise"
$token = [Environment]::GetEnvironmentVariable("GITHUB_TOKEN", "User")
if (-not $token) {
    # Fallback to inline token (read from remote URL)
    $remoteUrl = git remote get-url origin 2>$null
    if ($remoteUrl -match "gh[p_]\w+") {
        $token = $matches[0]
    }
}
if (-not $token) {
    Write-Error "GITHUB_TOKEN not found. Set env var or configure remote URL."
    exit 1
}

$headers = @{
    Authorization = "Bearer $token"
    Accept = "application/vnd.github+json"
}
$apiBase = "https://api.github.com/repos/$owner/$repoName"

# ── Step 1: Get latest tag ──
$latestTag = git describe --tags --abbrev=0 2>$null
if (-not $latestTag) {
    Write-Host "No tags found. Starting from initial commit."
    $latestTag = $null
} else {
    Write-Host "Latest tag: $latestTag"
}

# ── Step 2: Get commits since last tag ──
if ($latestTag) {
    $log = git log "$latestTag..HEAD" --oneline --format="%H %ai %s"
} else {
    $log = git log --oneline --format="%H %ai %s"
}

if (-not $log) {
    Write-Host "No new commits since last tag."
    exit 0
}

Write-Host "Commits since last tag:"
$log

# ── Step 3: Extract version markers ──
function Get-VersionFromMessage($msg) {
    # Match patterns like: "V0.16", "V0.16.1", "v0.16"
    if ($msg -match '(?i)\b(v\d+\.\d+(?:\.\d+)?)\b') {
        return $matches[1].ToLower()  # normalize to lowercase "v0.16"
    }
    return $null
}

$tagged = 0
$lines = $log -split "`n"
foreach ($line in $lines) {
    if (-not $line.Trim()) { continue }
    $parts = $line -split '\s+', 3
    if ($parts.Count -lt 3) { continue }
    $sha = $parts[0]
    $dateStr = $parts[1] + " " + $parts[2]
    $msg = $parts[3]

    $version = Get-VersionFromMessage $msg
    if (-not $version) { continue }

    # Check if tag already exists (locally or remote)
    $existing = git tag -l $version 2>$null
    if ($existing) {
        Write-Host "  ⏭️  Tag $version already exists locally"
        continue
    }

    Write-Host "  🏷️  Creating tag: $version → $sha ($msg)"

    # ── Create local annotated tag ──
    $fullSha = git rev-parse $sha 2>$null
    git tag -a $version -m "Release ${version}: $msg" $fullSha 2>$null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  ❌ Local tag failed for $version"
        continue
    }
    Write-Host "  ✅ Local tag created"

    # ── Push via API (since git push may fail) ──
    try {
        # Create tag object
        $tagBody = @{
            tag = $version
            message = "Release ${version}: $msg"
            object = $fullSha
            type = "commit"
            tagger = @{
                name = "QClaw Auto-Tagger"
                email = "qclaw@agent.qq.com"
                date = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")
            }
        } | ConvertTo-Json -Depth 10 -Compress

        $tagResult = Invoke-RestMethod -Uri "$apiBase/git/tags" -Method Post -Headers $headers -Body $tagBody -ContentType "application/json" -TimeoutSec 15
        $tagObjSha = $tagResult.sha

        # Create ref
        $refBody = @{
            ref = "refs/tags/$version"
            sha = $tagObjSha
        } | ConvertTo-Json -Compress

        $refResult = Invoke-RestMethod -Uri "$apiBase/git/refs" -Method Post -Headers $headers -Body $refBody -ContentType "application/json" -TimeoutSec 15
        Write-Host "  ✅ Remote tag created: $($refResult.ref)"
        $tagged++
    }
    catch {
        # Check if tag already exists on remote (409 or 422)
        if ($_.Exception.Response.StatusCode -eq 422 -or $_.Exception.Message -match "already exists") {
            Write-Host "  ⏭️  Remote tag $version already exists"
        } else {
            Write-Host "  ⚠️  Remote tag push failed for ${version}: $_"
        }
    }
}

Write-Host "=== Done: $tagged new tag(s) created ==="
