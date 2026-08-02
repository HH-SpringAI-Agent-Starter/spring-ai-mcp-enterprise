# -*- coding: utf-8 -*-
"""通过 GitHub Git Data API 推送本地 commits（绕过 git 443 间歇性阻断）
用法: $env:GITHUB_TOKEN='xxx'; python push-via-api.py
"""
import os, json, subprocess, urllib.request, urllib.error, base64

REPO = r'D:\javaproject\spring-ai-mcp-enterprise'
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

def api(method, path, data=None):
    url = API + path
    req = urllib.request.Request(url, headers=headers, method=method)
    if data is not None:
        req.data = json.dumps(data).encode('utf-8')
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            body = resp.read().decode('utf-8')
            return json.loads(body) if body else {}
    except urllib.error.HTTPError as e:
        body = e.read().decode('utf-8', errors='replace')
        print(f'HTTP {e.code}: {body[:300]}')
        raise

def git(cmd, cwd=REPO):
    r = subprocess.run(['git', '-C', cwd] + cmd, capture_output=True, text=True, encoding='utf-8', errors='replace')
    return r

def push_commits():
    # 1. 远程 main 最新
    print('Step 1: 获取远程 main...')
    try:
        ref = api('GET', '/git/ref/heads/main')
        remote_sha = ref['object']['sha']
    except Exception:
        remote_sha = None
    print(f'  远程: {remote_sha}')

    # 2. 本地 HEAD
    r = git(['rev-parse', 'HEAD'])
    local_sha = r.stdout.strip()
    print(f'  本地: {local_sha}')
    if local_sha == remote_sha:
        print('✅ 已同步')
        return

    # 3. 本地 HEAD commit 对象
    print('Step 2: 获取本地 commit 对象...')
    r = git(['cat-file', 'commit', local_sha])
    commit_data = r.stdout
    print(f'  commit 大小: {len(commit_data)}')

    # 4. 上传缺失的 blobs/trees/commits（从本地仓库导出 pack 通过 API 不可行，
    #    简单方案：只处理最近几个 commit 涉及的文件 —— 这里退化为检查差异并报告）
    # Git Data API 无法直接接受本地 pack，实际做法是逐个文件创建 blob。
    # 更简单：如果只是少数文件，用 Contents API 逐文件上传。

    # 5. 获取本地与远程之间的 commit 列表
    base = remote_sha or git(['rev-list', '--max-parents=0', 'HEAD']).stdout.strip()
    r = git(['log', '--oneline', f'{base}..HEAD'])
    commits = [l.split(maxsplit=1) for l in r.stdout.strip().splitlines() if l.strip()]
    print(f'Step 3: 待推送 {len(commits)} 个 commit')
    for sha, msg in commits:
        print(f'  {sha} {msg}')

    print('\n⚠️ Git Data API 全量推送复杂（需逐文件 blob），改用 Contents API 逐文件上传。')
    print('先获取差异文件列表...')
    r = git(['diff', '--name-only', base, local_sha])
    files = [l for l in r.stdout.splitlines() if l.strip()]
    print(f'  差异文件: {len(files)}')
    for f in files[:30]:
        print(f'    {f}')

if __name__ == '__main__':
    push_commits()
