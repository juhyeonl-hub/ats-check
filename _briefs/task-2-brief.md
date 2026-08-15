# [Codex 작업지시서] Task 2 — Stage 1 하드 필터 (언어 / 연차 / 학위)

## 목표

`CLAUDE.md` §5의 **Stage 1 하드 필터** 3개 규칙을 구현한다.
하나라도 걸리면 즉시 `SKIP`이 되는, 이 도구 가치의 핵심이다.

Stage 2(시니어리티 레벨)와 Stage 3(스킬 갭), `AtsChecker` 조립은 **다음 태스크**다.

## 배경 (확인된 사실 — 다시 조사하지 말 것)

- `main` 브랜치, 커밋 `5330f59`. 섹션 분류기가 완성되어 있다.
- **`SectionClassifier.classify(String) -> List<Clause>`가 이미 동작한다.** 이걸 입력으로 쓴다.
- `Clause.level()`은 `REQUIRED | NICE | NEGATED | AMBIGUOUS | UNKNOWN` 중 하나다.
- `Clause`는 원문 텍스트와 줄 번호, 근거 신호 목록을 보존한다.
- 툴체인: `source "$HOME/.sdkman/bin/sdkman-init.sh"` 후 `./gradlew`
- `core` 런타임 의존성은 **0개다. 이번에도 0개를 유지한다.**

### core 의존성 0을 유지하는 이유 (중요)

`CLAUDE.md` §3은 "core는 이상적으로 SnakeYAML만"이라고 하지만, **0개가 더 낫다.**
`profile.yml`을 읽는 것은 파일 I/O이므로 `cli`의 책임이다.
`core`는 이미 만들어진 `Profile` 객체를 받기만 한다.

**따라서 이번 태스크에서 YAML을 파싱하지 마라.** `Profile`은 순수 record다.

## 만들 것

```
core/src/main/java/dev/juhyeonl/atscheck/core/
├── model/
│   ├── Seniority.java     # JUNIOR | MID | SENIOR | LEAD  (순서 있는 enum)
│   ├── Degree.java        # NONE | BACHELOR | MASTER | PHD (순서 있는 enum)
│   ├── Profile.java       # record
│   ├── RuleId.java        # LANGUAGE | EXPERIENCE_YEARS | DEGREE | SENIORITY_LEVEL | SKILLS
│   ├── Status.java        # PASS | WARN | REVIEW | FAIL
│   └── Finding.java       # record
└── rule/
    ├── LanguageRule.java
    ├── ExperienceYearsRule.java
    └── DegreeRule.java
```

### Profile

```java
public record Profile(
    int yearsExperience,
    int yearsTolerance,      // 기본 1
    Seniority maxSeniority,
    Set<String> languages,   // 소문자 정규화. 예: {"english", "korean"}
    Degree degree,
    Set<String> skills       // 소문자 정규화
) {
    public static Profile defaults() { ... }   // profile.yml이 없을 때
}
```

- 컴팩트 생성자에서 `null`을 거부하고, 컬렉션은 **불변 복사본**으로 만들어라
- `defaults()`: `yearsExperience=0, yearsTolerance=1, maxSeniority=LEAD,
  languages={"english"}, degree=NONE, skills={}`
  — 기본값은 **아무것도 걸러내지 않는 쪽**이어야 한다. 설정 없이 쓰는 사용자에게
  근거 없는 SKIP을 주면 안 된다

### Finding

```java
public record Finding(
    RuleId rule,
    Status status,
    String summary,          // 한 줄. 예: "Finnish required"
    List<Clause> evidence    // 근거 절. 비어 있을 수 있다
) {}
```

`evidence`가 **§6의 "원문 문장을 반드시 함께 출력한다"를 지탱한다.**
FAIL/REVIEW/WARN을 낼 때는 근거 절을 반드시 채워라. PASS는 비어도 된다.

### 규칙 공통 계약

각 규칙은 아래 형태의 정적 메서드 하나를 공개한다:

```java
public static Finding evaluate(List<Clause> clauses, Profile profile)
```

