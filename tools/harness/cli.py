#!/usr/bin/env python3
"""국산김치맵 로컬 개발 하네스."""
import argparse
import ast
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys

from policy import PROTECTED, branch_name, message, pr, push_refs
from runtime import configure, install, local_directory

ROOT = Path(__file__).resolve().parents[2]


def run(args, cwd=None, capture=False, input=None):
    result = subprocess.run(args, cwd=cwd or ROOT, text=True, input=input, capture_output=capture)
    if result.returncode:
        raise ValueError('명령 실패(exit %s): %s' % (result.returncode, args[0]))
    return result.stdout.strip() if capture else ''


def git(*args):
    return run(['git', *args], capture=True)


def clean():
    if git('status', '--porcelain'):
        raise ValueError('미커밋 변경이 있습니다. 변경을 보존하고 먼저 정리하세요.')


def current():
    return git('symbolic-ref', '--quiet', '--short', 'HEAD')


def hook_check():
    actual = git('config', '--get', 'core.hooksPath')
    if actual != 'tools/harness/hooks':
        raise ValueError('훅 미설치: ./scripts/harness hooks-install')
    for name in ['pre-commit', 'commit-msg', 'pre-push']:
        if not os.access(ROOT / 'tools/harness/hooks' / name, os.X_OK):
            raise ValueError('훅 실행 권한 누락: ' + name)


def files():
    return [ROOT / p for p in git('ls-files', '--cached', '--others', '--exclude-standard').splitlines() if (ROOT / p).is_file()]


def format_check():
    errors = []
    for path in files():
        if path.suffix not in {'.py', '.json', '.md', '.yaml', '.yml', '.sh', '.sql'} and path.name not in {'harness', 'AGENTS.md', '.editorconfig', '.gitignore', '.env.example', 'pre-commit', 'pre-push', 'commit-msg'}:
            continue
        text = path.read_text()
        if not text.endswith('\n') or '\r' in text or any(line.rstrip() != line for line in text.splitlines()):
            errors.append(str(path.relative_to(ROOT)))
    if errors:
        raise ValueError('LF·마지막 개행·줄 끝 공백 확인: ' + ', '.join(errors))


def lint_harness():
    for path in files():
        rel = path.relative_to(ROOT).as_posix()
        if rel.startswith('.github/workflows/'):
            raise ValueError('CI/CD workflow 생성은 범위 밖입니다.')
        if path.suffix == '.py':
            ast.parse(path.read_text(), filename=rel)
        if path.suffix == '.json':
            json.loads(path.read_text())
        if path.suffix == '.md':
            for target in re.findall(r'\]\(([^)]+)\)', path.read_text()):
                if '://' in target or target.startswith('#'):
                    continue
                if not (path.parent / target.split('#')[0]).exists():
                    raise ValueError('문서 링크 없음: ' + rel + ' → ' + target)
    for path in ROOT.glob('.github/workflows/*'):
        raise ValueError('workflow 금지: ' + path.name)
    required = ['AGENTS.md', 'frontend/AGENTS.md', 'backend/AGENTS.md', 'docs/testing.md', 'docs/requirements.json', 'pnpm-lock.yaml']
    for path in required:
        if not (ROOT / path).is_file():
            raise ValueError('필수 파일 없음: ' + path)
    reqs = json.loads((ROOT / 'docs/requirements.json').read_text())
    ids = [r['id'] for r in reqs]
    if len(ids) != len(set(ids)) or len(ids) < 17:
        raise ValueError('요구사항 추적 목록을 확인하세요.')
    for req in reqs:
        if not req['acceptance'] or not (ROOT / req['document']).exists():
            raise ValueError('요구사항 수용 기준·문서 누락: ' + req['id'])



