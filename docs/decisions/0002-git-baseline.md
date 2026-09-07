# ADR 0002: 기존 초기 이력 보존

상태: 채택, 2026-09-07.
원격 main에 0796eb5bdd6921df6145a382f4f8049d4de4089a 초기 커밋이 이미 있다. 빈 저장소 bootstrap 커밋 예외를 사용하지 않는다. 원격 이력을 새로 만들거나 덮어쓰지 않는다.

origin/dev가 없어 동일 초기 커밋을 가리키는 로컬 dev만 만들고 chore/bootstrap-harness를 분기했다. dev의 잘못된 origin/main tracking은 제거했다. 이후 branch-start는 origin/dev가 없으면 실패한다. 원격 dev는 관리자가 기준 커밋과 보호 생성 계획을 확인해 GitHub에서 생성해야 한다. 이번 도구는 보호 브랜치 직접 push 예외를 제공하지 않는다.
