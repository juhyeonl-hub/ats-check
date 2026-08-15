# [Codex 작업지시서] Task 3 — Stage 2·3 + AtsChecker 조립

## 목표

판정 알고리즘을 완성한다.

1. **Stage 2** — 직무명에서 시니어리티 추출 (`SeniorityLevelRule`)
2. **Stage 3** — 스킬 갭 분석 (`SkillRule`)
3. **조립** — `AtsChecker.check(...)`가 `APPLY` / `REVIEW` / `SKIP`을 반환

이 태스크가 끝나면 **`core`가 완성**된다. 이후는 CLI와 골든 파일이다.

## 배경 (확인된 사실 — 다시 조사하지 말 것)

- `main` 브랜치, 커밋 `c25e097`. 테스트 68개 통과 중.
- 이미 있는 것:
  - `SectionClassifier.classify(String) -> List<Clause>`
  - `LanguageRule.evaluate(List<Clause>, Profile) -> Finding`
  - `ExperienceYearsRule.evaluate(...)`, `DegreeRule.evaluate(...)`
  - `Profile`, `Finding`, `Status`, `RuleId`, `Seniority`, `Degree`
- `core` 런타임 의존성 **0개. 이번에도 0개를 유지한다.**
- `AtsChecker.echo(String)`는 Spike A 시절의 **스텁이다. 이번에 대체하라.**
  `AtsCheckerTest`의 기존 2개 테스트도 함께 갱신하라 (이건 허용된 수정이다).

## 만들 것

```
core/src/main/java/dev/juhyeonl/atscheck/core/
├── model/
│   ├── JobPosting.java     # record: title, body
│   ├── Verdict.java        # APPLY | REVIEW | SKIP
│   ├── SkillGap.java       # record: matched, missingRequired, missingNice
│   └── CheckResult.java    # record: verdict, findings, skillGap, stoppedAtHardFilter
├── rule/
│   ├── SeniorityLevelRule.java
│   └── SkillRule.java
└── AtsChecker.java         # 조립 (기존 스텁 대체)
```

---

## Stage 2 — SeniorityLevelRule

### JobPosting

```java
public record JobPosting(String title, String body) {
    public static JobPosting fromText(String text) { ... }
}
```

`fromText`: **첫 번째 비어 있지 않은 줄을 `title`로, 전체를 `body`로** 삼는다.
공고를 복사-붙여넣기하면 대개 첫 줄이 직무명이다.
`cli`가 프론트매터에서 `title`을 읽었다면 그걸 쓰고, 없으면 `fromText`를 쓴다.

### 추출 규칙

**직무명(title)에서만** 찾는다. 본문에서 찾지 마라 —
`"you will work with senior engineers"` 같은 문장이 오탐을 만든다.

| 마커 | 결과 |
|---|---|
| `junior`, `entry`, `entry-level`, `graduate`, `trainee`, `intern`, `apprentice` | `JUNIOR` |
| (마커 없음) | `MID` |
| `senior`, `sr.`, `sr ` | `SENIOR` |
| `lead`, `staff`, `principal`, `head of`, `director`, `chief`, `architect` | `LEAD` |

여러 마커가 있으면 **가장 높은 것**을 취한다 (`"Senior Lead Engineer"` → `LEAD`).

### 판정

```java
public static Finding evaluate(JobPosting posting, Profile profile)
```

- 추출된 시니어리티 > `profile.maxSeniority()` → **`WARN`**
- 그 외 → `PASS`

**절대 `FAIL`을 내지 마라.** §5가 명시한다: "`max_seniority`를 초과하면 `WARN` (SKIP 아님)".
직무명이 시니어여도 지원할 가치가 있을 수 있다.

summary 예시: `"Senior Backend Engineer (profile max: mid)"`, `"no seniority marker"`

---

## Stage 3 — SkillRule

### 기술 사전 (상수. 이 목록을 그대로 쓰라)