def staged_check():
    for name in git('diff', '--cached', '--name-only', '--diff-filter=ACM').splitlines():
        path = Path(name)
        if (path.name.startswith('.env') and path.name != '.env.example') or path.suffix in {'.pem', '.key'} or name.startswith(('.local/', '.github/workflows/')):
            raise ValueError('비밀값·금지 파일을 커밋할 수 없습니다: ' + name)
        result = subprocess.run(['git', 'show', ':' + name], cwd=ROOT, capture_output=True)
        if result.returncode:
            raise ValueError('staged 파일을 읽지 못했습니다: ' + name)
        if re.search(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|gh[pousr]_[A-Za-z0-9]{30,}', result.stdout):
            raise ValueError('비밀값 의심 내용 발견: ' + name)


def test_harness():
    run([sys.executable, '-m', 'unittest', 'discover', '-s', 'tools/harness/tests', '-v'])


def doctor():
    failed = False
    for cmd in ['git', 'python3', 'node', 'pnpm', 'java', 'docker', 'gh']:
        ok = shutil.which(cmd) is not None
        print(('[확인] ' if ok else '[누락] ') + cmd)
        failed |= not ok
    for label, cmd in [('Docker engine', ['docker', 'info', '--format', '{{.Architecture}}']), ('Java 25', ['java', '-version']), ('Node 24.20.0', ['node', '--version']), ('pnpm 12.3.4', ['pnpm', '--version'])]:
        try:
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=15)
            output = result.stdout + result.stderr
            ok = result.returncode == 0
            if label == 'Java 25':
                ok &= bool(re.search(r'version "25[.\"]', output))
            if label.startswith('Node'):
                m = re.search(r'v(\d+)\.(\d+)', output)
                ok &= output.strip() == 'v24.20.0'
            if label.startswith('pnpm'):
                ok &= output.strip() == '12.3.4'
            print(('[확인] ' if ok else '[조치 필요] ') + label)
            failed |= not ok
        except (OSError, subprocess.TimeoutExpired):
            print('[조치 필요] ' + label)
            failed = True
    try:
        hook_check()
        print('[확인] Git 훅 설치')
    except ValueError:
        print('[조치 필요] hooks-install 실행')
        failed = True
    print('[상태] 서비스 미구현 여부는 ./scripts/harness verify로 확인합니다. 비밀값은 출력하지 않습니다.')
    if failed:
        raise ValueError('환경 진단 미충족. docs/local-development.md를 확인하세요.')


def env_init():
    path = local_directory(ROOT) / 'infra.env'
    if path.parent.is_symlink() or path.is_symlink() or (path.parent / 'redis.conf').is_symlink():
        raise ValueError('로컬 비밀 설정의 심볼릭 링크를 허용하지 않습니다.')
    path.parent.mkdir(mode=0o700, exist_ok=True)
    if path.parent.stat().st_mode & 0o077:
        raise ValueError('.local 디렉터리 권한을 700으로 제한하세요.')
    if path.exists():
        if path.stat().st_mode & 0o077:
            raise ValueError('infra.env 권한을 600으로 제한하세요.')
        print('기존 로컬 설정을 보존합니다.')
        return
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w') as stream:
        for key in ['POSTGRES_PASSWORD', 'MIGRATOR_PASSWORD', 'APP_PASSWORD', 'REDIS_PASSWORD']:
            value = secrets.token_hex(32)
            stream.write(key + '=' + value + '\n')
            if key == 'REDIS_PASSWORD':
                config = path.parent / 'redis.conf'
                config.write_text('appendonly yes\nprotected-mode yes\nbind 0.0.0.0\nrequirepass ' + value + '\n')
                config.chmod(0o644)
    print('.local/infra.env 생성 완료. 값은 출력하지 않습니다. 기존 .env는 변경하지 않습니다.')


def compose(*args):
    path = local_directory(ROOT) / 'infra.env'
    if not path.exists():
        raise ValueError('env-init을 먼저 실행하세요.')
    os.environ['KIMCHIMAP_REDIS_CONFIG'] = str(local_directory(ROOT) / 'redis.conf')
    run(['docker', 'compose', '--env-file', str(path), '-f', 'infra/compose.yaml', *args])


def service(name):
    entries = json.loads((ROOT / 'tools/harness/commands.json').read_text())
    spec = entries[name]
    absent = [p for p in spec['requires'] if not (ROOT / p).is_file()]
    if absent:
        raise ValueError('서비스 미구현: ' + ', '.join(absent) + '. 연결 계약: ' + spec['plan'])
    run(spec['argv'], cwd=ROOT / spec.get('cwd', '.'))


def branch_start(name):
    branch_name(name)
    clean()
    run(['git', 'fetch', 'origin', 'dev'])
    base = git('rev-parse', 'refs/remotes/origin/dev')
    run(['git', 'switch', '--no-track', '-c', name, 'refs/remotes/origin/dev'])
    git('config', 'branch.' + name + '.kimchimapBase', base)
    print('작업 브랜치 생성. 기준 dev: ' + base + ' (시작점 기록은 완전한 증명이 아닙니다.)')


def gh_json(*args):
    return json.loads(run(['gh', *args], capture=True))


def repo_name():
    url = git('remote', 'get-url', 'origin')
    match = re.fullmatch(r'(?:https://github.com/|git@github.com:)([\w.-]+/[\w.-]+?)(?:\.git)?', url)
    if not match:
        raise ValueError('이 도구는 확인된 github.com origin만 지원합니다.')
    return match[1]


