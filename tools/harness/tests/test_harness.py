"""외부 계정 없이 임시 저장소에서 성공과 거부를 검증한다."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'tools/harness'))
import cli
from policy import SECTIONS, message, pr, push_refs

BODY = '\n\n'.join('## ' + section + '\n\n하네스 검증 결과를 기록했으며 서비스는 미구현입니다.' for section in SECTIONS)


class PolicyTests(unittest.TestCase):
    def test_messages_accept(self):
        for title in ['feat: 식재료 원산지 조합 검색을 구현', 'fix!: JWT 갱신 경쟁 조건을 수정', 'docs: PostGIS 실행 방법을 정리', 'revert: 원산지 필터 변경을 되돌림', 'docs: Apple Silicon 실행 방법을 기록']:
            with self.subTest(title=title): message(title + '\n\n세션 경합 처리 결과를 기록했습니다.')

    def test_messages_reject(self):
        for text in ['', 'feat: implement search', 'feat: 한글 implement all the search filters now', 'fix: 수정', 'Merge pull request #1', 'Revert "test"', 'feat: 검색 기능을 구현\n\nThis changes all behavior.', 'feat: 원산지 검색 기능을 구현\n본문 구분을 누락']:
            with self.subTest(text=text), self.assertRaises(ValueError): message(text)

    def test_pr(self):
        pr('chore: 개발 하네스 검사 도구를 구성', BODY, 'dev', 'chore/bootstrap-harness')
        pr('release: 서비스 첫 릴리스 변경을 반영', BODY, 'main', 'dev')
        for base, head, same in [('main', 'feat/search', True), ('main', 'dev', False), ('main', 'main', True), ('dev', 'main', True)]:
            with self.subTest(base=base, head=head), self.assertRaises(ValueError): pr('release: 서비스 변경 사항을 반영', BODY, base, head, same)

    def test_empty_and_template_pr(self):
        for body in ['', '## 변경 목적\n\n', (ROOT / '.github/pull_request_template.md').read_text()]:
            with self.subTest(body=body), self.assertRaises(ValueError): pr('docs: 실행 방법과 검증 결과를 기록', body, 'dev', 'docs/setup')

    def test_push_refs(self):
        sha = 'a' * 40
        for target in ['main', 'dev']:
            for local in ['HEAD', '(delete)', 'refs/heads/feat/search']:
                with self.subTest(target=target, local=local), self.assertRaises(ValueError):
                    push_refs(f'{local} {sha} refs/heads/{target} {sha}\n')
        push_refs(f'HEAD {sha} refs/heads/feat/search {sha}\n')
        with self.assertRaises(ValueError): push_refs('malformed')

    def test_rules_do_not_require_ci_or_approvers(self):
        for branch in ['dev', 'main']:
            rules = cli.rules_payload(branch, 0)
            self.assertEqual(rules['bypass_actors'], [])
            self.assertNotIn('required_linear_history', [x['type'] for x in rules['rules']])
            params = rules['rules'][2]['parameters']
            self.assertEqual(params['required_approving_review_count'], 0)
            self.assertEqual(params['allowed_merge_methods'], ['merge' if branch == 'main' else 'squash'])

    def test_service_missing_fails(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(cli, 'ROOT', Path(folder)):
            target = Path(folder) / 'tools/harness'
            target.mkdir(parents=True)
            shutil.copy(ROOT / 'tools/harness/commands.json', target)
            with self.assertRaisesRegex(ValueError, '미구현'): cli.service('test-backend')

    def test_env_preserved(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(cli, 'ROOT', Path(folder)):
            cli.env_init()
            path = Path(folder) / '.local/infra.env'
            data = path.read_bytes()
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)
            cli.env_init()
            self.assertEqual(data, path.read_bytes())
            self.assertFalse((Path(folder) / '.env').exists())

    def test_cleanup_rejects_unmerged_and_changed_heads(self):
        base = {'state': 'MERGED', 'baseRefName': 'dev', 'headRefName': 'feat/search', 'headRefOid': 'abc', 'isCrossRepository': False}
        from argparse import Namespace
        for field, value in [('state', 'OPEN'), ('headRefOid', 'old'), ('isCrossRepository', True), ('baseRefName', 'main')]:
            with self.subTest(field=field), patch.object(cli, 'clean'), patch.object(cli, 'current', return_value='chore/other'), patch.object(cli, 'repo_name', return_value='sample/repo'), patch.object(cli, 'gh_json', return_value={**base, field: value}), patch.object(cli, 'git', return_value='abc'), patch.object(cli, 'run') as run:
                with self.assertRaises(ValueError): cli.cleanup(Namespace(branch='feat/search', pr=1, apply=True))
                run.assert_not_called()

    def test_cleanup_dry_run_and_verified_apply(self):
        from argparse import Namespace
        data = {'state': 'MERGED', 'baseRefName': 'dev', 'headRefName': 'feat/search', 'headRefOid': 'abc', 'isCrossRepository': False}
        for apply in [False, True]:
            with self.subTest(apply=apply), patch.object(cli, 'clean'), patch.object(cli, 'current', return_value='chore/other'), patch.object(cli, 'repo_name', return_value='sample/repo'), patch.object(cli, 'gh_json', return_value=data), patch.object(cli, 'git', side_effect=['abc', 'abc refs/heads/feat/search']), patch.object(cli, 'run') as run:
                cli.cleanup(Namespace(branch='feat/search', pr=1, apply=apply))
                self.assertEqual(run.call_count, 2 if apply else 0)


    def test_protection_dry_run_never_writes(self):
        with patch.object(cli, 'repo_name', return_value='sample/repo'), patch.object(cli, 'gh_json', side_effect=[{'permissions': {'admin': True}}, []]), patch.object(cli, 'run') as run:
            cli.protection()
            run.assert_not_called()

    def test_protection_conflicting_rules_preserved(self):
        with patch.object(cli, 'repo_name', return_value='sample/repo'), patch.object(cli, 'gh_json', side_effect=[{'permissions': {'admin': True}}, [{'name': 'kimchimap-main'}], {}, {}]), patch.object(cli, 'run') as run:
            with self.assertRaisesRegex(ValueError, '같은 이름'):
                cli.protection(apply=True)
            run.assert_not_called()

    def test_protection_apply_requeries(self):
        main = cli.rules_payload('main', 0)
        dev = cli.rules_payload('dev', 0)
        with patch.object(cli, 'repo_name', return_value='sample/repo'), patch.object(cli, 'gh_json', side_effect=[{'permissions': {'admin': True}}, [], {}, {}, main, dev]), patch.object(cli, 'run', side_effect=['{"id": 1}', '{"id": 2}']) as run:
            cli.protection(apply=True)
            self.assertEqual(run.call_count, 2)
            self.assertTrue(all('POST' in call.args[0] for call in run.call_args_list))

    def test_env_symlink_rejected(self):
        with tempfile.TemporaryDirectory() as folder, tempfile.TemporaryDirectory() as other, patch.object(cli, 'ROOT', Path(folder)):
            (Path(folder) / '.local').symlink_to(other)
            with self.assertRaises(ValueError): cli.env_init()


    def test_child_failure_is_not_success(self):
        with self.assertRaisesRegex(ValueError, 'exit 7'):
            cli.run([sys.executable, '-c', 'raise SystemExit(7)'])

    def test_rules_snapshot_matches_policy(self):
        data = json.loads((ROOT / '.github/protection-plan.json').read_text())
        self.assertEqual(data['rulesets'], [cli.rules_payload('main', 0), cli.rules_payload('dev', 0)])


class GitHookTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / 'repo'
        self.path.mkdir()
        for name in ['tools', 'scripts', 'docs', 'frontend', 'backend', '.github']:
            shutil.copytree(ROOT / name, self.path / name, ignore=shutil.ignore_patterns('__pycache__'))
        for name in ['AGENTS.md', 'README.md', 'CONTRIBUTING.md', 'SECURITY.md', 'pnpm-lock.yaml', '.gitignore']:
            shutil.copy(ROOT / name, self.path / name)
        self.env = {**os.environ, 'GIT_CONFIG_NOSYSTEM': '1', 'GIT_CONFIG_GLOBAL': os.devnull, 'GIT_TERMINAL_PROMPT': '0'}
        self.call('git', 'init', '-b', 'main')
        self.call('git', 'config', 'user.name', '하네스 테스트')
        self.call('git', 'config', 'user.email', 'test@example.invalid')
        self.call('git', 'add', '.')
        self.call('git', 'commit', '-m', 'chore: 테스트 기준 저장소를 생성')
        self.call('git', 'branch', 'dev')
        self.remote = Path(self.temp.name) / 'remote.git'
        self.call('git', 'init', '--bare', str(self.remote))
        self.call('git', 'remote', 'add', 'origin', str(self.remote))
        self.call('git', 'push', 'origin', 'main', 'dev')
        self.call('./scripts/harness', 'hooks-install')

    def call(self, *args, ok=True, input=None):
        result = subprocess.run(args, cwd=self.path, env=self.env, input=input, text=True, capture_output=True)
        if ok:
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        else:
            self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        return result

    def change(self):
        (self.path / 'change.txt').write_text('테스트 변경\n')
        self.call('git', 'add', 'change.txt')

    def test_protected_commit_rejected(self):
        self.change()
        self.call('git', 'commit', '-m', 'chore: 보호 브랜치 커밋을 검사', ok=False)
        self.call('git', 'switch', 'dev')
        self.call('git', 'commit', '-m', 'chore: 보호 브랜치 커밋을 검사', ok=False)

    def test_work_commit_and_invalid_message(self):
        self.call('./scripts/harness', 'branch-start', 'feat/search')
        self.change()
        self.call('git', 'commit', '-m', 'feat: implement search 한글', ok=False)
        self.call('git', 'commit', '-m', 'feat: 원산지 검색 검증 자료를 추가')
        self.call('git', 'push', 'origin', 'HEAD:refs/heads/feat/search')

    def test_actual_target_and_deletion_push_rejected(self):
        self.call('./scripts/harness', 'branch-start', 'feat/search')
        self.change()
        self.call('git', 'commit', '-m', 'feat: 원산지 검색 검증 자료를 추가')
        for target in ['main', 'dev']:
            self.call('git', 'push', 'origin', 'HEAD:refs/heads/' + target, ok=False)
            self.call('git', 'push', 'origin', ':refs/heads/' + target, ok=False)
            self.call('git', 'push', '--force', 'origin', 'HEAD:refs/heads/' + target, ok=False)

    def test_branch_start_preserves_dirty_changes(self):
        self.change()
        self.call('./scripts/harness', 'branch-start', 'feat/search', ok=False)
        self.assertEqual(self.call('git', 'branch', '--show-current').stdout.strip(), 'main')

    def test_pr_cli(self):
        body = Path(self.temp.name) / 'body.md'
        body.write_text(BODY)
        self.call('./scripts/harness', 'pr-check', '--title', 'docs: 개발 검증 방법을 기록', '--body-file', str(body), '--head', 'docs/setup')
        self.call('./scripts/harness', 'pr-check', '--title', 'docs: 개발 검증 방법을 기록', '--body-file', str(body), '--base', 'main', '--head', 'docs/setup', ok=False)


    def test_existing_hook_config_preserved(self):
        self.call('git', 'config', 'core.hooksPath', 'custom-hooks')
        self.call('./scripts/harness', 'hooks-install', ok=False)
        self.assertEqual(self.call('git', 'config', '--get', 'core.hooksPath').stdout.strip(), 'custom-hooks')

    def test_forced_secret_stage_rejected(self):
        self.call('./scripts/harness', 'branch-start', 'chore/secret-check')
        (self.path / '.env').write_text('TEST_VALUE=synthetic\n')
        self.call('git', 'add', '-f', '.env')
        self.call('git', 'commit', '-m', 'test: 비밀 설정 차단 동작을 확인', ok=False)

    def test_missing_origin_dev_fails(self):
        subprocess.run(['git', '--git-dir', str(self.remote), 'update-ref', '-d', 'refs/heads/dev'], check=True, env=self.env)
        self.call('./scripts/harness', 'branch-start', 'feat/search', ok=False)
        self.assertEqual(self.call('git', 'branch', '--show-current').stdout.strip(), 'main')


if __name__ == '__main__':
    unittest.main()
