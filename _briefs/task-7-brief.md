# [Codex 작업지시서] Task 7 — 배치 모드 (Day 10-13의 3/3)

## 목표

`CLAUDE.md` §8의 **배치 모드**를 구현한다. 이것으로 Day 10-13이 끝난다.

```bash
ats-check --job-dir ./jobs
```

`list` / `mark`는 **구현하지 마라.** §8이 "조건부 — 여유가 있을 때만"이라고 명시했고,
지금은 CI/CD(Day 14-17)로 넘어가는 것이 우선이다.

## 배경 (확인된 사실 — 다시 조사하지 말 것)

- `main` 브랜치, 커밋 `e8f63f5`. 테스트 170개 통과. 네이티브 단일 파일 19MB.
- 이미 있는 것:
  - `JobFileParser` — 프론트매터 + 본문 파싱 (알 수 없는 키 보존)
  - `JobStore` — `jobs/` 디렉토리 접근
  - `CheckCommand` — 단일 판정, 프론트매터 인식, 종료 코드 0/1/2/64/70
  - `TerminalRenderer`, `JsonRenderer`
  - `AtsChecker.check(JobPosting, Profile) -> CheckResult`
- `core`는 런타임 의존성 0개. **`core`를 건드리지 마라.**
- **AWT 금지** (§8, ADR-005)

## 1. 입력

```bash
ats-check --job-dir ./jobs
ats-check --job-dir ./jobs --json
```

- 디렉토리에서 `*.md`, `*.txt`를 읽는다. **파일명 오름차순**으로 처리한다 (출력이 재현 가능해야 한다)
- 하위 디렉토리는 **재귀하지 않는다** (v0.1 범위)
- 각 파일을 `JobFileParser`로 파싱하고 `CheckCommand`와 **동일한 방식**으로 판정한다
  — 단일 판정과 배치 판정이 어긋나면 안 된다. 판정 경로를 공유하도록 리팩터링하라
- 디렉토리가 없으면 **exit 64**
- 디렉토리가 비어 있으면 안내 후 **exit 0** (오류가 아니다)
- 읽을 수 없는 파일 하나 때문에 **전체가 죽으면 안 된다.** 그 파일만 건너뛰고
  stderr에 경고한 뒤 계속하라
- `--job`과 `--job-dir`을 동시에 주면 **exit 64**

## 2. 배치 출력 (§8)

```
VERDICT  COMPANY   TITLE                 REASON                URL
SKIP     Alten     Java Developer        Finnish required      linkedin.com/jobs/view/111
SKIP     Siili     Backend Architect     Senior (7+ years)     linkedin.com/jobs/view/222
APPLY    Wolt      Backend Engineer      missing: Kotlin, K8s  linkedin.com/jobs/view/333
APPLY    Ravogen   Fullstack Developer   full match            ravogen.fi/careers/12
REVIEW   Solita    Node.js Developer     Finnish ambiguous     solita.fi/careers/456

5 jobs · 2 apply · 1 review · 2 skip
```

### 컬럼 값

| 컬럼 | 내용 |
|---|---|
| `VERDICT` | `APPLY` / `REVIEW` / `SKIP` |
| `COMPANY` | 프론트매터 `company`. 비면 **파일명**으로 대체 (§8) |
| `TITLE` | 프론트매터 `title`. 비면 본문 첫 줄 |
| `REASON` | 아래 규칙 |
| `URL` | 프론트매터 `url`. **스킴(`https://`)을 생략**해서 표시 |

### REASON 생성 규칙

| verdict | REASON |
|---|---|
| `SKIP` | **첫 번째 `FAIL`** finding의 summary |
| `REVIEW` | 첫 번째 `REVIEW` finding의 summary |
| `APPLY` + missingRequired 있음 | `missing: <표시이름들>` (§8 예시: `missing: Kotlin, K8s`) |
| `APPLY` + missingRequired 없음 | `full match` |

- summary가 길면 컬럼 폭에 맞춰 자르고 `…`을 붙인다
- 스킬 이름은 `TerminalRenderer`의 **표시 이름 맵을 재사용**하라

### 레이아웃

- 각 컬럼은 내용에 맞춰 폭을 정하되, 전체가 터미널 너비를 넘으면 **URL 컬럼부터 생략**한다 (§8)
- 그래도 넘으면 `REASON`을 줄인다. `VERDICT`는 절대 줄이지 않는다
- 터미널 너비는 `COLUMNS` 환경변수를 읽고, 없으면 **100**을 기본값으로 쓴다.
  `--width <n>` 옵션으로 override 가능하게 하라 (테스트에서 필요하다)
- 컬럼 사이는 공백 2칸 이상

### 요약 줄

```
5 jobs · 2 apply · 1 review · 2 skip
```

- 개수가 0인 항목은 생략한다 (`3 jobs · 3 apply`)
- 구분자는 ` · ` (U+00B7)

## 3. OSC 8 하이퍼링크 (§8)

URL을 터미널 하이퍼링크로 감싼다:

```
\033]8;;https://linkedin.com/jobs/view/111\033\\linkedin.com/jobs/view/111\033]8;;\033\\
```

**반드시 지켜라 (§8):**
- **비-TTY(파이프·리다이렉트)에서는 OSC 8을 끈다.** `System.console() == null`로 판별
- **`--json`에는 어떤 경우에도 넣지 않는다**
- `--no-hyperlink` 옵션으로 강제로 끌 수 있게 하라 (테스트용)

제어문자가 섞이면 `grep`·`awk` 연동이 깨진다. 이것이 §8이 명시한 이유다.

## 4. `--json` 배치 출력

```json
{
  "jobs": [
    {
      "file": "wolt-backend-engineer.md",
      "company": "Wolt",
      "title": "Backend Engineer",
      "url": "https://linkedin.com/jobs/view/333",
      "status": "new",
      "verdict": "APPLY",
      "stoppedAtHardFilter": false,
      "findings": [ ... ],
      "skillGap": { ... }
    }
  ],
  "summary": { "total": 5, "apply": 2, "review": 1, "skip": 2 }
}
```

- 각 항목의 `findings` / `skillGap` 구조는 **단일 판정 JSON과 동일**해야 한다
- **프론트매터의 모든 메타데이터를 포함한다** (§8: `url, company, title, status`)
- 스킬 이름은 정규화된 **소문자** 유지
- 값이 없으면 `null` (필드 자체를 빼지 마라 — 스키마 안정성)

## 5. 종료 코드

**배치 모드는 가장 나쁜 판정을 반환한다** (§8 개정판):

| 상황 | 코드 |
|---|---|
| `SKIP`이 하나라도 있음 | **2** |
| `SKIP`은 없고 `REVIEW`가 있음 | **1** |
| 전부 `APPLY` | **0** |
| 디렉토리 없음 / 옵션 충돌 | **64** |
| 내부 오류 | **70** |
| 디렉토리가 비어 있음 | **0** |

## 6. 테스트

`@TempDir`에 공고 파일을 만들어 검증하라. **실제 터미널을 요구하지 마라.**

1. 3개 파일(APPLY/REVIEW/SKIP) → 표에 3줄이 나오고 exit **2**
2. 전부 APPLY → exit **0**
3. APPLY + REVIEW → exit **1**
4. 빈 디렉토리 → exit **0**, 안내 메시지
5. 없는 디렉토리 → exit **64**
6. `--job`과 `--job-dir` 동시 → exit **64**
7. 파일명 오름차순으로 정렬된다
8. **프론트매터가 없는 파일은 COMPANY가 파일명으로 대체된다** (§8)
9. URL에서 스킴이 생략되어 표시된다
10. REASON: SKIP은 첫 FAIL summary, APPLY+갭은 `missing: ...`, 갭 없으면 `full match`
11. 요약 줄이 정확하다 (`3 jobs · 1 apply · 1 review · 1 skip`)
12. 개수 0인 항목은 요약에서 생략된다
13. **`--no-hyperlink`면 출력에 `\033]8` 이 없다**
14. **`--json`에 OSC 8 제어문자가 없다**
15. `--json`의 각 항목이 단일 판정 JSON과 같은 구조다
16. `--width 60` 같은 좁은 폭에서 URL 컬럼이 생략된다
17. 읽을 수 없는 파일이 섞여 있어도 나머지가 처리된다
18. **배치 판정과 단일 판정이 같은 파일에 대해 동일한 verdict를 낸다** ← 중요

## 수정 금지

- `CLAUDE.md`, `docs/adr/`, `_reviews/`, `_briefs/` 기존 파일
- **`core/` 전체** — 문제를 발견하면 보고만 하라
- `core/src/test/resources/golden/`
- 단일 판정의 **종료 코드와 `--json` 스키마**
- `save` / `open` / `init`의 동작
- `git commit` / `git push` 금지
- **`list` / `mark` — 구현하지 마라** (§8 조건부 항목)

## 완료 조건

1. `./gradlew build` 성공
2. **기존 170개 테스트가 그대로 통과**
3. 위 18개 테스트가 통과
4. `core` 런타임 의존성 0개
5. `./gradlew :cli:nativeCompile` 성공, **단일 파일 유지**
6. 네이티브 바이너리로 실제 디렉토리를 판정한 출력을 보고서에 붙인다

## 보고서

`_briefs/task-7-report.md`

```
[Codex 결과 보고서] Task 7

1. 수행한 작업 요약
2. 생성/변경한 파일 목록
3. 단일 판정과 배치 판정이 같은 경로를 쓰도록 어떻게 했는가
4. 컬럼 폭 계산과 URL 생략 로직
5. OSC 8을 언제 켜고 끄는가
6. 테스트 결과 (총 개수, 기존 170개 유지 확인)
7. **네이티브 바이너리 배치 출력 실제 결과**   ← 반드시 붙일 것
8. 스스로 결정한 것 + 근거
9. 남은 리스크
10. 다음 추천 작업
```