def rules_payload(branch, approvals):
    return {'name': 'kimchimap-' + branch, 'target': 'branch', 'enforcement': 'active', 'bypass_actors': [],
            'conditions': {'ref_name': {'include': ['refs/heads/' + branch], 'exclude': []}},
            'rules': [{'type': 'deletion'}, {'type': 'non_fast_forward'}, {'type': 'pull_request', 'parameters': {
                'required_approving_review_count': approvals, 'dismiss_stale_reviews_on_push': True,
                'require_code_owner_review': False, 'require_last_push_approval': False,
                'required_review_thread_resolution': True, 'allowed_merge_methods': ['merge' if branch == 'main' else 'squash']}}]}


def matches_policy(actual, expected):
    if isinstance(expected, dict):
        return isinstance(actual, dict) and all(k in actual and matches_policy(actual[k], v) for k, v in expected.items())
    if isinstance(expected, list):
        return isinstance(actual, list) and len(actual) == len(expected) and all(any(matches_policy(a, e) for a in actual) for e in expected)
    return actual == expected


def protection(apply=False, approvals=0):
    repo = repo_name()
    meta = gh_json('api', 'repos/' + repo)
    existing = gh_json('api', '--paginate', 'repos/' + repo + '/rulesets')
    plans = [rules_payload(b, approvals) for b in ['main', 'dev']]
    print(json.dumps({'repository': repo, 'admin': meta.get('permissions', {}).get('admin', False), 'existingRulesets': existing, 'plannedAdditions': plans, 'mode': 'apply' if apply else 'dry-run'}, ensure_ascii=False, indent=2))
    if not apply:
        print('미적용. classic protection·상속 규칙은 별도 protection-check로 조회하세요.')
        return
    if not meta.get('permissions', {}).get('admin'):
        raise ValueError('관리 권한이 없습니다.')
    for branch in ['main', 'dev']:
        gh_json('api', 'repos/' + repo + '/branches/' + branch)
    pending = []
    for plan in plans:
        found = [x for x in existing if x['name'] == plan['name']]
        if found:
            if len(found) != 1 or 'id' not in found[0]:
                raise ValueError('같은 이름의 규칙을 확인할 수 없습니다.')
            actual = gh_json('api', 'repos/' + repo + '/rulesets/' + str(found[0]['id']))
            if not matches_policy(actual, plan):
                raise ValueError('같은 이름의 다른 규칙은 자동 변경하지 않습니다.')
        else:
            pending.append(plan)
    for plan in pending:
        created = json.loads(run(['gh', 'api', '--method', 'POST', 'repos/' + repo + '/rulesets', '--input', '-'], capture=True, input=json.dumps(plan)))
        actual = gh_json('api', 'repos/' + repo + '/rulesets/' + str(created['id']))
        if not matches_policy(actual, plan):
            raise ValueError('적용 후 일치 검증 실패. 부분 적용 가능: 현재 설정을 재조회하세요.')
    print('추가 규칙 재조회 일치. 서버에서 PR head=dev와 한국어 의미까지 보장하지 않습니다.')


def protection_check():
    repo = repo_name()
    issues = []
    for branch in ['main', 'dev']:
        try:
            gh_json('api', 'repos/' + repo + '/branches/' + branch)
            effective = gh_json('api', '--paginate', 'repos/' + repo + '/rules/branches/' + branch)
            types = {x['type'] for x in effective}
            if not {'deletion', 'non_fast_forward', 'pull_request'} <= types:
                issues.append(branch + ': 필수 ruleset 규칙 미충족(classic 보호 별도 확인)')
            pulls = [x.get('parameters', {}) for x in effective if x['type'] == 'pull_request']
            expected_method = ['merge' if branch == 'main' else 'squash']
            if not any(x.get('required_review_thread_resolution') and x.get('allowed_merge_methods') == expected_method for x in pulls):
                issues.append(branch + ': 대화 해결/병합 방식 미충족')
            if branch == 'main' and 'required_linear_history' in types:
                issues.append('main: merge commit을 막는 linear history 규칙')
            for rule_id in {x.get('ruleset_id') for x in effective if x.get('ruleset_id')}:
                detail = gh_json('api', 'repos/' + repo + '/rulesets/' + str(rule_id))
                if detail.get('bypass_actors'):
                    issues.append(branch + ': bypass 권한 검토 필요')
            print(json.dumps({'branch': branch, 'effectiveRules': effective}, ensure_ascii=False))
        except ValueError:
            issues.append(branch + ': 브랜치/권한/API 확인 실패')
        result = subprocess.run(['gh', 'api', 'repos/' + repo + '/branches/' + branch + '/protection'], capture_output=True, text=True)
        if result.returncode == 0:
            print(json.dumps({'branch': branch, 'classicProtection': json.loads(result.stdout)}, ensure_ascii=False))
        else:
            print(branch + ': classic 보호 조회 실패/없음. 미확인을 적용 완료로 취급하지 않습니다.')
    if issues:
        raise ValueError('; '.join(issues))


