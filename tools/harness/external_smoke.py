"""키를 출력하지 않고 외부 지도와 인가 진입만 확인한다."""
import os
from pathlib import Path
import subprocess
from runtime import configure

ROOT = Path(__file__).resolve().parents[2]


def main():
    configure()
    required = ('KAKAO_JAVASCRIPT_KEY', 'KAKAO_CLIENT_ID', 'KAKAO_CLIENT_SECRET')
    if any(not os.environ.get(key) for key in required):
        raise SystemExit('카카오 로컬 설정이 필요합니다. 키 값은 출력하지 않습니다.')
    result = subprocess.run(
        ['pnpm', '--dir', 'frontend', 'exec', 'playwright', 'test',
         '--config', 'playwright.external.config.ts'], cwd=ROOT, check=False,
    )
    if result.returncode:
        raise SystemExit(result.returncode)
    print('확인: 실제 카카오 지도 SDK·타일·공식 인가 진입. 미확인: 사용자 동의·코드 교환·초기 원산지 자료.')


if __name__ == '__main__':
    main()
