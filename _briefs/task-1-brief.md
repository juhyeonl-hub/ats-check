# [Codex 작업지시서] Task 1 — 섹션 분류기 (Day 4-9의 1/4)

## 목표

공고 텍스트를 **절(clause) 단위로 쪼개고, 각 절이 필수 요건인지 우대 사항인지 판정**하는
`core` 모듈을 만든다.

이것이 `CLAUDE.md` §6이 "이 프로젝트의 유일한 기술적 난제이자 핵심 가치"로 지목한 부분이다.
판정 규칙(언어/연차/학위/스킬)은 **이번 범위가 아니다.** 다음 태스크에서 이 위에 얹는다.

## 배경 (확인된 사실 — 다시 조사하지 말 것)

- `main` 브랜치, 커밋 `98fdfa6`. 골격은 이미 있다.
- `CLAUDE.md` §6이 이 작업의 사양이다. **먼저 읽어라.**
- 툴체인: `source "$HOME/.sdkman/bin/sdkman-init.sh"` 후 `./gradlew`
- `core`는 **의존성 0**이다. 이번에도 추가하지 마라. 파일 I/O·네트워크·`System.out` 금지.
- PDF 파싱은 v0.1에서 제외됐다 (ADR-005). 입력은 항상 순수 텍스트다.

## 설계 (이대로 구현하라)

### 3층 신호 — §6의 2층에 완화 표현을 하나 더 얹는다

§6은 2층 신호를 제시하지만, 그것만으로는 §6 자신의 예시를 처리할 수 없다:

```
"Working knowledge of Finnish"   → §6은 REVIEW로 분류하길 원한다
```

이 문장이 `Requirements:` 섹션 안에 있으면 2층 신호로는 REQUIRED가 되어버린다.
따라서 **완화 표현(hedge)** 을 세 번째 신호로 둔다.

| 신호 | 역할 |
|---|---|
| **1. 섹션 헤더** | 현재 섹션이 필수/우대 중 무엇인지 (문서를 훑으며 상태 추적) |
| **2. 문장 어조** | 절 자체의 강제성 마커 |
| **3. 완화 표현** | 강제성을 낮추는 마커 → 확신을 깎는다 |

### 결합 규칙 (이 표를 그대로 구현하고, 테스트로 전부 덮어라)

| 섹션 신호 | 어조 신호 | hedge | 결과 |
|---|---|---|---|
| REQUIRED | REQUIRED | 없음 | `REQUIRED` |
| REQUIRED | 없음 | 없음 | `REQUIRED` |
| REQUIRED | NICE | — | **`AMBIGUOUS`** (신호 충돌) |
| NICE | NICE | — | `NICE` |
| NICE | 없음 | — | `NICE` |
| NICE | REQUIRED | 없음 | **`AMBIGUOUS`** (신호 충돌) |
| 없음 | REQUIRED | 없음 | `REQUIRED` |
| 없음 | NICE | — | `NICE` |
| 없음 | 없음 | — | `UNKNOWN` |
| **REQUIRED 또는 없음** | **REQUIRED 또는 없음** | **있음** | **`AMBIGUOUS`** |

마지막 행이 `"Working knowledge of Finnish"`를 REVIEW로 보내는 규칙이다.
단, 어조가 NICE인데 hedge가 있으면 그냥 `NICE`다 (약한 신호끼리는 충돌이 아니다).

### 사전 (초기값 — 상수로 두고 나중에 확장 가능하게)

**섹션 헤더 — 필수:** requirements, must have, we expect, qualifications, you have,
essential, what we expect, what you bring, your profile, skills required

**섹션 헤더 — 우대:** nice to have, bonus, advantages, preferred, plus,
good to have, we appreciate, extra credit, would be a plus

**어조 — 필수:** must, required, is a requirement, essential, mandatory, expected,
you will need, fluent, native, proficiency in, minimum

**어조 — 우대:** is a plus, nice to have, beneficial, advantageous, preferred,
ideally, familiarity with, would be nice, appreciated, bonus

**완화(hedge):** working knowledge, basic, some knowledge, conversational,
understanding of, exposure to, willingness to learn, ability to learn

매칭은 **대소문자 무시, 단어 경계 존중**. `"plus"`가 `"surplus"`에 걸리면 안 된다.

### 절 분할

공고는 불릿 리스트가 많다. **줄 단위를 기본으로 하되**, 한 줄에 여러 문장이 있으면 문장으로 쪼갠다.