여러 절이 걸리면 **가장 강한 상태를 채택**한다: `FAIL > REVIEW > WARN > PASS`.
채택된 상태에 해당하는 절을 전부 `evidence`에 담아라.

---

## 규칙 1 — LanguageRule (하드 필터)

**검사 대상 언어와 표기 변형:**

| 언어 | 표기 |
|---|---|
| 핀란드어 | `finnish`, `finish`(오타), `suomi`, `suomen kieli`, `finska` |
| 스웨덴어 | `swedish`, `ruotsi`, `svenska` |

**영어는 검사하지 않는다.** 이 도구의 사용자는 영어로 구직 중이다.

**판정:**

1. 해당 언어가 `profile.languages()`에 있으면 → **`PASS`**
   (사용자가 구사하므로 장애물이 아니다). summary: `"Finnish required (you have it)"`
2. 그렇지 않으면 언어 키워드를 포함한 절의 `level()`에 따라:

| 절의 level | 상태 | summary 예시 |
|---|---|---|
| `REQUIRED` | **`FAIL`** | `"Finnish required"` |
| `AMBIGUOUS` | **`REVIEW`** | `"Finnish — ambiguous requirement"` |
| `NEGATED` | `PASS` | `"Finnish explicitly not required"` |
| `NICE` | `PASS` | `"Finnish is a plus"` |
| `UNKNOWN` | `PASS` | `"Finnish mentioned, no requirement signal"` |
| (언급 없음) | `PASS` | `"English only"` |

**`NEGATED`를 PASS로 처리하는 것이 이 규칙의 핵심이다.**
Task 1.5에서 고친 결함이 바로 이것이었다 — 핀란드어가 *불필요*하다고 명시한 공고를
버리면 도구의 목적과 정반대가 된다.

---

## 규칙 2 — ExperienceYearsRule (하드 필터)

**추출 패턴** (모두 대소문자 무시):

```
3+ years                     → 3
3 years                      → 3
at least 3 years             → 3
minimum of 3 years           → 3
minimum 3 years              → 3
over 5 years                 → 5
more than 5 years            → 5
5 or more years              → 5
3-5 years                    → 3   (범위는 하한)
3 to 5 years                 → 3
three years                  → 3   (one~ten 영어 숫자)
```

- `year` 단수형도 인식한다
- `of experience`가 뒤따르지 않아도 인식한다 (`"3+ years with Java"`)
- **`REQUIRED`와 `AMBIGUOUS` 절만 검사한다.** `NICE`/`NEGATED` 절의 연차는 무시한다

**판정:**

- 추출된 값 중 **최댓값**을 요구 연차로 본다
- 요구 연차 > `profile.yearsExperience() + profile.yearsTolerance()` → **`FAIL`**
- 위 조건에 걸리지만 근거 절이 `AMBIGUOUS`면 → **`REVIEW`**
- 요구 연차가 `profile.yearsExperience()`보다 크지만 tolerance 안이면 → **`WARN`**
  (§8 출력 예시의 `⚠ Seniority "3+ years" — borderline`이 이 경우다)
- 그 외 → `PASS`

summary 예시: `"3+ years (profile: 2, tolerance: 1)"`

---

## 규칙 3 — DegreeRule (하드 필터)

**감지 표현:**

| 학위 | 표기 |
|---|---|
| 학사 | `bachelor`, `bachelor's`, `bsc`, `b.sc`, `undergraduate degree` |
| 석사 | `master`, `master's`, `msc`, `m.sc`, `graduate degree` |
| 박사 | `phd`, `ph.d`, `doctorate`, `doctoral` |

**주의:** `master`는 `"master of your craft"`, `"mastery"`, `"master branch"` 같은
비학위 용법이 있다. **학위 문맥 단어**(`degree`, `education`, `studies`, `university`,
`msc`, `m.sc`)가 같은 절에 있거나 `master's` 소유격 형태일 때만 학위로 인정하라.

**완화 표현:** `or equivalent`, `or equivalent experience`, `or comparable experience`가
같은 절에 있으면 상태를 한 단계 낮춘다 (`FAIL` → `WARN`).
학위가 없어도 경력으로 대체 가능하다는 뜻이기 때문이다.

**판정:**

