# [Codex 작업지시서] Task 15 — 한국어 출력 (`--lang ko`)

## 목표

터미널 출력을 한국어로 낼 수 있게 한다. **기본값은 영어를 유지한다.**

```bash
ats-check                 # 영어 (기본, 지금 그대로)
ats-check --lang ko       # 한국어
ATS_CHECK_LANG=ko ats-check   # 환경변수로도 가능
```

우선순위: `--lang` 옵션 > `ATS_CHECK_LANG` 환경변수 > 영어.

## 절대 제약

1. **`--json` 출력은 어떤 언어 설정에서도 영어를 유지한다.**
   스키마 안정성이 §8 요구사항이고, 스크립트가 값을 파싱한다.
2. **판정 근거 원문(evidence)은 절대 번역하지 않는다.** 공고 원문 그대로다 (§6).
3. **`core/`를 수정하지 마라.** 번역은 `cli`의 렌더링 계층에서만 한다.
4. 기본값(영어) 출력이 **한 글자도 바뀌면 안 된다.** 기존 테스트가 이를 보장한다.

## 번역 대상

### 1. VERDICT

| 영어 | 한국어 |
|---|---|
| `VERDICT: APPLY` | `판정: 지원 가능` |
| `VERDICT: REVIEW` | `판정: 확인 필요` |
| `VERDICT: SKIP` | `판정: 제외` |

### 2. 라벨

| 영어 | 한국어 |
|---|---|
| `Language` | `언어` |
| `Level` | `레벨` |
| `Seniority` | `연차` |
| `Degree` | `학위` |

### 3. 스킬 블록

| 영어 | 한국어 |
|---|---|
| `MISSING (required)` | `부족 (필수)` |
| `MISSING (nice)` | `부족 (우대)` |
| `MATCHED` | `보유` |

### 4. 마무리 문구

| 영어 | 한국어 |
|---|---|
| `Analysis stopped at hard filter.` | `하드 필터에서 분석을 중단했습니다.` |

### 5. summary 문구

`core`가 만드는 summary 문자열을 `cli`에서 번역한다. **형태가 유한하므로
고정 문구는 매핑으로, 수치가 들어가는 것은 패턴 치환으로 처리하라.**

| 영어 | 한국어 |
|---|---|
| `English only` | `영어만 요구` |
| `Finnish required` / `Swedish required` | `핀란드어 필수` / `스웨덴어 필수` |
| `Finnish is a plus` | `핀란드어 우대` |
| `Finnish explicitly not required` | `핀란드어 불필요 명시` |
| `Finnish mentioned, no requirement signal` | `핀란드어 언급, 요구 신호 없음` |
| `Finnish - ambiguous requirement` | `핀란드어 - 요구 여부 불명확` |
| `Finnish required (you have it)` | `핀란드어 필수 (보유함)` |
| `not specified` | `명시 없음` |
| `not required` | `요구 없음` |
| `no seniority marker` | `레벨 표시 없음` |
| `full match` | `모두 충족` |

패턴이 들어간 것:

| 영어 | 한국어 |
|---|---|
| `3+ years (profile: 2, tolerance: 1)` | `3년 이상 (내 경력: 2, 허용: 1)` |
| `At least 7 years (profile: 2, tolerance: 1)` | `최소 7년 (내 경력: 2, 허용: 1)` |
| `... — borderline` | `... — 경계선` |
| `Backend Engineer (no seniority marker)` | `Backend Engineer (레벨 표시 없음)` |
| `Senior Backend Engineer (profile max: mid)` | `Senior Backend Engineer (내 상한: mid)` |
| `Master required (profile: NONE)` | `석사 필수 (내 학위: 없음)` |
| `Bachelor or equivalent experience (profile: NONE)` | `학사 또는 동등 경력 (내 학위: 없음)` |
| `missing 2 required, 1 nice` | `필수 2개, 우대 1개 부족` |

**직무명과 스킬 이름은 번역하지 마라** (`Backend Engineer`, `Java`, `Kotlin`).
학위/레벨 값(`NONE`, `mid`)은 위 표대로 처리하라.

