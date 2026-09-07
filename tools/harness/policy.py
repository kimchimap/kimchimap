"""Git 메시지와 브랜치 규칙의 공통 검증기."""
import json
import re
from pathlib import Path

PREFIXES = 'feat fix docs style refactor perf test build chore revert release'.split()
PROTECTED = {'main', 'dev'}
SECTIONS = ['변경 목적', '주요 변경 사항', '검증 방법과 실제 결과', '데이터·보안 영향', '호환성·마이그레이션', '남은 사항', '관련 이슈']
TERMS = set(json.loads((Path(__file__).parent / 'technical-terms.json').read_text()))


def korean(text):
    plain = re.sub(r'```[\s\S]*?```|`[^`]*`|https?://\S+', '', text)
    plain = re.sub(r'\b[\w.-]+(?:/[^\s]+|\.[a-zA-Z]{1,8})\b', '', plain)
    words = re.findall(r'[A-Za-z][A-Za-z0-9_-]*', plain)
    unknown = [w for w in words if w not in TERMS and not re.search(r'[_]|[a-z][A-Z]', w)]
    if not re.search('[가-힣]{2,}', plain):
        raise ValueError('한국어 설명이 필요합니다.')
    if unknown:
        raise ValueError('허용 기술 명칭 외 영어 설명은 한국어로 작성하세요: ' + ', '.join(unknown[:5]))


def message(text):
    lines = text.strip().splitlines()
    if not lines:
        raise ValueError('메시지가 비어 있습니다.')
    match = re.fullmatch(r'(' + '|'.join(PREFIXES) + r')!?: (.+)', lines[0])
    if not match:
        raise ValueError('제목은 prefix: 한국어 제목 형식이어야 합니다.')
    title = match[2]
    if len(title) < 8 or len(lines[0]) > 100 or title in {'수정', '업데이트', '오류 수정', '버그 수정'}:
        raise ValueError('변경 대상과 동작이 드러나는 구체적인 제목을 작성하세요.')
    korean(title)
    if len(lines) > 1 and lines[1].strip():
        raise ValueError('제목과 본문 사이에는 빈 줄이 필요합니다.')
    for line in lines[2:]:
        if line.strip() and not line.startswith(('Co-authored-by:', 'Signed-off-by:')):
            korean(line)


def pr(title, body, base, head, same_repo=True):
    message(title)
    if base == 'main':
        if head != 'dev' or not same_repo or not re.match(r'release!?: ', title):
            raise ValueError('main PR은 같은 저장소 dev의 release 제목만 허용합니다.')
    elif base != 'dev' or head in PROTECTED:
        raise ValueError('일반 작업 PR의 base는 dev여야 합니다.')
    if base == 'dev':
        branch_name(head)
    for section in SECTIONS:
        match = re.search(r'^## ' + re.escape(section) + r'\n([\s\S]*?)(?=^## |\Z)', body, re.M)
        if not match or not match[1].strip():
            raise ValueError('PR 본문 항목 누락: ' + section)
        content = match[1].strip()
        if content in {'한국어로 목적을 작성한다.', '한국어로 변경 내용을 작성한다.', '명령·실행 범위·성공/실패/미실행을 구분한다.', '개인정보·원산지 의미·권한 영향을 작성한다.', '호환성·DB 변경과 복구 방법을 작성한다.', '외부 차단과 미완료를 작성한다.', '관련 이슈 또는 해당 없음을 작성한다.'}:
            raise ValueError('PR 템플릿 안내문을 실제 내용으로 바꾸세요: ' + section)
        korean(content)


def branch_name(name):
    if not re.fullmatch(r'(' + '|'.join(PREFIXES) + r')/[a-z0-9]+(?:-[a-z0-9]+)*', name):
        raise ValueError('작업 브랜치는 feat/english-name 형식이어야 합니다.')


def push_refs(data):
    for line in data.splitlines():
        fields = line.split()
        if len(fields) != 4:
            raise ValueError('pre-push 입력 형식이 잘못되었습니다.')
        if fields[2] in {'refs/heads/main', 'refs/heads/dev'}:
            raise ValueError('main/dev 대상 push·강제 push·삭제는 금지합니다.')