- 빈 줄, 불릿 기호(`-`, `*`, `•`, `·`, `–`, 숫자+`.`)는 제거하되 원문 텍스트는 보존
- 문장 분할 시 약어를 문장 끝으로 오인하지 마라: `e.g.`, `i.e.`, `etc.`, `vs.`, `Inc.`, `Ltd.`, `Sr.`, `Jr.`
- **원문 문자열과 줄 번호를 반드시 보존한다.** §6이 "원문 문장을 반드시 함께 출력한다"고 요구한다

### 섹션 헤더 판정

한 줄이 섹션 헤더인지 판단하는 기준 (하나라도 맞으면 헤더 후보):
- 줄이 사전의 헤더 표현으로 시작하고, 콜론으로 끝나거나 길이가 짧다 (예: 60자 이하)
- 헤더로 판정된 줄은 절 목록에 포함하지 않는다
- 헤더를 만나면 그 아래 절들의 섹션 상태가 바뀐다. 새 헤더를 만날 때까지 유지된다

## 만들 것

```
core/src/main/java/dev/juhyeonl/atscheck/core/
├── model/
│   ├── RequirementLevel.java   # REQUIRED | NICE | AMBIGUOUS | UNKNOWN
│   ├── SectionKind.java        # REQUIRED_SECTION | NICE_SECTION | NONE
│   ├── Signal.java             # 어떤 신호가 왜 걸렸는지 (사전 항목 + 종류)
│   └── Clause.java             # text, lineNumber, level, section, List<Signal>
└── section/
    ├── ClauseSplitter.java     # String -> List<원문 절 + 줄번호>
    ├── SectionScanner.java     # 줄이 섹션 헤더인가, 어떤 종류인가
    ├── ToneAnalyzer.java       # 절 -> 어조 신호 + hedge 신호
    └── SectionClassifier.java  # 위 셋을 조립. String -> List<Clause>
```

- **모두 불변(immutable) 객체로.** `record`를 적극 사용하라 (Java 21).
- `Clause`는 **왜 그렇게 판정됐는지 설명할 수 있어야 한다.** 걸린 신호 목록을 보존하라.
  나중에 CLI가 `"Fluent Finnish and English are required"` 같은 근거 문장을 출력한다.
- 공개 API는 `SectionClassifier.classify(String jobText) -> List<Clause>` 하나로 충분하다.

## 테스트 (JUnit 5 + AssertJ, `core/src/test`)

**결합 규칙 표의 10개 행을 전부 덮어라.** 이게 최소 요구사항이다. 추가로:

1. §6의 세 예시가 정확히 동작하는가
   - `"Fluent Finnish and English are required"` → `REQUIRED`
   - `"Finnish is a plus"` → `NICE`
   - `"Working knowledge of Finnish"` → `AMBIGUOUS`
2. 섹션 헤더 상태가 다음 헤더까지 유지되는가
3. 불릿 기호가 제거되고 원문과 줄 번호가 보존되는가
4. `"surplus"`가 `"plus"`로 오탐되지 않는가 (단어 경계)
5. 약어(`e.g.`)가 문장 분할을 깨뜨리지 않는가
6. 빈 입력·공백만 있는 입력에서 예외 없이 빈 목록을 반환하는가

**테스트 이름은 무엇을 검증하는지 문장으로 쓰라.** `test1()` 금지.

## 수정 금지

- `CLAUDE.md`, `docs/adr/`, `_briefs/` 기존 파일
- `cli/` 전체 — 이번 작업은 `core`만 건드린다
- `core`에 의존성 추가 금지
- `git push` 금지, 브랜치 생성 금지 (`main`에서 작업)
- 판정 규칙(언어/연차/학위/스킬), `AtsChecker` 본체, 골든 파일 — **다음 태스크 범위**

## 완료 조건

1. `./gradlew build` 성공
2. `./gradlew :core:test` 통과, 결합 규칙 10개 행이 모두 테스트로 덮임
3. §6의 세 예시가 사양대로 판정됨
4. `core`에 여전히 런타임 의존성이 0개

## 보고서

`_briefs/task-1-report.md`

```
[Codex 결과 보고서] Task 1

1. 수행한 작업 요약
2. 생성한 파일 목록
3. 공개 API 시그니처 (SectionClassifier)
4. 결합 규칙 표를 어떻게 구현했는가 + 각 행을 덮는 테스트 이름
5. 실행한 테스트와 실제 출력 (테스트 개수 포함)
6. 사양이 모호해서 스스로 결정한 것 + 그 근거   ← 반드시 채울 것
7. 실패했거나 확인 못 한 것
8. 리스크 (오탐/미탐이 예상되는 공고 패턴)
9. 다음 추천 작업
```

**6번을 성실히 채워라.** 사양의 빈틈은 내가 다음 태스크에서 메운다.