**실제 summary 문자열을 `core` 소스에서 확인하고 빠짐없이 매핑하라.**
매핑에 없는 문자열은 **영어 그대로 출력**하고 죽지 마라.

## 가장 중요한 기술 과제 — 한글 폭 정렬

**한글·한자·가나는 터미널에서 폭이 2배다.** 현재 라벨은 12칸으로 패딩하는데,
`언어`(2글자, 표시폭 4)를 `Language`(8)와 같은 규칙으로 패딩하면 정렬이 깨진다.

**표시 폭(display width)을 계산하는 함수를 만들어라:**
- 동아시아 Wide/Fullwidth 문자는 2, 나머지는 1
- 판별 기준: 코드포인트 범위 (한글 `AC00-D7A3`, `1100-11FF`, `3130-318F`,
  CJK `4E00-9FFF`, `3000-303F`, 전각 `FF00-FF60`, 가나 `3040-30FF` 등)
- `Character.UnicodeBlock` 또는 코드포인트 범위 비교 중 편한 쪽

이 함수를 **패딩과 자르기(truncate) 양쪽에** 적용하라:
- 단일 판정: 라벨 컬럼, evidence 들여쓰기
- **배치 출력의 모든 컬럼** (COMPANY/TITLE/REASON/URL 폭 계산과 `…` 자르기)

배치 출력은 한국어 모드에서 헤더도 번역한다:
`VERDICT COMPANY TITLE REASON URL` → `판정 회사 직무 사유 URL`
요약 줄도: `5 jobs · 2 apply · 1 review · 2 skip` → `공고 5건 · 지원 2 · 확인 1 · 제외 2`

## 테스트

1. 기본(영어) 출력이 기존과 **완전히 동일**하다 (기존 테스트로 이미 보장되지만 명시적으로 확인)
2. `--lang ko`에서 VERDICT/라벨/스킬 블록이 한국어로 나온다
3. `ATS_CHECK_LANG=ko`가 동작하고, `--lang en`이 환경변수를 이긴다
4. **`--lang ko --json`이 영어 JSON을 낸다** ← 중요
5. **evidence 원문이 번역되지 않는다** ← 중요
6. 표시 폭 함수 단위 테스트: `"언어"` → 4, `"Language"` → 8, `"Backend"` → 7, 혼합 문자열
7. 한국어 라벨 정렬이 맞다 (라벨 컬럼 뒤 summary 시작 위치가 모든 행에서 동일)
8. 배치 출력이 한국어에서 정렬되고, 좁은 폭에서 자르기가 깨지지 않는다
9. 매핑에 없는 summary가 와도 예외 없이 영어로 출력된다
10. 알 수 없는 `--lang xx` 값 → 영어로 폴백하고 stderr에 경고 (exit는 정상)

## 수정 금지

- **`core/` 전체** — 판정 로직, summary 생성, 골든 파일
- `CLAUDE.md`, `docs/adr/`, `_reviews/`, `_briefs/` 기존 파일
- `.github/workflows/`
- `--json` 스키마와 종료 코드
- `git commit` / `git push` 금지

## 완료 조건

1. `./gradlew build` 성공, **기존 221개 테스트 전부 통과**
2. 위 10개 테스트 통과
3. `./gradlew :cli:nativeCompile` 성공, **단일 파일 유지**
4. 네이티브 바이너리로 영어/한국어 출력을 각각 확인했다

## 보고서

`_briefs/task-15-report.md`

```
[Codex 결과 보고서] Task 15
1. 번역 계층을 어디에 두었는가 (구조)
2. summary 번역: 고정 매핑과 패턴 치환을 각각 어떻게 처리했는가
3. 표시 폭 계산 구현 방법과 적용 지점
4. **네이티브 바이너리 실제 출력 — 영어/한국어, 단일 판정/배치 각각**  ← 반드시 붙일 것
5. --json이 영어를 유지함을 어떻게 확인했는가
6. 테스트 결과
7. 매핑하지 못한 summary가 있는가 (있으면 목록)
8. 남은 리스크
```