def pr_prepare(args):
    clean()
    hook_check()
    head = current()
    pr(args.title, Path(args.body_file).read_text(), args.base, head)
    run(['git', 'fetch', 'origin', args.base])
    if subprocess.run(['git', 'merge-base', '--is-ancestor', 'origin/' + args.base, 'HEAD'], cwd=ROOT).returncode:
        raise ValueError('최신 base를 통합한 뒤 전체 검증을 다시 실행하세요.')
    commits = git('log', '--format=%H', 'origin/' + args.base + '..HEAD').splitlines()
    if not commits:
        raise ValueError('PR 변경 커밋이 없습니다.')
    for sha in commits:
        message(git('show', '-s', '--format=%B', sha))
    if args.unit and args.harness_only:
        raise ValueError('작업 단위와 하네스 전용 옵션을 동시에 지정할 수 없습니다.')
    if args.unit:
        verify_unit(args.unit)
    elif args.harness_only:
        changed = git('diff', '--name-only', 'origin/' + args.base + '...HEAD').splitlines()
        if any(p.startswith(('frontend/', 'backend/')) and not p.endswith('.md') for p in changed):
            raise ValueError('서비스 변경은 harness-only 검증을 사용할 수 없습니다.')
        verify_harness()
    else:
        verify()
    print('로컬 PR 준비 검증 완료. 원격 push/PR 생성/병합은 수행하지 않았습니다.')


def cleanup(args):
    branch_name(args.branch)
    clean()
    if current() == args.branch:
        raise ValueError('삭제할 브랜치에서 먼저 다른 브랜치로 이동하세요.')
    repo = repo_name()
    data = gh_json('pr', 'view', str(args.pr), '--repo', repo, '--json', 'state,baseRefName,headRefName,headRefOid,isCrossRepository,headRepository')
    local = git('rev-parse', 'refs/heads/' + args.branch)
    if data['state'] != 'MERGED' or data['baseRefName'] != 'dev' or data['headRefName'] != args.branch or data['isCrossRepository'] or data['headRefOid'] != local:
        raise ValueError('동일 저장소 dev 병합 PR과 현재 브랜치 HEAD가 일치하지 않습니다.')
    remote = git('ls-remote', '--heads', 'origin', 'refs/heads/' + args.branch).split()
    if remote and remote[0] != local:
        raise ValueError('원격에 추가 커밋이 있습니다.')
    print('병합 PR과 HEAD 확인. 삭제 대상: ' + args.branch)
    if args.apply:
        if remote:
            run(['git', 'push', '--force-with-lease=refs/heads/' + args.branch + ':' + local, 'origin', ':refs/heads/' + args.branch])
        run(['git', 'branch', '-D', args.branch])
    else:
        print('dry-run. 실제 삭제는 검토 후 --apply를 지정하세요.')


def verify_harness():
    format_check()
    lint_harness()
    test_harness()
    print('하네스 검증 통과. 서비스 테스트·실연동·운영 준비 완료를 뜻하지 않습니다.')


def verify_unit(name):
    units = json.loads((ROOT / 'tools/harness/units.json').read_text())
    if name not in units:
        raise ValueError('등록되지 않은 작업 단위입니다.')
    unit = units[name]
    if not (ROOT / unit['plan']).is_file():
        raise ValueError('작업 수용 기준 문서가 없습니다.')
    for check in unit['checks']:
        if check == 'verify-harness':
            verify_harness()
        elif check == 'doctor':
            doctor()
        else:
            service(check)
    print('작업 단위 검증 통과: ' + name)
    print('미구현 후속 기능: ' + ', '.join(unit['notImplemented']))
    print('전체 서비스 완료 판정이 아닙니다. verify의 전체 검사는 유지됩니다.')


def verify():
    failures = []
    for name in ['verify-harness', 'format-backend', 'format-frontend', 'lint', 'typecheck', 'test-backend', 'test-frontend', 'test-integration', 'test-contract', 'api-check', 'test-e2e', 'build-backend', 'build']:
        try:
            verify_harness() if name == 'verify-harness' else service(name)
        except ValueError as exc:
            failures.append(name)
            print('[실패] ' + name + ': ' + str(exc), file=sys.stderr)
    if failures:
        raise ValueError('전체 검증 실패: ' + ', '.join(failures))


