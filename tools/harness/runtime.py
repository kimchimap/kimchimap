"""시스템 설정을 바꾸지 않고 고정된 프로젝트 런타임을 선택한다."""
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
import tarfile
import tempfile
import urllib.request

CONFIG = json.loads((Path(__file__).parent / 'toolchains.json').read_text())
CACHE = Path(os.environ.get('KIMCHIMAP_TOOLCHAIN_HOME', Path.home() / '.cache/kimchimap-toolchains'))


def selected():
    architecture = {'aarch64': 'arm64', 'arm64': 'arm64', 'x86_64': 'x64', 'AMD64': 'x64'}.get(platform.machine())
    key = platform.system().lower() + '-' + str(architecture)
    if key not in CONFIG['platforms']:
        raise ValueError('지원 런타임은 macOS/Linux ARM64/AMD64입니다.')
    return CONFIG['platforms'][key]


def local_directory(root):
    result = subprocess.run(['git', 'rev-parse', '--path-format=absolute', '--git-common-dir'], cwd=root, capture_output=True, text=True)
    return (Path(result.stdout.strip()).parent if result.returncode == 0 else root) / '.local'


def load_local_environment(root):
    allowed = {'POSTGRES_PASSWORD', 'MIGRATOR_PASSWORD', 'APP_PASSWORD', 'REDIS_PASSWORD', 'KAKAO_JAVASCRIPT_KEY', 'KAKAO_CLIENT_ID', 'KAKAO_CLIENT_SECRET', 'PUBLIC_DATA_SERVICE_KEY', 'PUBLIC_ORIGIN', 'JWT_PRIVATE_KEY_PATH', 'JWT_KEY_ID', 'SEARCH_CURSOR_SECRET'}
    for name in ['infra.env', 'integrations.env']:
        path = local_directory(root) / name
        if not path.exists():
            continue
        if path.is_symlink() or path.stat().st_mode & 0o077:
            raise ValueError('로컬 비밀 설정은 일반 파일과 권한 600이 필요합니다: ' + name)
        for line in path.read_text().splitlines():
            if not line.strip() or line.lstrip().startswith('#'):
                continue
            key, separator, value = line.partition('=')
            key = key.strip()
            if not separator or key not in allowed:
                raise ValueError('허용되지 않은 로컬 설정 키: ' + name)
            os.environ.setdefault(key, value.strip().strip('"').strip("'"))


def configure():
    load_local_environment(Path(__file__).resolve().parents[2])
    config = selected()
    java_home = CACHE / config['java']['directory'] / config['java']['home']
    node_bin = CACHE / config['node']['directory'] / 'bin'
    if (java_home / 'bin/java').is_file():
        os.environ['JAVA_HOME'] = str(java_home)
        os.environ['PATH'] = str(java_home / 'bin') + os.pathsep + os.environ['PATH']
    if (node_bin / 'node').is_file():
        os.environ['PATH'] = str(node_bin) + os.pathsep + os.environ['PATH']
    pnpm_bin = CACHE / 'pnpm-12.3.4/node_modules/.bin'
    if (pnpm_bin / 'pnpm').is_file():
        os.environ['PATH'] = str(pnpm_bin) + os.pathsep + os.environ['PATH']


def install():
    CACHE.mkdir(parents=True, exist_ok=True)
    for name, spec in selected().items():
        with tempfile.TemporaryDirectory(dir=CACHE) as folder:
            folder = Path(folder)
            archive = folder / 'runtime.tar.gz'
            with urllib.request.urlopen(spec['url'], timeout=60) as response, archive.open('wb') as output:
                digest = hashlib.sha256()
                while True:
                    block = response.read(1024 * 1024)
                    if not block:
                        break
                    digest.update(block)
                    output.write(block)
            if digest.hexdigest() != spec['sha256']:
                raise ValueError(name + ' 배포 checksum 불일치')
            if (CACHE / spec['directory']).exists():
                print(name + ': 배포 checksum 확인, 기존 설치 보존')
                continue
            with tarfile.open(archive) as tar:
                for member in tar.getmembers():
                    target = (folder / member.name).resolve()
                    if folder not in target.parents or member.isdev() or member.isfifo():
                        raise ValueError('안전하지 않은 아카이브 항목')
                    if member.issym() or member.islnk():
                        link = (target.parent / member.linkname).resolve() if member.issym() else (folder / member.linkname).resolve()
                        if folder not in link.parents:
                            raise ValueError('아카이브 외부 링크 거부')
                tar.extractall(folder)
            (folder / spec['directory']).rename(CACHE / spec['directory'])
            print(name + ': checksum 확인 후 설치 완료')
    configure()
    subprocess.run(['npm', 'install', '--prefix', str(CACHE / 'pnpm-12.3.4'), 'pnpm@12.3.4', '--ignore-scripts', '--no-audit', '--no-fund'], check=True)
    subprocess.run(['node', str(CACHE / 'pnpm-12.3.4/node_modules/pnpm/install.js')], check=True)


if __name__ == '__main__':
    configure()
    if len(sys.argv) == 1:
        raise SystemExit('실행할 명령을 지정하세요.')
    raise SystemExit(subprocess.call(sys.argv[1:]))
