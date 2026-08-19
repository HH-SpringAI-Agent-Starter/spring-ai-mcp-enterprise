# -*- coding: utf-8 -*-
"""GitHub Git Data API 全量推送（修复版）：blob -> tree -> commit -> update ref
用法: $env:GITHUB_TOKEN='xxx'; python scripts/push-via-api-fixed.py
"""
import os, json, subprocess, urllib.request, urllib.error, base64, sys

REPO_DIR = r'D:\javaproject\spring-ai-mcp-enterprise'
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
        with urllib.request.urlopen(req, timeout=90) as resp:
            body = resp.read().decode('utf-8')
            return json.loads(body) if body else {}
    except urllib.error.HTTPError as e:
        detail = e.read().decode('utf-8', errors='replace')
        print(f'HTTP {e.code}: {detail[:500]}')
        raise

def git(args):
    r = subprocess.run(['git', '-C', REPO_DIR] + args, capture_output=True, text=True,
                       encoding='utf-8', errors='replace')
    return r

def main():
    # 1. remote main sha
    ref = api('GET', '/git/refs/heads/main')
    base_sha = ref['object']['sha']
    print(f'remote main: {base_sha}')

    local_sha = git(['rev-parse', 'HEAD']).stdout.strip()
    print(f'local HEAD:  {local_sha}')
    if local_sha == base_sha:
        print('already up to date')
        return

    # 2. changed files between base..HEAD (fallback HEAD~2..HEAD if base not local)
    r = git(['merge-base', '--is-ancestor', base_sha, local_sha])
    if r.returncode == 0:
        r = git(['diff', '--name-only', f'{base_sha}..{local_sha}', '--diff-filter=ACMR'])
    else:
        print('remote base not in local repo, fallback HEAD~3..HEAD')
        r = git(['diff', '--name-only', 'HEAD~3..HEAD', '--diff-filter=ACMR'])
    files = [f for f in r.stdout.splitlines() if f.strip()]
    print(f'files to upload: {len(files)}')

    # 3. create blobs
    items = []
    for f in files:
        full = os.path.join(REPO_DIR, f.replace('/', os.sep))
        if not os.path.exists(full):
            print(f'SKIP missing: {f}')
            continue
        with open(full, 'rb') as fh:
            b64 = base64.b64encode(fh.read()).decode('ascii')
        blob = api('POST', '/git/blobs', {'content': b64, 'encoding': 'base64'})
        items.append({'path': f, 'mode': '100644', 'type': 'blob', 'sha': blob['sha']})
        print(f'  blob: {f} -> {blob["sha"][:7]}')

    # 4. create tree
    base_tree = api('GET', f'/git/commits/{base_sha}')['tree']['sha']
    tree = api('POST', '/git/trees', {'base_tree': base_tree, 'tree': items})
    print(f'new tree: {tree["sha"]}')

    # 5. create commit (message from git log, UTF-8 safe in python)
    msg = git(['log', '-1', '--format=%s', local_sha]).stdout.strip()
    if not msg:
        msg = 'feat: update via API'
    print(f'commit message: {msg}')
    commit = api('POST', '/git/commits', {
        'message': msg,
        'tree': tree['sha'],
        'parents': [base_sha]
    })
    print(f'new commit: {commit["sha"]}')

    # 6. update ref
    updated = api('PATCH', '/git/refs/heads/main', {'sha': commit['sha'], 'force': False})
    print(f'PUSH OK: main -> {updated["object"]["sha"]}')

if __name__ == '__main__':
    main()