- `REQUIRED` 절에서 감지된 최고 학위 > `profile.degree()` → **`FAIL`**
- `AMBIGUOUS` 절이면 → **`REVIEW`**
- `NICE`/`NEGATED` 절이면 → `PASS`
- 감지 없음 → `PASS`, summary: `"not required"`

`Degree`와 `Seniority` enum은 **선언 순서로 비교**할 수 있게 만들어라
(`compareTo` 또는 명시적 `int rank()`).

---

## 테스트 (JUnit 5 + AssertJ)

각 규칙마다 별도 테스트 클래스. **아래는 최소 요구이며, 표의 모든 행을 덮어라.**

### LanguageRule
1. `Requirements:` 아래 `"Fluent Finnish is required"` → `FAIL`
2. 같은 입력 + `profile.languages`에 `finnish` 포함 → `PASS`
3. `"Finnish is a plus"` → `PASS`
4. **`"Finnish is not required"` → `PASS`** (회귀 방지 — Task 1.5의 핵심 수정)
5. `"Working knowledge of Finnish"` → `REVIEW`
6. 스웨덴어(`svenska`)도 동일하게 동작
7. 언어 언급이 전혀 없는 공고 → `PASS`
8. `FAIL`일 때 `evidence`에 원문 절이 들어 있다

### ExperienceYearsRule
9. 위 추출 패턴 표의 **11개 형태를 전부** 파싱한다 (파라미터화 테스트 권장)
10. `3-5 years`에서 하한 3을 취한다
11. profile 2년 + tolerance 1 → `"5+ years"`는 `FAIL`, `"3+ years"`는 `WARN`, `"2 years"`는 `PASS`
12. `NICE` 절의 `"5+ years"`는 무시된다
13. 여러 연차가 나오면 최댓값을 쓴다
14. 연차 언급이 없으면 `PASS`

### DegreeRule
15. `REQUIRED` 절의 `"MSc in Computer Science"` + profile `bachelor` → `FAIL`
16. 같은 입력 + profile `master` → `PASS`
17. `"Bachelor's degree"` + profile `bachelor` → `PASS`
18. **`"master of your craft"` → 학위로 인식하지 않는다** (오탐 방지)
19. `"MSc or equivalent experience"` → `WARN` (`FAIL` 아님)
20. `"PhD is a plus"` → `PASS`

### 통합
21. 실제 공고 형태의 텍스트 하나로 세 규칙을 모두 돌려 기대 상태가 나오는지 확인

**테스트 이름은 무엇을 검증하는지 문장으로 쓰라.**

## 수정 금지

- `CLAUDE.md`, `docs/adr/`, `_reviews/`, `_briefs/` 기존 파일
- `cli/` 전체
- **`core/section/` 전체** — 섹션 분류기는 검증이 끝났다. 건드리지 마라.
  분류기 동작이 바뀌어야 한다고 판단되면 **고치지 말고 보고서에 적어라**
- `core`에 의존성 추가 금지
- `git commit` / `git push` 금지
- Stage 2(시니어리티 레벨), Stage 3(스킬), `AtsChecker` 조립 — **다음 태스크**

## 완료 조건

1. `./gradlew build` 성공
2. `./gradlew :core:test` 통과. **기존 32개 테스트가 그대로 통과**해야 한다
3. 위 21개 테스트 요구가 모두 구현됨
4. `./gradlew :core:dependencies --configuration runtimeClasspath` → `No dependencies`

## 보고서

`_briefs/task-2-report.md`

```
[Codex 결과 보고서] Task 2

1. 수행한 작업 요약
2. 생성한 파일 목록
3. 각 규칙의 공개 API와 판정 로직 요약
4. 연차 추출 정규식/파싱 전략 (어떤 패턴을 어떻게 잡았는가)
5. `master` 오탐 방지를 어떻게 구현했는가
6. 테스트 결과 (총 개수, 기존 32개 유지 확인)
7. 사양이 모호해서 스스로 결정한 것 + 근거
8. 남은 오탐/미탐 리스크
9. 섹션 분류기에서 고쳐야 한다고 생각하는 것 (고치지는 말고 보고만)
10. 다음 추천 작업
```