```
java, kotlin, scala, groovy, clojure,
spring, spring boot, hibernate, jpa, quarkus, micronaut, jakarta ee,
python, django, flask, fastapi, pandas, numpy,
javascript, typescript, node.js, deno, express, nest.js,
react, vue, angular, svelte, next.js, redux,
go, rust, c, c++, c#, .net, php, laravel, ruby, rails, elixir, swift, objective-c,
sql, postgresql, mysql, mariadb, oracle, sql server, sqlite,
mongodb, redis, cassandra, dynamodb, elasticsearch, neo4j,
kafka, rabbitmq, activemq, sqs, pulsar,
docker, kubernetes, helm, terraform, ansible, pulumi, vagrant,
aws, azure, gcp, google cloud, digitalocean, heroku,
jenkins, github actions, gitlab ci, circleci, teamcity, argocd,
git, svn, mercurial,
rest, graphql, grpc, soap, websocket, openapi,
microservices, event-driven, ddd, tdd, cqrs, serverless,
linux, unix, bash, shell, powershell, nginx, apache,
html, css, sass, less, tailwind, bootstrap,
junit, jest, pytest, mockito, testng, selenium, cypress, playwright, testcontainers,
maven, gradle, npm, yarn, pnpm, webpack, vite,
agile, scrum, kanban, jira, confluence,
prometheus, grafana, datadog, splunk, opentelemetry,
kibana, logstash, sentry
```

이 사전과 `profile.skills()`의 **합집합**을 탐색 대상으로 한다.
사용자가 사전에 없는 스킬을 프로필에 넣었다면 그것도 매칭되어야 한다.

### 매칭 규칙

- 대소문자 무시, **단어 경계 존중**
- **`c`, `c++`, `c#`, `.net`, `go`, `node.js` 처리에 주의하라.**
  - `c`가 `"can"`, `"clear"`에 걸리면 안 된다
  - `go`가 `"going"`, `"algorithm"`에 걸리면 안 된다
  - `c++`, `c#`, `.net`은 정규식 특수문자를 이스케이프해야 한다
  - **긴 항목을 먼저 매칭하라.** `"spring boot"`가 `"spring"`으로 잘리면 안 된다
- 절 하나에서 여러 스킬이 나올 수 있다

### 집계

```java
public record SkillGap(
    Set<String> matched,          // 공고에 있고 profile에도 있음
    Set<String> missingRequired,  // REQUIRED 절에 있는데 profile에 없음
    Set<String> missingNice       // NICE 절에 있는데 profile에 없음
) {}
```

- `REQUIRED` 절과 `AMBIGUOUS` 절의 스킬 → `missingRequired` 후보
- `NICE` 절의 스킬 → `missingNice` 후보
- `NEGATED`/`UNKNOWN` 절의 스킬은 **`matched` 판정에만** 쓰고 missing에는 넣지 않는다
- 같은 스킬이 required와 nice 양쪽에 나오면 **required 우선**
- 출력 순서가 안정적이도록 **`LinkedHashSet`** 을 쓰라 (테스트 재현성)

```java
public static SkillGap analyze(List<Clause> clauses, Profile profile)
public static Finding evaluate(List<Clause> clauses, Profile profile)
```

`evaluate`의 상태는 **항상 `PASS`**다. 스킬 부족은 SKIP 사유가 아니다 (§8 출력 예시에서
`MISSING (required)`가 있는데도 `VERDICT: APPLY`다).
summary 예시: `"missing 2 required, 1 nice"`, `"full match"`

---

## 조립 — AtsChecker

```java
public static CheckResult check(JobPosting posting, Profile profile)
public static CheckResult check(String jobText, Profile profile)  // fromText 편의 오버로드
```

### CheckResult

```java
public record CheckResult(
    Verdict verdict,
    List<Finding> findings,       // 실행된 규칙의 Finding 전부, 실행 순서대로
    SkillGap skillGap,            // 하드 필터에서 멈추면 null
    boolean stoppedAtHardFilter
) {}
```

### 실행 순서와 판정

```
1. SectionClassifier.classify(posting.body())
2. Stage 1: LanguageRule, ExperienceYearsRule, DegreeRule  (항상 셋 다 실행)
   → 하나라도 FAIL이면:
        verdict = SKIP
        stoppedAtHardFilter = true
        skillGap = null
        Stage 2·3을 실행하지 않고 즉시 반환
3. Stage 2: SeniorityLevelRule
4. Stage 3: SkillRule
5. 최종 판정:
        Stage 1에 REVIEW가 있으면  → REVIEW
        그 외                      → APPLY
        (WARN은 verdict를 바꾸지 않는다. 표시만 한다)
```

