# [Codex 작업지시서] Task 6.5 — check가 프론트매터를 인식하게 한다

## 목표

`save`로 저장한 파일을 `check`하면 **판정이 달라지는 결함**을 고친다.

짧은 작업이다. 커밋 전에 끝내야 한다.

## 재현 (네이티브 바이너리 실측)

같은 공고를 두 경로로 판정했다:

```bash
# 1) 순수 본문을 stdin으로
printf 'Senior Backend Engineer\nAurora Labs\n\nRequirements:\nStrong Java and Spring Boot.\n' | ./ats-check
VERDICT: APPLY
  ⚠ Level       Senior Backend Engineer (profile max: mid)     ← 정확

# 2) save로 저장한 뒤 --job으로
printf '...같은 내용...' | ./ats-check save --url "https://example.com/senior"
./ats-check --job jobs/aurora-labs-senior-backend-engineer.md
VERDICT: APPLY
  ✓ Level       --- (no seniority marker)                      ← 망가짐
```

## 원인

`CheckCommand`가 파일 내용을 그대로 `JobPosting.fromText(text)`에 넘긴다.
`fromText`는 **첫 번째 비어 있지 않은 줄을 title로** 삼는데, 저장된 파일의 첫 줄은 `---`다.

결과:
1. **시니어리티 감지가 무력화된다.** `Senior` 직무를 못 잡아 `WARN`이 사라진다
2. 프론트매터 줄들(`url:`, `company:`, `status: new`)이 본문 절로 분류되어
   판정에 섞일 수 있다

`save`가 배치 모드의 전제인데, **저장한 파일을 판정하면 결과가 달라진다.**
이 상태로는 `--job-dir` 배치 모드가 전부 틀린 답을 낸다.

## 수정

`CheckCommand`가 입력을 `JobFileParser`로 파싱하도록 하라 (Task 6에서 만든 것을 재사용).

| 상황 | title | 판정 대상 본문 |
|---|---|---|
| 프론트매터에 `title`이 있음 | 프론트매터의 `title` | body |
| 프론트매터는 있으나 `title`이 빔 | body의 첫 비어 있지 않은 줄 | body |
| 프론트매터 없음 | 첫 비어 있지 않은 줄 (현재 동작) | 전체 |

- **`--job` 파일과 stdin 둘 다** 같은 처리를 하라.
  붙여넣기한 텍스트에 프론트매터가 있을 수 있다
- 프론트매터가 없으면 **동작이 지금과 완전히 같아야 한다** (하위 호환, §8)
- 판정에 **프론트매터 줄이 절대 섞이지 않아야 한다**

`core`는 건드리지 마라. `JobPosting`은 이미 `title`과 `body`를 분리해 받는다.

## 테스트

`cli` 테스트에 추가하라:

1. **왕복 동등성** — 같은 공고를 (a) 순수 본문 stdin, (b) `save` 후 `--job`으로 판정했을 때
   **verdict와 모든 finding의 rule+status가 동일하다** ← 이 테스트가 핵심이다
2. 프론트매터의 `title`이 `SENIORITY_LEVEL` 판정에 쓰인다
   (`title: Senior Backend Engineer` → `WARN`)
3. 프론트매터에 `title`이 없으면 body 첫 줄이 title이 된다
4. 프론트매터가 없는 파일은 기존과 동일하게 동작한다
5. 프론트매터 줄(`url:`, `status: new`)이 finding의 evidence에 나타나지 않는다
6. stdin으로 프론트매터가 포함된 텍스트를 줘도 동일하게 처리된다

## 수정 금지

- `CLAUDE.md`, `docs/adr/`, `_reviews/`, `_briefs/` 기존 파일
- **`core/` 전체**
- `core/src/test/resources/golden/`
- `save` / `open` / `init`의 동작
- `--json` 스키마
- `git commit` / `git push` 금지
- 배치 모드(`--job-dir`) — 다음 태스크

## 완료 조건

1. `./gradlew build` 성공
2. **기존 164개 테스트가 그대로 통과**
3. 위 6개 테스트가 통과
4. `./gradlew :cli:nativeCompile` 성공, **단일 파일 유지**
5. 위 재현 케이스를 네이티브 바이너리로 다시 실행해서 두 경로가 같은 결과를 낸다

## 보고서

`_briefs/task-6.5-report.md`

```
[Codex 결과 보고서] Task 6.5

1. 수정 방법
2. 변경한 파일 목록
3. **재현 케이스 수정 전후 출력 비교 (네이티브 바이너리)**   ← 반드시 붙일 것
4. 왕복 동등성 테스트를 어떻게 작성했는가
5. 테스트 결과 (총 개수, 기존 164개 유지 확인)
6. 스스로 결정한 것 + 근거
7. 다음 추천 작업
```
