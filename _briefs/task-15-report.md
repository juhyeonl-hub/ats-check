[Codex 결과 보고서] Task 15

1. 번역 계층을 어디에 두었는가 (구조)

- 번역은 `cli` 렌더링 계층에만 두었다.
- `TerminalLanguage`가 `en`/`ko` 파싱을 담당한다.
- `LocalizedText`가 VERDICT, 라벨, 스킬 블록 라벨, 하드 필터 문구를 담당한다.
- `SummaryTranslator`가 core summary 문자열의 한국어 변환을 담당한다.
- `CheckCommand`는 `--lang` 옵션과 `ATS_CHECK_LANG` 환경변수를 해석해 터미널 렌더러에만 전달한다.
- `--json` 경로는 기존 `JsonRenderer`/`BatchJsonRenderer`를 그대로 사용하므로 언어 설정의 영향을 받지 않는다.

2. summary 번역: 고정 매핑과 패턴 치환을 각각 어떻게 처리했는가

- 고정 summary는 `SummaryTranslator.FIXED_KO` 맵으로 처리했다.
- 수치/가변 문자열은 정규식 패턴으로 처리했다.
- 경력 summary: `3+ years`, `At least 7 years`, `minimum of`, `over`, `more than`, 범위, 단어 숫자 형태를 한국어 연차 문구로 바꾼 뒤 `(내 경력: n, 허용: n)`을 붙인다.
- 레벨 summary: `(... no seniority marker)`, `(... profile max: mid)` 형태를 변환하되 직무명은 번역하지 않는다.
- 학위 summary: `Bachelor|Master|PhD required`, `... or equivalent experience` 형태를 변환하고 profile 학위 값은 `NONE -> 없음` 등으로 변환한다.
- 스킬 summary: `missing n required, m nice`를 `필수 n개, 우대 m개 부족`으로 변환한다.
- 매핑에 없는 summary는 예외 없이 원문 영어를 반환한다.

3. 표시 폭 계산 구현 방법과 적용 지점

- `DisplayWidth`를 추가해 문자열을 코드포인트 단위로 순회한다.
- 한글, 한자, 가나, CJK 기호, 전각 문자 등 East Asian Wide/Fullwidth 범위는 표시폭 2로 계산하고 나머지는 1로 계산한다.
- `padRight`와 `truncate`가 문자열 길이가 아니라 표시폭 기준으로 동작한다.
- 적용 지점:
  - 단일 터미널 출력의 finding 라벨 패딩
  - 단일 터미널 출력의 스킬 블록 라벨 패딩
  - 배치 표의 모든 컬럼 폭 계산
  - 배치 표의 모든 컬럼 자르기와 패딩
- 추가 수정: 한국어 배치 표에서 URL 최소 폭을 유지하고 남는 폭이 있으면 `사유` 컬럼을 실제 표시폭까지 확장하도록 했다. 이로써 `--width 100`에서 `핀란드어 - 요구 여부 불명확`이 잘리지 않는다.

4. 네이티브 바이너리 실제 출력 — 영어/한국어, 단일 판정/배치 각각

단일 판정 영어:

```text
VERDICT: APPLY

  ✓ Language    Finnish is a plus
  ✓ Level       Developer (no seniority marker)
  ⚠ Seniority   3+ years (profile: 2, tolerance: 2) — borderline
                "3+ years of frontend work."
  ✓ Degree      not required

  MATCHED              react, TypeScript
```

단일 판정 한국어:

```text
판정: 지원 가능

  ✓ 언어        핀란드어 우대
  ✓ 레벨        Developer (레벨 표시 없음)
  ⚠ 연차        3년 이상 (내 경력: 2, 허용: 2) — 경계선
                "3+ years of frontend work."
  ✓ 학위        요구 없음

  보유                 react, TypeScript
```

배치 영어:

```text
VERDICT  COMPANY  TITLE                REASON                  URL
APPLY    Ravogen  Fullstack Developer  full match              ravogen.fi/careers/12
REVIEW   Solita   Node.js Developer    Finnish - ambiguous r…  solita.fi/careers/456
SKIP     Alten    Java Developer       Finnish required        linkedin.com/jobs/view/111

3 jobs · 1 apply · 1 review · 1 skip
```

배치 한국어:

```text
판정       회사     직무                 사유                         URL
지원 가능  Ravogen  Fullstack Developer  모두 충족                    ravogen.fi/careers/12
확인 필요  Solita   Node.js Developer    핀란드어 - 요구 여부 불명확  solita.fi/careers/456
제외       Alten    Java Developer       핀란드어 필수                linkedin.com/jobs/view/111

공고 3건 · 지원 1 · 확인 1 · 제외 1
```

5. --json이 영어를 유지함을 어떻게 확인했는가

네이티브 바이너리에서 `--lang ko --json`을 실행해 JSON key/value와 summary가 영어임을 확인했다.

```text
{
  "verdict": "APPLY",
  "stoppedAtHardFilter": false,
  "findings": [
    {
```

테스트에서도 `jsonOutputStaysEnglishWithKoreanLanguage()`가 `verdict: SKIP`, `"summary": "Finnish required"`, evidence 원문을 확인하고 한국어 문자열이 없음을 검증한다.

6. 테스트 결과

- `./gradlew build`: 성공
- 테스트 XML 기준: `core` 150개, `cli` 85개, 총 235개 테스트 통과
- `./gradlew :cli:nativeCompile`: 성공
- 네이티브 산출물: `cli/build/native/nativeCompile/ats-check`
- nativeCompile 디렉터리의 파일: `ats-check` 1개

7. 매핑하지 못한 summary가 있는가 (있으면 목록)

- core 소스의 현재 summary 생성 형태 기준으로 누락된 known summary는 없다.
- 의도적으로 unknown/custom summary는 영어 원문으로 fallback한다.

8. 남은 리스크

- `DisplayWidth`는 작업 지시서에 명시된 주요 East Asian Wide/Fullwidth 범위를 직접 판정한다. 이모지, 조합 문자, ANSI escape가 들어간 임의 문자열의 폭까지 완전 지원하는 범용 wcwidth 구현은 아니다.
- 한국어 배치 표는 URL 최소 폭을 보존하면서 `사유` 컬럼을 우선 확장한다. 더 긴 한국어 summary는 터미널 폭에 따라 여전히 줄임표 처리된다.
