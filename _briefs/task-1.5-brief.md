# [Codex 작업지시서] Task 1.5 — 섹션 분류기 결함 수정

## 목표

Task 1 산출물을 2차 검증한 결과 **실제 결함 3건**을 확인했다. 이를 수정한다.

판정 규칙(Task 2)이 이 분류기 위에 얹히므로, **분류가 틀리면 모든 규칙이 틀린다.**
다음 태스크로 넘어가기 전에 반드시 고친다.

너의 Task 1 보고서 §8이 이 문제들을 스스로 지적했다. 정확한 자기 평가였다.
이제 실측으로 재현됐으니 고친다.

## 재현된 결함 (실행 출력)

```
### A. 부정문
Requirements:
Strong Java skills.
Finnish is not required.      →  [REQUIRED ]   ❌ 오탐
English is mandatory.

### B. 미지 헤더 아래 섹션 상태 지속
Requirements:
Java experience.

Benefits:                     →  [REQUIRED ]   ❌ 헤더가 절로 들어감
Free Finnish lessons.         →  [REQUIRED ]   ❌ 복지를 필수 요건으로 오독
Lunch benefit.                →  [REQUIRED ]   ❌

### C. 헤더 오탐
Requirements include Java and Kotlin.    →  절이 0개   ❌ 요건 소실
We expect you to ship code.              →  절이 0개   ❌
```

**결함 A가 가장 치명적이다.** 이 도구의 목적은 "핀란드어 필수 공고를 거르는 것"인데,
현재는 **핀란드어가 필요 없다고 명시한 공고를 SKIP시킨다.** 정반대 방향의 오류다.

**결함 B도 실제 공고에서 흔하다.** 핀란드 회사는 복리후생으로 "Finnish lessons provided"를
자주 적는다. 이걸 언어 필수 요건으로 읽으면 좋은 공고를 전부 버린다.

## 수정 사항

### 수정 1 — 부정 신호(negation)를 4번째 신호로 추가

`RequirementLevel`에 값을 하나 추가한다:

```java
NEGATED   // 요구하지 않는다고 명시됨
```

`NICE`로 뭉뚱그리지 마라. `NICE`는 "있으면 좋다", `NEGATED`는 "필요 없다"로 의미가 다르고,
출력 문구도 달라야 한다 (`"Finnish: explicitly not required"`).

**부정 마커 사전:**
```
not required, not mandatory, not necessary, not essential, not needed,
not expected, not a requirement, no need for, without the need for,
isn't required, is not a must, not obligatory
```

**규칙:**
- 절에 부정 마커가 있으면 어조 신호를 **무효화**하고 `NEGATED`를 부여한다
- 단, **대조 접속사**(`but`, `however`, `although`, `though`, `while`)가 같은 절에 있으면
  `AMBIGUOUS`로 한다. 예: `"Finnish is not required, but it helps"` — 부정 범위가 불확실하다
- 부정 마커는 어조 마커보다 **우선한다.** `not required`에서 `required`가 먼저 걸리면 안 된다
  (더 긴 매칭 우선 또는 부정 검사 선행)

### 수정 2 — 헤더 판정을 형태 기반으로 강화

현재는 "사전 표현으로 시작 + 60자 이하"만 본다. 그래서
`Requirements include Java and Kotlin.`이 헤더로 먹힌다.

**헤더 판정 규칙 (전부 만족해야 헤더):**
1. 길이 60자 이하
2. **문장 종결 부호(`.` `!` `?`)로 끝나지 않는다** ← 이 조건이 결함 C를 고친다
3. 콜론으로 끝나거나, 줄 전체가 사전 표현과 일치한다

`Requirements include Java and Kotlin.`은 마침표로 끝나므로 헤더가 아니다 → 절이 된다.
`We expect you to ship code.`도 마침표로 끝나므로 절이 된다 (어조 `we expect` → REQUIRED).

### 수정 3 — 중립 헤더 사전을 추가하고 섹션 상태를 리셋

