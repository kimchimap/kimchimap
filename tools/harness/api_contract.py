"""실제 서버 OpenAPI와 프론트 타입의 변경을 함께 검증한다."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
from runtime import configure

ROOT = Path(__file__).resolve().parents[2]


def main(mode):
    configure()
    subprocess.run([str(ROOT / 'scripts/harness'), 'api-generate'], cwd=ROOT, check=True)
    document = json.loads((ROOT / 'backend/build/contracts/openapi.json').read_text())
    canonical = json.dumps(document, ensure_ascii=False, sort_keys=True, indent=2) + '\n'
    with tempfile.TemporaryDirectory() as directory:
        spec = Path(directory) / 'openapi.json'
        types = Path(directory) / 'generated.d.ts'
        spec.write_text(canonical)
        subprocess.run(['pnpm', '--dir', 'frontend', 'exec', 'openapi-typescript', str(spec), '--output', str(types)], cwd=ROOT, check=True)
        outputs = {ROOT / 'docs/api/openapi.json': canonical, ROOT / 'frontend/src/api/generated.d.ts': types.read_text()}
        for destination, content in outputs.items():
            if mode == 'generate':
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_text(content)
            elif not destination.exists() or destination.read_text() != content:
                raise SystemExit('API 계약 변경: pnpm --dir frontend api:generate 후 차이를 검토하세요.')
    print('API 문서·타입 생성 완료' if mode == 'generate' else 'API 문서·타입 일치')


if __name__ == '__main__':
    if len(sys.argv) != 2 or sys.argv[1] not in {'generate', 'check'}:
        raise SystemExit('generate 또는 check를 지정하세요.')
    main(sys.argv[1])