**Stage 1의 세 규칙은 FAIL이 나와도 셋 다 실행한다.** 첫 FAIL에서 멈추지 마라.
§8 출력 예시가 SKIP일 때도 나머지 검사 결과를 보여준다:

```
VERDICT: SKIP
  ✗ Language    Finnish required
  ✓ Seniority   3+ years (profile: 2, tolerance: 1)
  ✓ Degree      not required
  Analysis stopped at hard filter.
```

"stopped at hard filter"가 뜻하는 것은 **Stage 2·3을 건너뛰었다**는 것이지
Stage 1을 중간에 멈췄다는 것이 아니다.

---

## 테스트

기존 68개를 유지하면서 추가하라. `AtsCheckerTest`의 스텁 테스트 2개는 갱신한다.

### SeniorityLevelRule
1. `"Senior Backend Engineer"` + profile `MID` → `WARN`
2. `"Backend Engineer"` → `PASS`, `MID`로 인식
3. `"Junior Developer"` + profile `MID` → `PASS`
4. `"Senior Lead Engineer"` → `LEAD`로 인식 (가장 높은 것)
5. **본문의 `"you will work with senior engineers"`는 무시된다** (title만 본다)
6. `"Head of Engineering"` → `LEAD`

### SkillRule
7. `Requirements:` 아래 `"Java and Kotlin"` + profile에 java만 → `missingRequired`에 kotlin
8. `Nice to have:` 아래 `"Terraform"` → `missingNice`에 terraform
9. profile에 있는 스킬은 `matched`
10. **`"spring boot"`가 `"spring"`으로 잘리지 않는다**
11. **`"go"`가 `"going"`에, `"c"`가 `"can"`에 매칭되지 않는다**
12. `c++`, `c#`, `.net`이 정확히 매칭된다
13. 같은 스킬이 required·nice 양쪽에 있으면 required 우선
14. 상태는 항상 `PASS`

### AtsChecker
15. 핀란드어 필수 공고 → `SKIP`, `stoppedAtHardFilter=true`, `skillGap=null`
16. **SKIP이어도 Stage 1의 Finding 3개가 모두 들어 있다**
17. 애매한 핀란드어 공고 → `REVIEW`
18. 깨끗한 공고 → `APPLY`, `skillGap != null`
19. `WARN`만 있는 공고 → `APPLY` (WARN은 verdict를 바꾸지 않는다)
20. **통합**: §8의 SKIP 예시와 APPLY 예시에 해당하는 공고 텍스트를 각각 넣어
    verdict와 Finding 목록이 기대대로 나오는지 확인

## 수정 금지

- `CLAUDE.md`, `docs/adr/`, `_reviews/`, `_briefs/` 기존 파일
- `cli/` 전체
- **`core/section/` 전체** — 분류기는 검증이 끝났다. 문제를 발견하면 고치지 말고 보고하라
- **`core/rule/` 의 기존 3개 규칙** — 검증이 끝났다. 동일하게 보고만 하라
- `core`에 의존성 추가 금지
- `git commit` / `git push` 금지

**예외:** `AtsChecker.java`와 `AtsCheckerTest.java`는 스텁이므로 대체해도 된다.

## 완료 조건

1. `./gradlew build` 성공
2. `./gradlew :core:test` 통과. **기존 66개(= 68 - 스텁 2개)가 그대로 통과**
3. 위 20개 테스트 요구가 모두 구현됨
4. `./gradlew :core:dependencies --configuration runtimeClasspath` → `No dependencies`

## 보고서

`_briefs/task-3-report.md`

```
[Codex 결과 보고서] Task 3

1. 수행한 작업 요약
2. 생성/변경한 파일 목록
3. AtsChecker의 공개 API와 판정 흐름
4. 스킬 매칭에서 c/go/c++/c#/.net/spring boot를 어떻게 처리했는가   ← 구체적으로
5. 테스트 결과 (총 개수, 기존 테스트 유지 확인)
6. 스스로 결정한 것 + 근거
7. 남은 오탐/미탐 리스크
8. 기존 분류기·규칙에서 고쳐야 한다고 생각하는 것 (보고만)
9. 다음 추천 작업
```
