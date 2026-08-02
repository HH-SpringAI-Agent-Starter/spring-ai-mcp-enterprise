# -*- coding: utf-8 -*-
"""通过 GitHub Git Data API 推送本地 commit（绕过 git 443 阻断）"""
import subprocess, json, base64, urllib.request, urllib.error

REPO = r'D:\javaproject\spring-ai-mcp-enterprise'
import os
TOKEN = os.environ.get('GITHUB_TOKEN', '')
if not TOKEN:
    raise SystemExit('GITHUB_TOKEN env var required')
OWNER = 'HH-SpringAI-Agent-Starter'
REPO_NAME = 'spring-ai-mcp-enterprise'
API = f'https://api.github.com/repos/{OWNER}/{REPO_NAME}'

headers = {
    'Authorization': f'Bearer {TOKEN}',
    'Accept': 'application/vnd.github+json',
    'User-Agent': 'MCP-Enterprise-Push'
}

def api_call(method, path, data=None):
    url = API + path
    req = urllib.request.Request(url, headers=headers, method=method)
    if data:
        req.data = json.dumps(data).encode('utf-8')
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return json.loads(resp.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        body = e.read().decode('utf-8')
        print(f'HTTP {e.code}: {body[:200]}')
        raise

# 1. 获取远程 main 最新 commit SHA
print('Step 1: 获取远程 main 最新 commit...')
refs = api_call('GET', '/git/refs/heads/main')
remote_sha = refs['object']['sha']
print(f'  远程 main SHA: {remote_sha}')

# 2. 获取本地 HEAD commit SHA
print('Step 2: 获取本地 HEAD...')
r = subprocess.run(['git', '-C', REPO, 'rev-parse', 'HEAD'], capture_output=True, text=True, encoding='utf-8', errors='replace')
local_sha = r.stdout.strip()
print(f'  本地 HEAD: {local_sha}')

if local_sha == remote_sha:
    print('✅ 本地已同步，无需推送')
    exit(0)

# 3. 获取本地所有未推送 commit 列表
print('Step 3: 获取未推送 commit 列表...')
r = subprocess.run(['git', '-C', REPO, 'log', '--oneline', f'{remote_sha}..HEAD'],
                   capture_output=True, text=True, encoding='utf-8', errors='replace')
commits = [line.split(maxsplit=1) for line in r.stdout.strip().splitlines() if line.strip()]
print(f'  未推送 commit: {len(commits)} 个')
for sha, msg in commits:
    print(f'    {sha} {msg}')

# 4. 推送需要同步所有对象。由于 commit 较多，简单方案：创建新 branch + PR
# 或直接用 /git/refs/heads/main 强制更新（需 force push）
# 这里安全起见：创建一个临时 branch 推送，然后提示用户手动 PR

print('Step 4: 创建临时 branch 并推送...')
branch_name = f'v1.0-production-docs-{local_sha[:7]}'
# 创建本地分支
r = subprocess.run(['git', '-C', REPO, 'branch', branch_name], capture_output=True, text=True, errors='replace')
# 获取该分支 SHA
r = subprocess.run(['git', '-C', REPO, 'rev-parse', branch_name], capture_output=True, text=True, encoding='utf-8', errors='replace')
branch_sha = r.stdout.strip()
print(f'  本地分支 {branch_name} SHA: {branch_sha}')

# 创建远程 ref
print('Step 5: 创建远程 ref...')
try:
    result = api_call('POST', '/git/refs', {
        'ref': f'refs/heads/{branch_name}',
        'sha': branch_sha
    })
    print(f'✅ 远程分支已创建: {branch_name}')
    print(f'   PR 链接: https://github.com/{OWNER}/{REPO_NAME}/compare/main...{branch_name}')
except Exception as e:
    # 如果 branch 已存在，尝试更新
    print(f'  分支已存在，尝试更新...')
    try:
        result = api_call('PATCH', f'/git/refs/heads/{branch_name}', {'sha': branch_sha, 'force': True})
        print(f'✅ 远程分支已更新: {branch_name}')
        print(f'   PR 链接: https://github.com/{OWNER}/{REPO_NAME}/compare/main...{branch_name}')
    except Exception as e2:
        print(f'❌ 推送失败: {e2}')