def main():
    parser = argparse.ArgumentParser(description='국산김치맵 개발 하네스. 미구현은 exit 2, 성공은 exit 0.')
    sub = parser.add_subparsers(dest='command', required=True)
    basic = ['doctor', 'toolchain-install', 'env-init', 'hooks-install', 'format-check', 'lint-harness', 'test-harness', 'verify-harness', 'verify', 'infra-up', 'infra-down', 'infra-check', 'protection-check', 'pre-commit', 'pre-push']
    services = json.loads((ROOT / 'tools/harness/commands.json').read_text())
    for name in basic + list(services):
        sub.add_parser(name)
    p = sub.add_parser('commit-check'); p.add_argument('file')
    p = sub.add_parser('branch-start'); p.add_argument('name')
    for name in ['pr-check', 'pr-prepare']:
        p = sub.add_parser(name)
        p.add_argument('--title', required=True); p.add_argument('--body-file', required=True)
        p.add_argument('--base', default='dev'); p.add_argument('--head', default='chore/example')
        p.add_argument('--cross-repo', action='store_true'); p.add_argument('--harness-only', action='store_true')
        p.add_argument('--unit')
    p = sub.add_parser('protection-plan'); p.add_argument('--apply', action='store_true'); p.add_argument('--approvals', type=int, choices=range(7), default=0)
    p = sub.add_parser('verify-unit'); p.add_argument('name')
    p = sub.add_parser('branch-cleanup'); p.add_argument('branch'); p.add_argument('--pr', type=int, required=True); p.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    cmd = args.command
    if cmd in services:
        service(cmd)
    elif cmd == 'doctor': doctor()
    elif cmd == 'toolchain-install': install()
    elif cmd == 'env-init': env_init()
    elif cmd == 'format-check': format_check()
    elif cmd == 'lint-harness': lint_harness()
    elif cmd == 'test-harness': test_harness()
    elif cmd == 'verify-harness': verify_harness()
    elif cmd == 'verify': verify()
    elif cmd == 'verify-unit': verify_unit(args.name)
    elif cmd == 'hooks-install':
        existing = subprocess.run(['git', 'config', '--get', 'core.hooksPath'], cwd=ROOT, capture_output=True, text=True).stdout.strip()
        if existing and existing != 'tools/harness/hooks':
            raise ValueError('기존 hooksPath를 덮어쓰지 않습니다: 수동 통합 필요')
        run(['git', 'config', '--local', 'core.hooksPath', 'tools/harness/hooks'])
        hook_check()
    elif cmd == 'commit-check': message(Path(args.file).read_text())
    elif cmd == 'pre-commit':
        branch_name(current())
        run(['git', 'diff', '--cached', '--check'])
        lint_harness()
        staged_check()
    elif cmd == 'pre-push': push_refs(sys.stdin.read())
    elif cmd == 'branch-start': branch_start(args.name)
    elif cmd == 'pr-check': pr(args.title, Path(args.body_file).read_text(), args.base, args.head, not args.cross_repo)
    elif cmd == 'pr-prepare': pr_prepare(args)
    elif cmd == 'branch-cleanup': cleanup(args)
    elif cmd == 'protection-plan': protection(args.apply, args.approvals)
    elif cmd == 'protection-check': protection_check()
    elif cmd == 'infra-up': compose('up', '-d', '--wait', '--wait-timeout', '90')
    elif cmd == 'infra-down': compose('down')
    elif cmd == 'infra-check':
        compose('exec', '-T', 'db', 'psql', '-U', 'postgres', '-d', 'kimchimap', '-v', 'ON_ERROR_STOP=1', '-c', 'SELECT version(), PostGIS_Full_Version();')
        compose('exec', '-T', 'db', 'psql', '-U', 'postgres', '-d', 'kimchimap', '-v', 'ON_ERROR_STOP=1', '-f', '/harness/verify-roles.sql')
        compose('exec', '-T', 'redis', 'sh', '-c', 'test "$(REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli PING)" = PONG && redis-cli PING | grep -q NOAUTH')
        print('PostGIS 공간 함수·앱 역할 제한·Redis 인증 허용/거부 확인')


if __name__ == '__main__':
    try:
        configure()
        main()
    except (ValueError, OSError, json.JSONDecodeError) as exc:
        print('오류: ' + str(exc), file=sys.stderr)
        sys.exit(2)
