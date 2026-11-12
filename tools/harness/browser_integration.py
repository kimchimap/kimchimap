"""화면별 임시 DB와 Redis로 로그인 요청 제한과 실제 서비스 경계를 보존한다."""
from pathlib import Path
import subprocess
from runtime import configure

ROOT = Path(__file__).resolve().parents[2]


def main():
    configure()
    for project in ('desktop', 'mobile'):
        subprocess.run(
            ['pnpm', '--dir', 'frontend', 'exec', 'playwright', 'test',
             '--config', 'playwright.integration.config.ts', '--project', project],
            cwd=ROOT, check=True,
        )


if __name__ == '__main__':
    main()