헤더로 판정됐지만 필수/우대 사전에 없으면 **섹션 상태를 `NONE`으로 리셋**한다.
현재는 이전 상태가 그대로 이어져서 결함 B가 발생한다.

**중립 헤더 사전 (명시적으로):**
```
benefits, perks, what we offer, we offer, compensation, salary,
about us, about the team, about the company, who we are, why join,
our stack, tech stack, technologies, how to apply, application process,
interview process, location, working hours, contact
```

**중요:** 사전에 없는 미지의 헤더도 `NONE`으로 리셋한다.
알 수 없는 섹션에서 이전 섹션의 강제성을 이어가는 것이 결함 B의 원인이다.
**모르면 중립으로 두는 것이 §6의 "단정하지 않는다" 원칙에 맞다.**

또한 **헤더로 판정된 줄은 절 목록에 포함하지 마라.** 현재 `Benefits:`가 절로 들어가고 있다.

## 추가할 테스트

기존 18개 테스트를 유지하면서 아래를 추가하라. **위 재현 케이스를 그대로 쓰라:**

1. `"Finnish is not required."` → `NEGATED`
2. `"Finnish is not mandatory."` → `NEGATED`
3. `"Finnish is not required, but it helps."` → `AMBIGUOUS` (대조 접속사)
4. `Requirements:` 섹션 안에서도 부정문은 `NEGATED`
5. `Benefits:` 헤더 아래 절이 `REQUIRED`가 **아님** (섹션 리셋 확인)
6. `Benefits:` 줄 자체가 절 목록에 **없음**
7. 미지 헤더(`Our Stack:`) 아래에서도 섹션이 리셋됨
8. `"Requirements include Java and Kotlin."` → 헤더가 아니라 **절**로 잡힘
9. `"We expect you to ship code."` → 절이며 `REQUIRED` (어조 `we expect`)
10. 부정 마커가 어조 마커보다 우선함 (`not required`에서 `required`가 안 걸림)

**결함 A/B/C 재현 케이스 3개를 통합 테스트로도 넣어라** — 위 재현 출력의 텍스트 블록을
그대로 입력으로 주고 기대 레벨 목록을 검증하는 테스트.

## 회귀 방지

- **기존 18개 테스트가 전부 통과해야 한다.** 특히 §6의 세 예시:
  - `"Fluent Finnish and English are required"` → `REQUIRED`
  - `"Finnish is a plus"` → `NICE`
  - `"Working knowledge of Finnish"` → `AMBIGUOUS`
- 결합 규칙 표 10개 행도 그대로 유지된다. `NEGATED`는 표에 없던 **새 축**이며,
  기존 행의 결과를 바꾸지 않는다

## 수정 금지

- `CLAUDE.md`, `docs/adr/`, `_briefs/` 기존 파일
- `cli/` 전체
- `core`에 의존성 추가 금지
- `git commit` / `git push` 금지 — 커밋은 Claude가 검토 후 한다
- 판정 규칙(언어/연차/학위/스킬), `AtsChecker` 본체 — 다음 태스크 범위

## 완료 조건

1. `./gradlew build` 성공
2. `./gradlew :core:test` 통과. 기존 18개 + 신규 테스트 전부
3. 위 재현 케이스 A/B/C가 기대대로 동작
4. `core` 런타임 의존성 여전히 0개 (`./gradlew :core:dependencies --configuration runtimeClasspath`로 확인)

## 보고서

`_briefs/task-1.5-report.md`

```
[Codex 결과 보고서] Task 1.5

1. 수정한 결함과 수정 방법 (A/B/C 각각)
2. 변경한 파일 목록
3. NEGATED 도입이 기존 결합 규칙에 미친 영향
4. 재현 케이스 A/B/C의 수정 전/후 출력 비교   ← 실제 출력을 붙일 것
5. 테스트 결과 (개수 포함, 기존 18개 유지 확인)
6. 스스로 결정한 것 + 근거
7. 여전히 남아 있는 오탐/미탐 리스크
8. 다음 추천 작업
```
